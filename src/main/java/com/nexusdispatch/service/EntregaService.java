package com.nexusdispatch.service;

import com.nexusdispatch.dao.*;
import com.nexusdispatch.database.ConexaoBD;
import com.nexusdispatch.enums.*;
import com.nexusdispatch.model.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.util.*;

/**
 * Ciclo JDBC de entrega. Lote da mesma compra/cliente, coleta em ID crescente,
 * entrega ao cliente depois das coletas. IDs no modo de demonstracao nao sao login.
 */
public final class EntregaService {
    /** Conexao nova e dedicada por operacao. */
    @FunctionalInterface
    public interface FabricaConexao { Connection abrir() throws SQLException; }
    /** Classificacao explicita: categoria textual do produto nao comprova conservacao. */
    public record ProdutoLogistico(boolean alimento, boolean comidaPronta, BigDecimal prazoMinutos) {
        public ProdutoLogistico {
            if (comidaPronta && !alimento) throw new IllegalArgumentException("Comida pronta e alimento.");
            if ((alimento && prazoMinutos == null) || (prazoMinutos != null && prazoMinutos.signum() <= 0))
                throw new IllegalArgumentException("Prazo invalido/ausente.");
        }
    }
    /**
     * Fonte interna confiavel dos dados ausentes do SQL. Implementacao local,
     * rapida e estavel durante a operacao; nunca dados de elegibilidade do cliente.
     * capacidadeKg e carga util verificada; para van, nao usar o PBT.
     */
    public interface DadosLogisticos {
        MalhaEntrega malha();
        String posicaoEntregador(long id);
        String pontoLoja(long idLojista);
        String pontoCliente(long idCliente);
        ProdutoLogistico produto(long idProduto);
        BigDecimal capacidadeKg(EntregadorRegistro entregador);
        BigDecimal minutosColeta(long idLojista);
    }
    /** Limites configuraveis; distancia e tempo adicionais sao ambos obrigatorios. */
    public record Limites(BigDecimal aproximacaoKm, BigDecimal comidaKm, BigDecimal comidaMin,
            BigDecimal outrosKm, BigDecimal outrosMin) {
        public Limites {
            for (BigDecimal n : new BigDecimal[]{aproximacaoKm, comidaKm, comidaMin, outrosKm, outrosMin})
                if (n == null || n.signum() < 0) throw new IllegalArgumentException("Limite invalido.");
        }
    }
    // Resumo da comanda: peso acumulado, presenca de alimentos/comida pronta e
    // o menor prazo informado. null no prazo indica ausencia de limite cadastrado.
    private record Carga(BigDecimal kg, boolean alimento, boolean comida, BigDecimal prazo) { }
    private final FabricaConexao fabrica;
    private final DadosLogisticos dados;
    private final BigDecimal percentualPlataforma;
    private final Map<TipoVeiculo, Limites> limites;

    /** Ciclo persistido disponivel; criar/aceitar exige cadastro logistico configurado. */
    public EntregaService() { this(ConexaoBD::conectar); }
    /** Injecao JDBC para testes; nao inventa malha ou capacidade. */
    public EntregaService(FabricaConexao fabrica) { this(fabrica, null, new BigDecimal("10"), limitesIniciais()); }
    /** Parametros de teste podem ser substituidos sem alterar regras no fonte. */
    public EntregaService(FabricaConexao fabrica, DadosLogisticos dados, BigDecimal percentual,
            Map<TipoVeiculo, Limites> limites) {
        this.fabrica = Objects.requireNonNull(fabrica);
        this.dados = dados;
        CompraService.calcularPercentual(BigDecimal.ZERO, percentual);
        percentualPlataforma = percentual;
        this.limites = Map.copyOf(limites);
        if (!this.limites.keySet().containsAll(EnumSet.allOf(TipoVeiculo.class)))
            throw new IllegalArgumentException("Configure todos os veiculos.");
    }
    /** Parametros aprovados; nao gera distancias ficticias para a malha. */
    public static Map<TipoVeiculo, Limites> limitesIniciais() {
        return Map.of(TipoVeiculo.BICICLETA, limite("3.5", "1", "5", "3", "15"),
                TipoVeiculo.MOTO, limite("7", "2", "7", "6", "20"),
                TipoVeiculo.CARRO, limite("7", "0", "0", "8", "25"),
                TipoVeiculo.VAN, limite("15", "0", "0", "12", "30"));
    }
    /** Constroi limites decimais sem aproximacao binaria. */
    // Ordem dos argumentos: aproximacao km, desvio de comida km/minutos,
    // desvio dos demais produtos km/minutos. Strings evitam arredondamento de double.
    private static Limites limite(String a, String b, String c, String d, String e) {
        return new Limites(new BigDecimal(a), new BigDecimal(b), new BigDecimal(c), new BigDecimal(d), new BigDecimal(e));
    }
    /** Consulta snapshot sem reservar. */
    public Optional<OrdemEntregaRegistro> buscar(long id) throws SQLException {
        TransacaoJdbc.validarId(id);
        return executar(c -> new OrdemEntregaDao(c).buscarPorId(id));
    }
    /** Aceite unitario usa as mesmas verificacoes do lote. */
    public boolean aceitarEntrega(long entregador, long entrega) throws SQLException {
        return aceitarRota(entregador, List.of(entrega));
    }
    /**
     * Todas as ordens ou nenhuma. Lote escolhido antes da coleta, sem acrescentar
     * ordens a uma rota ativa. Bloqueios: entregador, usuario, pedido, ordens por ID.
     */
    public boolean aceitarRota(long entregador, List<Long> entregas) throws SQLException {
        TransacaoJdbc.validarId(entregador);
        List<Long> ids = validarLote(entregas);
        exigirDados();
        return executar(c -> {
            var ed = new EntregadorDao(c);
            var e = ed.buscarPorIdParaAtualizacao(entregador);
            if (e.isEmpty() || e.orElseThrow().statusOperacao() != StatusOperacaoEntregador.DISPONIVEL) return false;
            var u = new UsuarioDao(c).buscarPorIdParaAtualizacao(entregador);
            if (u.isEmpty() || u.orElseThrow().perfil() != PerfilUsuario.ENTREGADOR
                    || !Boolean.TRUE.equals(u.orElseThrow().ativo())) return false;
            var od = new OrdemEntregaDao(c);
            if (od.possuiOrdemAtiva(entregador)) return false;
            // A primeira ordem identifica a compra do lote. Depois de bloquear o pedido,
            // cada ordem e relida sob bloqueio e precisa pertencer a essa mesma compra.
            var primeira = od.buscarPorId(ids.getFirst());
            if (primeira.isEmpty()) return false;
            var cd = new ComandaLojistaDao(c);
            var comanda = cd.buscarPorId(primeira.orElseThrow().idComanda()).orElseThrow();
            bloquearPedido(c, comanda.idPedido());
            var pedido = new PedidoMestreDao(c).buscarPorId(comanda.idPedido()).orElseThrow();
            if (pedido.statusPedido() != StatusPedidoMestre.PAGO) return false;
            List<ComandaLojistaRegistro> partes = new ArrayList<>();
            for (long id : ids) {
                var ordem = od.buscarPorIdParaAtualizacao(id);
                if (ordem.isEmpty()) return false;
                var o = ordem.orElseThrow();
                var parte = cd.buscarPorId(o.idComanda()).orElseThrow();
                if (o.idEntregador() != null || o.statusEntrega() != StatusOrdemEntrega.AGUARDANDO_COLETA
                        || o.dataHoraColeta() != null || o.dataHoraEntrega() != null
                        || parte.idPedido() != pedido.id() || parte.statusComanda() != StatusComanda.AGUARDANDO_COLETA) return false;
                partes.add(parte);
            }
            // Verifica todas as restricoes antes de reservar. Se qualquer UPDATE posterior
            // falhar, exigir lanca excecao e a transacao desfaz o lote inteiro.
            avaliarRota(c, e.orElseThrow(), pedido, partes);
            exigir(ed.ocuparDisponivel(entregador));
            for (long id : ids) exigir(od.vincularDisponivel(id, entregador));
            return true;
        });
    }
    /** Retirada apenas pelo vinculado, seguindo a sequencia avaliada no aceite. */
    public boolean registrarColeta(long entregador, long entrega) throws SQLException {
        return operar(entregador, entrega, (c, o, ativas) -> {
            if (o.statusEntrega() != StatusOrdemEntrega.AGUARDANDO_COLETA) return false;
            if (ativas.stream().anyMatch(a -> a.id() < o.id() && a.statusEntrega() == StatusOrdemEntrega.AGUARDANDO_COLETA))
                throw new IllegalStateException("Colete primeiro a ordem anterior da rota.");
            if (!new OrdemEntregaDao(c).registrarColeta(entrega, entregador)) return false;
            exigir(new ComandaLojistaDao(c).atualizarStatus(o.idComanda(), StatusComanda.AGUARDANDO_COLETA, StatusComanda.EM_TRANSITO));
            return true;
        });
    }
    /** Conclui comanda; ultima parte conclui compra; ultima ordem ativa libera veiculo. */
    public boolean concluirEntrega(long entregador, long entrega) throws SQLException {
        return operar(entregador, entrega, (c, o, ativas) -> {
            if (o.statusEntrega() != StatusOrdemEntrega.EM_TRANSITO) return false;
            if (ativas.stream().anyMatch(a -> a.statusEntrega() == StatusOrdemEntrega.AGUARDANDO_COLETA))
                throw new IllegalStateException("Conclua as coletas da rota antes de entregar.");
            if (!new OrdemEntregaDao(c).concluirEntrega(entrega, entregador)) return false;
            var cd = new ComandaLojistaDao(c);
            exigir(cd.atualizarStatus(o.idComanda(), StatusComanda.EM_TRANSITO, StatusComanda.CONCLUIDO));
            long pedido = cd.buscarPorId(o.idComanda()).orElseThrow().idPedido();
            var partes = cd.listarPorPedido(pedido);
            if (!partes.isEmpty() && partes.stream().allMatch(p -> p.statusComanda() == StatusComanda.CONCLUIDO))
                exigir(new PedidoMestreDao(c).atualizarStatus(pedido, StatusPedidoMestre.PAGO, StatusPedidoMestre.CONCLUIDO));
            liberarSeLivre(c, entregador);
            return true;
        });
    }
    /** Desistir devolve a atribuicao anterior a coleta, preservando compra e frete. */
    public boolean desistirAntesDaColeta(long entregador, long entrega) throws SQLException {
        return operar(entregador, entrega, (c, o, ativas) -> {
            if (!new OrdemEntregaDao(c).desvincularAntesDaColeta(entrega, entregador)) return false;
            liberarSeLivre(c, entregador);
            return true;
        });
    }
    /** Comando do lojista para iniciar separacao. */
    public boolean iniciarPreparo(long lojista, long comanda) throws SQLException {
        return preparar(lojista, comanda, StatusComanda.PAGO, StatusComanda.PREPARANDO);
    }
    /** Comando do lojista para informar produtos prontos. */
    public boolean disponibilizarColeta(long lojista, long comanda) throws SQLException {
        return preparar(lojista, comanda, StatusComanda.PREPARANDO, StatusComanda.AGUARDANDO_COLETA);
    }
    /** Serializa preparo com operacoes de entrega da compra. */
    private boolean preparar(long lojista, long comanda, StatusComanda de, StatusComanda para) throws SQLException {
        TransacaoJdbc.validarId(lojista); TransacaoJdbc.validarId(comanda);
        return executar(c -> {
            var cd = new ComandaLojistaDao(c);
            var parte = cd.buscarPorId(comanda);
            if (parte.isEmpty() || parte.orElseThrow().idLojista() != lojista) return false;
            bloquearPedido(c, parte.orElseThrow().idPedido());
            var u = new UsuarioDao(c).buscarPorId(lojista).orElseThrow();
            if (u.perfil() != PerfilUsuario.LOJISTA || !Boolean.TRUE.equals(u.ativo()))
                throw new IllegalStateException("Lojista deve estar ativo.");
            if (new PedidoMestreDao(c).buscarPorId(parte.orElseThrow().idPedido()).orElseThrow().statusPedido() != StatusPedidoMestre.PAGO) return false;
            return cd.atualizarStatus(comanda, de, para);
        });
    }
    /**
     * Hook do checkout na mesma conexao, sem commit proprio. Rateio por maiores
     * restos, desempate por ID, conserva centavos da taxa cobrada.
     */
    void criarOrdens(Connection c, PedidoMestreRegistro pedido, List<ComandaLojistaRegistro> partes) throws SQLException {
        exigirDados();
        if (c.getAutoCommit() || pedido.statusPedido() != StatusPedidoMestre.PAGO || partes.isEmpty())
            throw new IllegalStateException("Criacao exige checkout pago em transacao.");
        var ordenadas = partes.stream().sorted(Comparator.comparing(ComandaLojistaRegistro::id)).toList();
        BigDecimal soma = ordenadas.stream().map(ComandaLojistaRegistro::valorProdutos).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (soma.signum() <= 0 || soma.compareTo(pedido.valorProdutos()) != 0) throw new IllegalStateException("Subtotais inconsistentes.");
        // Trabalhamos em centavos inteiros: uma taxa de 0.21 vira 21 unidades.
        // Cada loja recebe a proporcao do seu valor de produtos, nunca uma divisao
        // independente arredondada que pudesse criar ou perder centavos no total.
        BigDecimal centavos = pedido.valorFretes().movePointRight(2), usados = BigDecimal.ZERO;
        Map<Long, BigDecimal> cotas = new HashMap<>(), restos = new HashMap<>();
        for (var p : ordenadas) {
            if (p.idPedido() != pedido.id() || p.statusComanda() != StatusComanda.PAGO) throw new IllegalStateException("Comanda fora do checkout.");
            BigDecimal numerador = centavos.multiply(p.valorProdutos());
            // A parte inteira fica com a loja; o resto mede a fracao ainda nao distribuida.
            // Como o denominador soma e igual para todas, comparamos os restos sem dividir.
            BigDecimal inteiro = numerador.divide(soma, 0, RoundingMode.DOWN);
            cotas.put(p.id(), inteiro); usados = usados.add(inteiro);
            restos.put(p.id(), numerador.subtract(inteiro.multiply(soma)));
        }
        // Maiores restos recebem os centavos que sobraram. ID desempata de forma
        // deterministica. Exemplo: cotas 0.33 e 0.67 de um centavo destinam-no a segunda loja.
        var prioridade = ordenadas.stream().sorted(Comparator.<ComandaLojistaRegistro, BigDecimal>comparing(p -> restos.get(p.id())).reversed()
                .thenComparing(ComandaLojistaRegistro::id)).toList();
        int sobra = centavos.subtract(usados).intValueExact();
        for (int i = 0; i < sobra; i++) cotas.merge(prioridade.get(i).id(), BigDecimal.ONE, BigDecimal::add);
        for (var p : ordenadas) {
            Carga carga = carregarCarga(c, p.id());
            // Referencia historica de distancia; aceite calcula novamente com o veiculo escolhido.
            TipoVeiculo referencia = carga.alimento() ? TipoVeiculo.MOTO : TipoVeiculo.CARRO;
            var caminho = dados.malha().calcular(dados.pontoLoja(p.idLojista()), dados.pontoCliente(pedido.idCliente()), referencia);
            BigDecimal distancia = caminho.km().setScale(2, RoundingMode.HALF_UP);
            if (distancia.signum() <= 0) throw new IllegalStateException("Schema exige distancia historica positiva.");
            BigDecimal taxa = cotas.get(p.id()).movePointLeft(2).setScale(2);
            // A retencao e parte da cota, nao uma cobranca adicional. Subtrair o valor
            // da plataforma garante repasse + retencao igual ao frete desta comanda.
            BigDecimal plataforma = CompraService.calcularPercentual(taxa, percentualPlataforma);
            new OrdemEntregaDao(c).inserir(OrdemEntregaRegistro.novaOrdem(p.id(), distancia, taxa.subtract(plataforma), plataforma));
        }
    }
    /** Avalia ordem fixa por ID e destino unico; nao promete otimo global. */
    private void avaliarRota(Connection c, EntregadorRegistro e, PedidoMestreRegistro pedido, List<ComandaLojistaRegistro> partes) throws SQLException {
        BigDecimal capacidade = dados.capacidadeKg(e);
        if (capacidade == null || capacidade.signum() <= 0) throw new IllegalStateException("Capacidade util ausente.");
        BigDecimal teto = switch (e.tipoVeiculo()) {
            case BICICLETA, MOTO -> new BigDecimal("20");
            case CARRO -> new BigDecimal("450");
            case VAN -> capacidade;
        };
        // Usa o menor entre capacidade util cadastrada e teto da modalidade.
        // Para VAN, o teto e a propria capacidade informada, sem confundir carga com PBT.
        capacidade = capacidade.min(teto);
        List<Carga> cargas = new ArrayList<>();
        BigDecimal peso = BigDecimal.ZERO;
        boolean comida = false, alimento = false;
        for (var p : partes) {
            Carga carga = carregarCarga(c, p.id()); cargas.add(carga);
            peso = peso.add(carga.kg()); comida |= carga.comida(); alimento |= carga.alimento();
        }
        if (peso.compareTo(capacidade) > 0) throw new IllegalStateException("Carga excede capacidade.");
        if (alimento && (e.tipoVeiculo() == TipoVeiculo.CARRO || e.tipoVeiculo() == TipoVeiculo.VAN))
            throw new IllegalStateException("Alimentos exigem bicicleta ou moto.");
        if (comida && partes.size() > 2) throw new IllegalStateException("Comida pronta permite ate duas lojas.");
        Limites l = limites.get(e.tipoVeiculo());
        var malha = dados.malha();
        String destino = dados.pontoCliente(pedido.idCliente()), posicao = dados.posicaoEntregador(e.id());
        BigDecimal tempo = BigDecimal.ZERO, distancia = BigDecimal.ZERO;
        // Guarda os acumulados ao terminar cada coleta. Depois, subtrair esse marco
        // do total revela quanto tempo/distancia aquela carga permanece no trajeto.
        // A ordem e fixa por ID; nao se tenta permutar lojas para otimizar a rota.
        List<BigDecimal> tempos = new ArrayList<>(), distancias = new ArrayList<>();
        for (int i = 0; i < partes.size(); i++) {
            var p = partes.get(i);
            String loja = dados.pontoLoja(p.idLojista());
            var trecho = malha.calcular(posicao, loja, e.tipoVeiculo());
            if (i == 0 && trecho.km().compareTo(l.aproximacaoKm()) > 0) throw new IllegalStateException("Primeira loja fora do alcance.");
            BigDecimal espera = dados.minutosColeta(p.idLojista());
            if (espera == null || espera.signum() < 0) throw new IllegalStateException("Tempo de coleta ausente/invalido.");
            // Inclui deslocamento e espera desta loja antes de marcar a coleta concluida.
            // Para uma carga ja coletada, a espera nas lojas seguintes entra em sua duracao.
            tempo = tempo.add(trecho.minutos()).add(espera); distancia = distancia.add(trecho.km());
            tempos.add(tempo); distancias.add(distancia); posicao = loja;
        }
        var fim = malha.calcular(posicao, destino, e.tipoVeiculo());
        tempo = tempo.add(fim.minutos()); distancia = distancia.add(fim.km());
        for (int i = 0; i < partes.size(); i++) {
            var direto = malha.calcular(dados.pontoLoja(partes.get(i).idLojista()), destino, e.tipoVeiculo());
            BigDecimal duracao = tempo.subtract(tempos.get(i));
            // Compara o percurso restante do lote com a entrega direta desta loja ao cliente.
            // max(ZERO) evita tratar uma diferenca negativa como credito para outro limite;
            // tempo e distancia adicionais precisam passar nas duas verificacoes.
            BigDecimal extraMin = duracao.subtract(direto.minutos()).max(BigDecimal.ZERO);
            BigDecimal extraKm = distancia.subtract(distancias.get(i)).subtract(direto.km()).max(BigDecimal.ZERO);
            if (extraMin.compareTo(comida ? l.comidaMin() : l.outrosMin()) > 0 || extraKm.compareTo(comida ? l.comidaKm() : l.outrosKm()) > 0)
                throw new IllegalStateException("Desvio excede o limite da rota.");
            BigDecimal prazo = cargas.get(i).prazo();
            // Se o lote contem comida pronta, aplica o teto de 30 minutos, conservando
            // um prazo cadastrado ainda menor. A contagem parte do marco apos a coleta.
            if (comida) prazo = prazo == null ? new BigDecimal("30") : prazo.min(new BigDecimal("30"));
            if (prazo != null && duracao.compareTo(prazo) > 0) throw new IllegalStateException("Prazo excedido na rota planejada.");
        }
    }
    /** Peso JDBC vezes quantidade; metadados internos explicitos, sem inferir pela categoria. */
    private Carga carregarCarga(Connection c, long comanda) throws SQLException {
        BigDecimal kg = BigDecimal.ZERO, prazo = null;
        boolean alimento = false, comida = false, encontrou = false;
        var itens = new ItemPedidoDao(c);
        for (int pagina = 0; ; pagina += 100) {
            var lista = itens.listarPorComanda(comanda, 100, pagina);
            for (var item : lista) {
                encontrou = true;
                var produto = new ProdutoDao(c).buscarPorId(item.idProduto()).orElseThrow();
                if (produto.pesoKg() == null || produto.pesoKg().signum() <= 0) throw new IllegalStateException("Produto sem peso verificavel.");
                var info = Objects.requireNonNull(dados.produto(item.idProduto()), "Metadados logisticos ausentes.");
                // O peso e por unidade: multiplica pela quantidade comprada antes de somar.
                // O menor prazo protege o item mais restritivo; os flags acumulam com OU logico.
                kg = kg.add(produto.pesoKg().multiply(BigDecimal.valueOf(item.quantidade())));
                alimento |= info.alimento(); comida |= info.comidaPronta();
                if (info.prazoMinutos() != null) prazo = prazo == null ? info.prazoMinutos() : prazo.min(info.prazoMinutos());
            }
            if (lista.size() < 100) break;
        }
        if (!encontrou) throw new IllegalStateException("Comanda sem itens.");
        return new Carga(kg, alimento, comida, prazo);
    }
    /** Callback interno executado sob bloqueios e transacao unica. */
    @FunctionalInterface
    private interface Acao { boolean executar(Connection c, OrdemEntregaRegistro ordem, List<OrdemEntregaRegistro> ativas) throws SQLException; }
    /** Bloqueio do pedido serializa conclusoes de entregadores distintos da mesma compra. */
    private boolean operar(long entregador, long entrega, Acao acao) throws SQLException {
        TransacaoJdbc.validarId(entregador); TransacaoJdbc.validarId(entrega);
        return executar(c -> {
            var e = new EntregadorDao(c).buscarPorIdParaAtualizacao(entregador);
            if (e.isEmpty() || e.orElseThrow().statusOperacao() != StatusOperacaoEntregador.EM_ROTA) return false;
            var od = new OrdemEntregaDao(c);
            var inicial = od.buscarPorId(entrega);
            if (inicial.isEmpty() || !Objects.equals(inicial.orElseThrow().idEntregador(), entregador)) return false;
            long pedido = new ComandaLojistaDao(c).buscarPorId(inicial.orElseThrow().idComanda()).orElseThrow().idPedido();
            bloquearPedido(c, pedido);
            if (new PedidoMestreDao(c).buscarPorId(pedido).orElseThrow().statusPedido() != StatusPedidoMestre.PAGO) return false;
            // Rele o manifesto depois dos bloqueios. Uma consulta anterior serve para
            // localizar a compra, mas nao basta para autorizar transicao com dados atuais.
            var ativas = od.listarAtivasParaAtualizacao(entregador);
            var ordem = ativas.stream().filter(o -> o.id() == entrega).findFirst();
            return ordem.isPresent() && acao.executar(c, ordem.orElseThrow(), ativas);
        });
    }
    /** Nao libera veiculo que ainda possui outra comanda em andamento. */
    private static void liberarSeLivre(Connection c, long entregador) throws SQLException {
        if (!new OrdemEntregaDao(c).possuiOrdemAtiva(entregador)) exigir(new EntregadorDao(c).liberarSemOrdens(entregador));
    }
    /** SQL fixo do pai; compartilhado por todos os fluxos novos. */
    private static void bloquearPedido(Connection c, long pedido) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT id_pedido FROM pedido_mestre WHERE id_pedido=? FOR UPDATE")) {
            s.setLong(1, pedido);
            try (ResultSet r = s.executeQuery()) { if (!r.next()) throw new IllegalStateException("Compra ausente."); }
        }
    }
    /** Recusa duplicatas e normaliza a ordem de bloqueio/coleta. */
    private static List<Long> validarLote(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100) throw new IllegalArgumentException("Lote deve conter 1 a 100 ordens.");
        List<Long> copia = new ArrayList<>(List.copyOf(ids));
        copia.forEach(TransacaoJdbc::validarId); copia.sort(Long::compareTo);
        if (new HashSet<>(copia).size() != copia.size()) throw new IllegalArgumentException("Ordem duplicada no lote.");
        return List.copyOf(copia);
    }
    /** Ausencia de malha nunca equivale a rota aprovada. */
    private void exigirDados() {
        if (dados == null) throw new IllegalStateException("Configure malha e cadastro logistico antes de criar/aceitar entregas.");
    }
    /** Falha intermediaria deve reverter todas as alteracoes, nao confirmar uma parte. */
    private static void exigir(boolean ok) throws SQLException {
        if (!ok) throw new SQLException("Estado mudou; operacao integralmente revertida.");
    }
    /** Compartilha infraestrutura existente de commit, rollback e fechamento. */
    private <T> T executar(TransacaoJdbc.Operacao<T> operacao) throws SQLException {
        return TransacaoJdbc.executar(fabrica::abrir, operacao);
    }
}
