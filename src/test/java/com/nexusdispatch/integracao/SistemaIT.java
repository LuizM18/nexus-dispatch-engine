package com.nexusdispatch.integracao;

import com.nexusdispatch.dao.*;
import com.nexusdispatch.console.ConsoleNexus;
import com.nexusdispatch.enums.*;
import com.nexusdispatch.model.*;
import com.nexusdispatch.service.*;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

/** Integracao real em banco UUID descartavel; nao escreve no catalogo original. */
class SistemaIT {
    private static final String SENHA_TESTE = "Teste2026!";
    private static final String HASH_TESTE = new SenhaService().gerar(SENHA_TESTE.toCharArray());
    // Constroi o decimal a partir de texto, sem aproximacao binaria de double.
    // Assim, o valor esperado pode ser comparado com o contrato monetario do modelo.
    static BigDecimal b(String s) { return new BigDecimal(s); }
    // Transforma uma condicao falsa em AssertionError para interromper o teste.
    // A mensagem/contador identifica a verificacao; imprimir sucesso nao substitui a assercao.
    static void ok(boolean valor, String mensagem) {
        if (!valor) throw new AssertionError(mensagem);
    }
    @FunctionalInterface interface Tentativa { void executar() throws Exception; }
    // Executa um caso que deve ser recusado pela regra de dominio. Falha de SQL
    // nao e convertida em sucesso: continua sendo um problema distinto do argumento/estado.
    static void recusar(Tentativa t) throws Exception {
        try { t.executar(); throw new AssertionError("Operacao deveria falhar"); }
        catch (IllegalStateException | IllegalArgumentException esperado) { return; }
    }
    static void sql(Connection c, String texto) throws SQLException {
        try (Statement s = c.createStatement()) { s.execute(texto); }
    }
    static long contar(Connection c, String tabela) throws SQLException {
        // Identificadores internos dos testes, sem entrada do usuario.
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT COUNT(*) FROM " + tabela)) {
            r.next(); return r.getLong(1);
        }
    }
    // Fixture logistica controlada pelo teste. Flags alteram uma restricao por vez
    // para isolar alcance, peso, prazo e ausencia de metadados. As distancias sao
    // sinteticas e nao representam ruas reais ou um cadastro de producao.
    static final class Dados implements EntregaService.DadosLogisticos {
        final Map<Long, String> lojas = new HashMap<>();
        boolean comida, longe, desvio, prazoCurto, vanSemCapacidade, produtoSemDados;
        BigDecimal capacidade = b("20");
        final MalhaEntrega malha;
        // Pontos recebem coordenadas em uma linha; distancia e a diferenca absoluta
        // e tempo e quatro vezes essa distancia. Cria trechos nos dois sentidos para
        // todos os pares, permitindo calcular os resultados esperados independentemente.
        Dados() {
            List<MalhaEntrega.Trecho> ts = new ArrayList<>();
            Set<TipoVeiculo> todos = EnumSet.allOf(TipoVeiculo.class);
            String[] pontos = {"P", "A", "B", "C", "F"};
            BigDecimal[] x = {b("0"), b("1"), b("1.5"), b("3"), b("30")};
            for (int i = 0; i < pontos.length; i++) for (int j = 0; j < pontos.length; j++) if (i != j) {
                BigDecimal km = x[i].subtract(x[j]).abs();
                ts.add(new MalhaEntrega.Trecho(pontos[i], pontos[j], km, km.multiply(b("4")), todos));
            }
            malha = new MalhaEntrega(ts);
        }
        public MalhaEntrega malha() { return malha; }
        public String posicaoEntregador(long id) { return longe ? "F" : "P"; }
        public String pontoLoja(long id) {
            String ponto = lojas.get(id);
            if (ponto == null) throw new IllegalStateException("Loja sem ponto");
            return desvio && ponto.equals("B") ? "F" : ponto;
        }
        public String pontoCliente(long id) { return "C"; }
        public EntregaService.ProdutoLogistico produto(long id) {
            if (produtoSemDados) throw new IllegalStateException("Produto sem metadados");
            return new EntregaService.ProdutoLogistico(comida, comida, prazoCurto ? b("1") : comida ? b("30") : null);
        }
        public BigDecimal capacidadeKg(EntregadorRegistro e) { return vanSemCapacidade ? null : capacidade; }
        public BigDecimal minutosColeta(long id) { return b("2"); }
    }
    // Testa caminho conhecido, percurso zero e restricoes de sentido/veiculo.
    // Uma rua A -> B nao implica a existencia de B -> A no grafo dirigido.
    static void malha() throws Exception {
        var d = new Dados();
        ok(d.malha.calcular("P", "C", TipoVeiculo.MOTO).km().compareTo(b("3")) == 0, "Menor caminho da malha");
        ok(d.malha.calcular("A", "A", TipoVeiculo.BICICLETA).km().signum() == 0, "Mesmo ponto conhecido");
        recusar(() -> d.malha.calcular("desconhecido", "A", TipoVeiculo.CARRO));
        var restrita = new MalhaEntrega(List.of(new MalhaEntrega.Trecho("A", "B", b("1"), b("5"), Set.of(TipoVeiculo.BICICLETA))));
        recusar(() -> restrita.calcular("A", "B", TipoVeiculo.VAN));
        recusar(() -> restrita.calcular("B", "A", TipoVeiculo.BICICLETA));
        ok(restrita.calcular("A", "B", TipoVeiculo.BICICLETA).minutos().compareTo(b("5")) == 0, "Veiculo permitido");
    }
    // Recupera as ordens criadas pelo checkout e exige uma por comanda. Ordena
    // os IDs para que os testes de coleta sigam a sequencia usada no aceite.
    static List<Long> ordens(Connection c, CompraService.Resultado r) throws SQLException {
        List<Long> ids = new ArrayList<>();
        for (var parte : r.comandas()) {
            var lista = new OrdemEntregaDao(c).listarPorComanda(parte.id(), 100, 0);
            ok(lista.size() == 1, "Uma ordem por comanda"); ids.add(lista.getFirst().id());
        }
        return ids.stream().sorted().toList();
    }
    // Avanca cada comanda pelas duas etapas do lojista antes do aceite.
    // Criar uma ordem no checkout nao significa que os produtos ja estejam prontos.
    static void pronto(EntregaService s, CompraService.Resultado r) throws SQLException {
        for (var p : r.comandas()) {
            ok(s.iniciarPreparo(p.idLojista(), p.id()), "Iniciar preparo");
            ok(s.disponibilizarColeta(p.idLojista(), p.id()), "Disponibilizar coleta");
        }
    }
    // Consulta o estado novamente no banco, em vez de confiar num objeto anterior
    // que poderia estar desatualizado apos a operacao do service.
    static StatusOperacaoEntregador estado(Connection c, long id) throws SQLException {
        return new EntregadorDao(c).buscarPorId(id).orElseThrow().statusOperacao();
    }
    // Cria identidade ficticia com documento/email distintos pelo nome. O hash
    // fixo tem tamanho valido para o DAO e nao representa uma senha utilizavel.
    static long usuario(Connection c, String nome, PerfilUsuario perfil) throws SQLException {
        return new UsuarioDao(c).inserir(nome, "DOC-" + nome, nome + "@example.test", HASH_TESTE, perfil);
    }
    // Percorre aceite, todas as coletas e todas as conclusoes. A cada conclusao
    // confere que o entregador so fica livre depois da ultima parte ativa.
    static void ciclo(Connection c, EntregaService s, long e, List<Long> ids) throws SQLException {
        ok(s.aceitarRota(e, ids), "Aceita lote completo");
        for (long id : ids) ok(s.registrarColeta(e, id), "Coleta vinculada");
        for (int i = 0; i < ids.size(); i++) {
            ok(s.concluirEntrega(e, ids.get(i)), "Conclui parte");
            ok(estado(c, e) == (i + 1 == ids.size() ? StatusOperacaoEntregador.DISPONIVEL : StatusOperacaoEntregador.EM_ROTA), "Liberacao somente na ultima parte");
        }
    }
    /** Reproduz o CRUD da banca pelo menu, em instancias distintas ligadas ao mesmo MySQL. */
    static void verificarCrudNoTerminal(CompraService.FabricaConexao fabrica, long lojista) throws Exception {
        String loja = Long.toString(lojista);
        String nome = "Produto da banca";
        var produtos = new ProdutoService(fabrica::abrir);
        String cadastro = executarTerminal(fabrica, "1", loja, "2", nome, "OUTROS", "Item descartavel",
                "12,50", "0,250", "4", "s", "3", "0", "0");
        long id = produtos.listar(lojista, 20, 0).stream().filter(p -> p.nome().equals(nome)).findFirst().orElseThrow().id();
        String item = Long.toString(id);
        String registro = "ID: " + item + System.lineSeparator() + "Nome: " + nome;
        ok(cadastro.indexOf(registro) >= 0 && cadastro.indexOf(registro) != cadastro.lastIndexOf(registro)
                && cadastro.contains("Produto cadastrado com sucesso!"),
                "Terminal cadastra e lista o produto salvo");

        String editado = "Produto da banca editado";
        try (var conexao = fabrica.abrir()) {
            sql(conexao, "UPDATE produto SET status_ativo = NULL WHERE id_produto = " + id);
        }
        String edicao = executarTerminal(fabrica, "1", loja, "4", item, "5", item, editado, "OUTROS", "",
                "15,90", "0,250", "0");
        ok(edicao.contains(registro) && edicao.contains("Operacao concluida."),
                "Outra abertura do terminal busca e atualiza o mesmo registro");
        ok(produtos.buscar(lojista, id).orElseThrow().ativo() == null,
                "Editar pelo terminal preserva o status nao informado");

        String exclusao = executarTerminal(fabrica, "1", loja, "4", item, "8", item, "s", "4", item, "0");
        ok(exclusao.contains(String.join(System.lineSeparator(), "ID: " + item, "Nome: " + editado,
                "Categoria: OUTROS", "Preco: R$ 15.90"))
                && exclusao.contains("Produto nao encontrado para este lojista.") && produtos.buscar(lojista, id).isEmpty(),
                "Terminal mostra a edicao persistida, executa DELETE e confirma ausencia no MySQL");
    }

    // StringReader fornece as respostas como linhas digitadas; StringWriter captura
    // a saida para assercoes. Usa a entrada legada executar, nao o menu por perfil.
    static String executarTerminal(CompraService.FabricaConexao fabrica, String... comandos) throws Exception {
        var texto = new StringWriter();
        new ConsoleNexus(new ProdutoService(fabrica::abrir), new StringReader(String.join("\n", comandos) + "\n"),
                new PrintWriter(texto, true)).executar();
        return texto.toString();
    }

    /** Executa os menus da banca contra o MySQL descartavel, com um novo console por sessao. */
    static String executarTerminalPorPerfil(CompraService.FabricaConexao fabrica, CompraService compra, String... comandos)
            throws Exception {
        var texto = new StringWriter();
        new ConsoleNexus(new ProdutoService(fabrica::abrir), new CadastroService(fabrica::abrir, new SenhaService()),
                new UsuarioService(fabrica::abrir), new LojistaService(fabrica::abrir), new EntregadorService(fabrica::abrir),
                compra, new StringReader(String.join("\n", comandos) + "\n"), new PrintWriter(texto, true)).executarPorPerfil();
        return texto.toString();
    }

    static void verificarTerminalPorPerfil(Connection c, CompraService.FabricaConexao fabrica, long cliente,
            long loja, long produto) throws Exception {
        var compra = new CompraService(fabrica, b("7"), p -> true);
        var catalogo = new ProdutoService(fabrica::abrir);
        var dao = new UsuarioDao(c);
        String emailLoja = dao.buscarPorId(loja).orElseThrow().email();
        String emailCliente = dao.buscarPorId(cliente).orElseThrow().email();
        String idLoja = Long.toString(loja), idProduto = Long.toString(produto);
        int saldo = catalogo.buscar(loja, produto).orElseThrow().estoqueDisponivel();
        String lojaCli = executarTerminalPorPerfil(fabrica, compra, "1", emailLoja, SENHA_TESTE,
                "1", "Caneca do roteiro", "OUTROS", "", "8,50", "0,5", "3", "s", "2", "0", "0", "0");
        String idRetirado = catalogo.listar(loja, 20, 0).stream().filter(p -> p.nome().equals("Caneca do roteiro"))
                .findFirst().orElseThrow().id().toString();
        String registro = "ID: " + idRetirado + System.lineSeparator() + "Nome: Caneca do roteiro";
        ok(lojaCli.indexOf(registro) >= 0 && lojaCli.indexOf(registro) != lojaCli.lastIndexOf(registro)
                && lojaCli.contains("=== CADASTRO DE PRODUTOS ===")
                && lojaCli.contains("1. Cadastrar produto" + System.lineSeparator() + "2. Listar produtos"),
                "Novo menu lojista cadastra e lista produto");

        // As strings sao respostas sequenciais aos menus: seleciona cliente/loja,
        // adiciona e edita itens, remove um produto e confirma a compra. As consultas
        // abaixo conferem o efeito no MySQL, alem do texto impresso pelo console.
        String clienteCli = executarTerminalPorPerfil(fabrica, compra, "1", emailCliente, SENHA_TESTE, "1", "?", idLoja, "0", "2",
                "2", idProduto, "1", "2", idRetirado, "1", "3", idProduto, "2", "4", idRetirado, "1", "0",
                "1", idLoja, "0", "2", "1", "5", "1", "s", "1", "0", "3", "1", "0", "0", "0", "0");
        var pedido = compra.listarHistorico(cliente, 1, 0).getFirst();
        var partes = compra.buscarCompra(cliente, pedido.id()).orElseThrow().comandas();
        ok(clienteCli.contains("Produto retirado do carrinho.") && clienteCli.contains("Pagamento: AUTORIZADO")
                && clienteCli.contains("Carrinho vazio.") && clienteCli.contains("MEUS PEDIDOS")
                && clienteCli.contains("ID " + loja + " - Loja A") && !clienteCli.contains(emailLoja),
                "Terminal adiciona, altera, retira, confirma e mostra historico");
        ok(partes.size() == 1 && partes.getFirst().itens().size() == 1
                && partes.getFirst().itens().getFirst().idProduto() == produto
                && partes.getFirst().itens().getFirst().quantidade() == 2
                && catalogo.buscar(loja, produto).orElseThrow().estoqueDisponivel() == saldo - 2
                && catalogo.buscar(loja, Long.parseLong(idRetirado)).orElseThrow().estoqueDisponivel() == 3,
                "Pedido e baixa contêm somente o item mantido no carrinho");
        String remover = executarTerminalPorPerfil(fabrica, compra, "1", emailLoja, SENHA_TESTE,
                "7", idRetirado, "s", "3", idRetirado, "0", "0");
        ok(remover.contains("Produto nao encontrado para este lojista.")
                && catalogo.buscar(loja, Long.parseLong(idRetirado)).isEmpty(), "Lojista exclui produto sem historico");

        String senhaTeste = SENHA_TESTE;
        executarTerminalPorPerfil(fabrica, compra, "2", "3", "Entrega do roteiro", "CLI-E", "entrega-cli@example.test",
                "BICICLETA", "", "Centro", senhaTeste, senhaTeste, "0");
        long entregador = dao.buscarPorEmail("entrega-cli@example.test").orElseThrow().id();
        ok(estado(c, entregador) == StatusOperacaoEntregador.INATIVO, "Perfil criado inativo pelo terminal");
        String entregadorCli = executarTerminalPorPerfil(fabrica, compra,
                "1", "entrega-cli@example.test", senhaTeste, "2", "3", "0", "0");
        ok(entregadorCli.contains("Situacao: DISPONIVEL") && estado(c, entregador) == StatusOperacaoEntregador.INATIVO,
                "Disponibilidade e inativacao pelo terminal");

        var cadastroAdmin = new CadastroService(fabrica::abrir, new SenhaService());
        var saidaAdmin = new StringWriter();
        var configuracao = String.join("\n", "Administrador do roteiro", "CLI-ADMIN", "admin-cli@example.test",
                senhaTeste, senhaTeste) + "\n";
        new ConsoleNexus(catalogo, cadastroAdmin, new StringReader(configuracao), new PrintWriter(saidaAdmin, true))
                .executarCadastroAdministradorInicial();
        String admin = Long.toString(dao.buscarPorEmail("admin-cli@example.test").orElseThrow().id());
        ok(saidaAdmin.toString().contains("Administrador cadastrado. ID " + admin), "Provisionamento inicial pelo terminal");
        recusar(() -> cadastroAdmin.cadastrarAdministradorInicial(
                new CadastroService.DadosUsuario("Segundo admin", "CLI-ADMIN2", "admin2-cli@example.test"), senhaTeste.toCharArray()));
        String bloqueio = executarTerminalPorPerfil(fabrica, compra,
                "1", admin, senhaTeste, "1", "admin-cli@example.test", "senha incorreta",
                "1", emailCliente, senhaTeste, "4", "0", "4", "0");
        ok(bloqueio.contains("Não foi possível entrar.") && bloqueio.contains("CLIENTE | ")
                && bloqueio.contains("Você saiu da conta.") && !bloqueio.contains("Gerenciar usuarios"),
                "ID e senha incorreta nao autenticam; cliente e sessao encerrada nao acessam o admin");
        String adminCli = executarTerminalPorPerfil(fabrica, compra, "1", "admin-cli@example.test", senhaTeste,
                "1", "1", "0", "3", "1", "Cliente administrativo",
                "CLI-C", "cliente-cli@example.test", senhaTeste, senhaTeste, "0", "3", pedido.id().toString(), "0", "0");
        ok(adminCli.contains("Nome: Entrega do roteiro" + System.lineSeparator() + "Perfil: ENTREGADOR")
                && adminCli.contains("Pedido " + pedido.id())
                && !adminCli.contains(senhaTeste), "Admin lista usuarios e consulta pedidos sem expor senha");
        String novo = Long.toString(dao.buscarPorEmail("cliente-cli@example.test").orElseThrow().id());
        String adminCrud = executarTerminalPorPerfil(fabrica, compra, "1", "admin-cli@example.test", senhaTeste,
                "1", "2", novo, "4", novo,
                "Cliente administrativo editado", "CLI-C", "cliente-cli@example.test", "5", novo, "n", "6", novo, "s", "0", "0", "0");
        ok(adminCrud.lines().filter(l -> l.contains("Operacao concluida.")).count() == 3
                && dao.buscarPorId(Long.parseLong(novo)).isEmpty(), "Admin edita, desativa e remove cliente sem vinculos");

        var usuarios = new UsuarioService(fabrica::abrir);
        usuarios.definirAtivo(entregador, false);
        String inativo = executarTerminalPorPerfil(fabrica, compra, "1", "entrega-cli@example.test", senhaTeste, "0");
        ok(inativo.contains("Não foi possível entrar.") && !inativo.contains("Consultar perfil"),
                "Conta desativada nao entra mesmo com a senha certa");

        long outraLoja = usuario(c, "lojaexclusiva", PerfilUsuario.LOJISTA);
        new LojistaDao(c).inserir(new LojistaRegistro(outraLoja, "Loja exclusiva", b("5"), "Centro", "Rua B"));
        var produtoAlheio = catalogo.cadastrar(new ProdutoRegistro(null, outraLoja,
                "Item exclusivo", "OUTROS", null, b("10"), b("1"), 3, true));
        String isolado = executarTerminalPorPerfil(fabrica, compra, "1", emailLoja, senhaTeste,
                "4", produtoAlheio.id().toString(), "0", "0");
        ok(isolado.contains("Produto nao encontrado para este lojista.")
                && !isolado.contains("Item exclusivo") && !isolado.contains("ID da loja ("),
                "Lojista entra na propria loja e nao pode abrir a edicao de produto alheio");

        var itens = List.of(new CompraService.ItemCarrinho(produto, 1, null));
        var antes = compra.cotar(cliente, itens);
        var original = catalogo.buscar(loja, produto).orElseThrow();
        var alterado = new ProdutoRegistro(original.id(), loja, original.nome(), original.categoria(),
                original.descricao(), original.precoUnitario().add(b("1")), original.pesoKg(),
                original.estoqueDisponivel(), original.ativo());
        ok(catalogo.atualizar(alterado), "Simula alteracao de preco depois da revisao");
        recusar(() -> compra.comprarConfirmado(cliente, itens, MetodoPagamento.PIX, antes));
        ok(compra.listarHistorico(cliente, 1, 0).getFirst().id().equals(pedido.id())
                && catalogo.buscar(loja, produto).orElseThrow().estoqueDisponivel() == original.estoqueDisponivel(),
                "Total alterado exige nova confirmacao e preserva estoque e pedidos");
    }
    // Compara consulta privada, estorno, reposicao unica e bloqueio por preparo.
    // Tambem injeta falha na ultima escrita para provar que os efeitos anteriores
    // sao revertidos juntos, nao apenas que uma excecao foi lancada.
    static void verificarCancelamento(Connection c, CompraService compra, CompraService recusador,
            EntregaService entrega, long cliente, long entregador, List<CompraService.ItemCarrinho> carrinho)
            throws Exception {
        long outroCliente = usuario(c, "outrocliente", PerfilUsuario.CLIENTE);
        long produto = carrinho.getFirst().idProduto();
        var produtos = new ProdutoDao(c);
        int saldo = produtos.buscarPorId(produto).orElseThrow().estoqueDisponivel();
        var paga = compra.comprar(cliente, carrinho, MetodoPagamento.PIX);
        long pedido = paga.pedido().id();
        var detalhe = compra.buscarCompra(cliente, pedido).orElseThrow();
        ok(detalhe.comandas().size() == 2 && detalhe.comandas().stream().allMatch(p -> p.itens().size() == 1
                && p.entregas().size() == 1), "Detalhe tem itens e entregas de ambas as lojas");
        ok(compra.listarHistorico(cliente, 1, 0).getFirst().id() == pedido
                && compra.listarHistorico(cliente, 1, 1).getFirst().id() != pedido, "Historico paginado");
        ok(compra.listarHistorico(outroCliente, 10, 0).isEmpty() && compra.buscarCompra(outroCliente, pedido).isEmpty()
                && !compra.cancelarCompra(outroCliente, pedido), "Compra privada");

        // Reproduz atribuicao legada antes de PREPARANDO, impossivel pelo aceite atual.
        long ordemLegada = detalhe.comandas().getFirst().entregas().getFirst().id();
        sql(c, "UPDATE ordem_entrega SET id_entregador=" + entregador + " WHERE id_entrega=" + ordemLegada);
        sql(c, "UPDATE entregador SET status_operacao='EM_ROTA' WHERE id_entregador=" + entregador);
        sql(c, "CREATE TRIGGER falha_cancelamento BEFORE UPDATE ON pedido_mestre FOR EACH ROW "
                + "BEGIN IF NEW.status_pedido='CANCELADO' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='falha de teste'; END IF; END");
        try {
            compra.cancelarCompra(cliente, pedido);
            throw new AssertionError("Cancelamento deveria reverter");
        } catch (SQLException esperado) {
            ok("45000".equals(esperado.getSQLState()), "Falha induzida na ultima escrita do cancelamento");
        } finally {
            sql(c, "DROP TRIGGER falha_cancelamento");
        }
        var revertida = compra.buscarCompra(cliente, pedido).orElseThrow();
        ok(revertida.pedido().statusPedido() == StatusPedidoMestre.PAGO
                && revertida.pagamento().orElseThrow().statusPagamento() == StatusPagamento.AUTORIZADO
                && revertida.comandas().stream().allMatch(p -> p.comanda().statusComanda() == StatusComanda.PAGO
                        && p.entregas().getFirst().statusEntrega() == StatusOrdemEntrega.AGUARDANDO_COLETA)
                && estado(c, entregador) == StatusOperacaoEntregador.EM_ROTA
                && produtos.buscarPorId(produto).orElseThrow().estoqueDisponivel() == saldo - 1, "Rollback integral");
        ok(compra.cancelarCompra(cliente, pedido) && compra.cancelarCompra(cliente, pedido), "Cancelamento idempotente");
        var cancelada = compra.buscarCompra(cliente, pedido).orElseThrow();
        var estorno = cancelada.pagamento().orElseThrow();
        ok(cancelada.pedido().statusPedido() == StatusPedidoMestre.CANCELADO && estorno.statusPagamento() == StatusPagamento.ESTORNADO
                && estorno.valorProcessado().equals(paga.pedido().valorTotal())
                && estorno.codigoAutorizacao().equals(paga.pagamento().codigoAutorizacao()), "Estorno preserva valor integral e referencia");
        ok(cancelada.comandas().stream().allMatch(p -> p.comanda().statusComanda() == StatusComanda.CANCELADO
                && p.itens().size() == 1 && p.entregas().getFirst().statusEntrega() == StatusOrdemEntrega.CANCELADO),
                "Todas as partes canceladas e historico preservado");
        ok(estado(c, entregador) == StatusOperacaoEntregador.DISPONIVEL
                && produtos.buscarPorId(produto).orElseThrow().estoqueDisponivel() == saldo, "Libera entregador e repoe uma vez");

        var recusada = recusador.comprar(cliente, carrinho, MetodoPagamento.PIX);
        ok(compra.cancelarCompra(cliente, recusada.pedido().id()), "Cancela compra recusada");
        ok(compra.buscarCompra(cliente, recusada.pedido().id()).orElseThrow().pagamento().orElseThrow()
                .statusPagamento() == StatusPagamento.RECUSADO
                && produtos.buscarPorId(produto).orElseThrow().estoqueDisponivel() == saldo, "Recusa nao estorna nem repoe");
        recusar(() -> compra.tentarNovamente(cliente, recusada.pedido().id(),
                recusada.pagamento().codigoAutorizacao(), carrinho, MetodoPagamento.PIX));

        var emPreparo = compra.comprar(cliente, carrinho, MetodoPagamento.PIX);
        var parte = emPreparo.comandas().getFirst();
        ok(entrega.iniciarPreparo(parte.idLojista(), parte.id())
                && !compra.cancelarCompra(cliente, emPreparo.pedido().id()), "Uma loja preparando bloqueia a compra inteira");
        ok(compra.buscarCompra(cliente, emPreparo.pedido().id()).orElseThrow().pagamento().orElseThrow()
                .statusPagamento() == StatusPagamento.AUTORIZADO, "Bloqueio preserva autorizacao");

        // Duas conexoes disputam preparo e cancelamento da mesma compra. A barreira
        // libera as tarefas juntas; somente uma deve vencer. O resultado financeiro
        // precisa corresponder ao vencedor, sem misturar as duas operacoes.
        var disputa = compra.comprar(cliente, carrinho, MetodoPagamento.PIX);
        var primeira = disputa.comandas().getFirst();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch inicio = new CountDownLatch(1);
            Future<Boolean> preparo = pool.submit(() -> { inicio.await(); return entrega.iniciarPreparo(primeira.idLojista(), primeira.id()); });
            Future<Boolean> cancelamento = pool.submit(() -> { inicio.await(); return compra.cancelarCompra(cliente, disputa.pedido().id()); });
            inicio.countDown();
            boolean preparou = preparo.get(20, TimeUnit.SECONDS);
            boolean cancelou = cancelamento.get(20, TimeUnit.SECONDS);
            ok(preparou != cancelou, "Preparo e cancelamento sao mutuamente exclusivos");
            var finalDisputa = compra.buscarCompra(cliente, disputa.pedido().id()).orElseThrow();
            ok(finalDisputa.pagamento().orElseThrow().statusPagamento() == (cancelou
                    ? StatusPagamento.ESTORNADO : StatusPagamento.AUTORIZADO), "Pagamento segue o resultado da disputa");
        } finally { pool.shutdownNow(); }
    }
    @Test
    void validacoesLogisticas() throws Exception {
        malha();
        var semDados = new EntregaService(() -> { throw new AssertionError("Nao deveria conectar"); });
        recusar(() -> semDados.aceitarEntrega(1, 1));
        recusar(() -> semDados.aceitarRota(1, List.of(1L, 1L)));
        recusar(() -> semDados.aceitarRota(0, List.of(1L)));
    }

    @Test
    // Cenario integrado com duas lojas. Comeca pelas restricoes logisticas, depois
    // exercita ciclo completo, rollback induzido, disputas e cancelamento. Cada
    // service abre conexoes dedicadas para o catalogo criado por BancoTeste.
    void compraEntregaECancelamento() throws Exception {
        List<Connection> abertas = new CopyOnWriteArrayList<>();
        try (var banco = BancoTeste.criar(); Connection c = banco.abrir()) {
            CompraService.FabricaConexao fabrica = () -> {
                Connection nova = banco.abrir();
                abertas.add(nova);
                return nova;
            };
            Dados dados = new Dados();
            var entrega = new EntregaService(fabrica::abrir, dados, b("10"), EntregaService.limitesIniciais());
            var compra = new CompraService(fabrica, b("7"), p -> true, entrega);
            long cliente = usuario(c, "cliente", PerfilUsuario.CLIENTE);
            long lojaA = usuario(c, "lojaa", PerfilUsuario.LOJISTA), lojaB = usuario(c, "lojab", PerfilUsuario.LOJISTA);
            new LojistaDao(c).inserir(new LojistaRegistro(lojaA, "Loja A", b("5"), "Centro", "Rua A"));
            new LojistaDao(c).inserir(new LojistaRegistro(lojaB, "Loja B", b("5"), "Centro", "Rua B"));
            dados.lojas.put(lojaA, "A"); dados.lojas.put(lojaB, "B");
            long produtoA = new ProdutoDao(c).inserir(new ProdutoRegistro(null, lojaA, "Produto A", "OUTROS", null, b("1"), b("1"), 500, true));
            long produtoB = new ProdutoDao(c).inserir(new ProdutoRegistro(null, lojaB, "Produto B", "OUTROS", null, b("2"), b("1"), 500, true));
            var carrinho = List.of(new CompraService.ItemCarrinho(produtoA, 1, null), new CompraService.ItemCarrinho(produtoB, 1, null));
            long e = usuario(c, "moto", PerfilUsuario.ENTREGADOR), outro = usuario(c, "outro", PerfilUsuario.ENTREGADOR);
            new EntregadorDao(c).inserir(EntregadorRegistro.novoCadastro(e, "111", TipoVeiculo.MOTO, "Centro"));
            new EntregadorDao(c).inserir(EntregadorRegistro.novoCadastro(outro, "222", TipoVeiculo.MOTO, "Centro"));
            var es = new EntregadorService(fabrica::abrir);
            ok(es.definirDisponivel(e, true) && es.definirDisponivel(outro, true), "Disponibilidade inicial");
            var r = compra.comprar(cliente, carrinho, MetodoPagamento.PIX);
            List<Long> ids = ordens(c, r);
            var o1 = entrega.buscar(ids.get(0)).orElseThrow(); var o2 = entrega.buscar(ids.get(1)).orElseThrow();
            ok(o1.valorFrete().compareTo(b("0.07")) == 0 && o2.valorFrete().compareTo(b("0.14")) == 0, "Rateio proporcional por valor");
            ok(o1.taxaFreteRetida().compareTo(b("0.01")) == 0 && o2.taxaFreteRetida().compareTo(b("0.01")) == 0, "10 por cento por parcela HALF_UP");
            ok(!entrega.aceitarRota(e, ids), "Nao aceita antes de pronto");
            pronto(entrega, r);
            dados.longe = true; recusar(() -> entrega.aceitarRota(e, ids)); dados.longe = false;
            dados.capacidade = b("1"); recusar(() -> entrega.aceitarRota(e, ids)); dados.capacidade = b("20");
            dados.desvio = true; recusar(() -> entrega.aceitarRota(e, ids)); dados.desvio = false;
            dados.prazoCurto = true; recusar(() -> entrega.aceitarRota(e, ids)); dados.prazoCurto = false;
            ok(estado(c, e) == StatusOperacaoEntregador.DISPONIVEL && entrega.buscar(ids.get(0)).orElseThrow().idEntregador() == null, "Recusa nao reserva parte do lote");
            dados.comida = true;
            ok(entrega.aceitarRota(e, ids), "Moto aceita duas lojas com comida");
            ok(!entrega.registrarColeta(outro, ids.get(0)), "Outro ator nao coleta");
            recusar(() -> entrega.registrarColeta(e, ids.get(1)));
            ok(entrega.registrarColeta(e, ids.get(0)), "Primeira coleta");
            ok(!entrega.desistirAntesDaColeta(e, ids.get(0)), "Desistir apos coleta proibido");
            recusar(() -> entrega.concluirEntrega(e, ids.get(0)));
            ok(entrega.registrarColeta(e, ids.get(1)), "Segunda coleta");
            sql(c, "CREATE TRIGGER falha_conclusao BEFORE UPDATE ON comanda_lojista FOR EACH ROW BEGIN IF NEW.status_comanda='CONCLUIDO' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='falha de teste'; END IF; END");
            try { entrega.concluirEntrega(e, ids.get(0)); throw new AssertionError("Falha SQL esperada"); }
            catch (SQLException esperado) { ok("45000".equals(esperado.getSQLState()), "Falha induzida pelo trigger"); }
            ok(entrega.buscar(ids.get(0)).orElseThrow().statusEntrega() == StatusOrdemEntrega.EM_TRANSITO, "Rollback da ordem apos falha na comanda");
            sql(c, "DROP TRIGGER falha_conclusao");
            ok(entrega.concluirEntrega(e, ids.get(0)), "Conclui primeira parte");
            ok(estado(c, e) == StatusOperacaoEntregador.EM_ROTA, "Nao libera com outra parte ativa");
            ok(new PedidoMestreDao(c).buscarPorId(r.pedido().id()).orElseThrow().statusPedido() == StatusPedidoMestre.PAGO, "Compra aguarda ultima parte");
            ok(entrega.concluirEntrega(e, ids.get(1)), "Conclui ultima parte");
            ok(estado(c, e) == StatusOperacaoEntregador.DISPONIVEL, "Libera automaticamente");
            ok(new PedidoMestreDao(c).buscarPorId(r.pedido().id()).orElseThrow().statusPedido() == StatusPedidoMestre.CONCLUIDO, "Compra concluida");
            ok(!entrega.concluirEntrega(e, ids.get(1)), "Repeticao nao duplica conclusao");
            dados.comida = false;
            var segunda = compra.comprar(cliente, carrinho, MetodoPagamento.PIX); pronto(entrega, segunda);
            var is = ordens(c, segunda);
            ok(entrega.aceitarRota(e, is), "Novo lote");
            ok(entrega.desistirAntesDaColeta(e, is.get(0)), "Devolve primeira atribuicao");
            ok(estado(c, e) == StatusOperacaoEntregador.EM_ROTA, "Mantem ocupado pela outra ordem");
            ok(entrega.desistirAntesDaColeta(e, is.get(1)), "Devolve ultima atribuicao");
            ok(estado(c, e) == StatusOperacaoEntregador.DISPONIVEL, "Desistencia libera apenas no final");
            ok(entrega.buscar(is.get(0)).orElseThrow().statusEntrega() == StatusOrdemEntrega.AGUARDANDO_COLETA, "Desistencia nao cancela ordem");
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                // CountDownLatch funciona como barreira de largada: as duas tarefas esperam
                // antes de disputar o mesmo lote. Future.get limita a espera e propaga falhas
                // da thread; apenas contar mensagens de sucesso nao provaria exclusividade.
                CountDownLatch inicio = new CountDownLatch(1);
                Future<Boolean> f1 = pool.submit(() -> { inicio.await(); return entrega.aceitarRota(e, is); });
                Future<Boolean> f2 = pool.submit(() -> { inicio.await(); return entrega.aceitarRota(outro, is); });
                inicio.countDown(); boolean a = f1.get(20, TimeUnit.SECONDS), z = f2.get(20, TimeUnit.SECONDS);
                ok(a != z, "Disputa de lote tem um vencedor");
                long vencedor = a ? e : outro;
                for (long id : is) ok(Objects.equals(entrega.buscar(id).orElseThrow().idEntregador(), vencedor), "Lote inteiro pertence ao vencedor");
                for (long id : is) ok(entrega.desistirAntesDaColeta(vencedor, id), "Devolve lote disputado");
            } finally { pool.shutdownNow(); }
            long carro = usuario(c, "carro", PerfilUsuario.ENTREGADOR);
            new EntregadorDao(c).inserir(EntregadorRegistro.novoCadastro(carro, "333", TipoVeiculo.CARRO, "Centro")); es.definirDisponivel(carro, true);
            dados.comida = true; recusar(() -> entrega.aceitarRota(carro, is)); dados.comida = false;
            ciclo(c, entrega, carro, is);
            long antesPedidos = contar(c, "pedido_mestre"), antesOrdens = contar(c, "ordem_entrega");
            int estoque = new ProdutoDao(c).buscarPorId(produtoA).orElseThrow().estoqueDisponivel();
            dados.produtoSemDados = true;
            recusar(() -> compra.comprar(cliente, carrinho, MetodoPagamento.PIX)); dados.produtoSemDados = false;
            ok(contar(c, "pedido_mestre") == antesPedidos && contar(c, "ordem_entrega") == antesOrdens, "Falha logistica reverte checkout inteiro");
            ok(new ProdutoDao(c).buscarPorId(produtoA).orElseThrow().estoqueDisponivel() == estoque, "Rollback preserva estoque");
            var recusada = new CompraService(fabrica, b("7"), p -> false, entrega).comprar(cliente, carrinho, MetodoPagamento.PIX);
            ok(recusada.comandas().isEmpty() && contar(c, "ordem_entrega") == antesOrdens, "Pagamento recusado nao cria entrega");
            var retry = compra.tentarNovamente(cliente, recusada.pedido().id(), recusada.pagamento().codigoAutorizacao(), carrinho, MetodoPagamento.PIX);
            ok(ordens(c, retry).size() == 2, "Retry autorizado tambem cria ordens");
            // Produtos totalizam 3.00; taxa 0.33% arredonda para 0.01. O rateio deve
            // entregar esse unico centavo a maior fracao, conservando o total cobrado.
            var residuo = new CompraService(fabrica, b("0.33"), p -> true, entrega).comprar(cliente, carrinho, MetodoPagamento.PIX);
            var restos = ordens(c, residuo);
            ok(entrega.buscar(restos.get(0)).orElseThrow().valorFrete().compareTo(b("0.00")) == 0
                    && entrega.buscar(restos.get(1)).orElseThrow().valorFrete().compareTo(b("0.01")) == 0,
                    "Centavo residual vai para maior resto e conserva taxa exata");
            pronto(entrega, retry);
            var ultimas = ordens(c, retry);
            long van = usuario(c, "van", PerfilUsuario.ENTREGADOR);
            new EntregadorDao(c).inserir(EntregadorRegistro.novoCadastro(van, "444", TipoVeiculo.VAN, "Centro"));
            es.definirDisponivel(van, true);
            dados.vanSemCapacidade = true; recusar(() -> entrega.aceitarRota(van, ultimas)); dados.vanSemCapacidade = false;
            dados.capacidade = b("0.5"); recusar(() -> entrega.aceitarRota(van, ultimas)); dados.capacidade = b("20");
            var misto = List.of(is.getFirst(), ultimas.getFirst());
            ok(!entrega.aceitarRota(e, misto), "Nao agrega compra concluida com outra compra");
            var parametros = new HashMap<>(EntregaService.limitesIniciais());
            parametros.put(TipoVeiculo.MOTO, new EntregaService.Limites(b("0.99"), b("2"), b("7"), b("6"), b("20")));
            var curto = new EntregaService(fabrica::abrir, dados, b("10"), parametros);
            recusar(() -> curto.aceitarEntrega(e, ultimas.getFirst()));
            parametros.put(TipoVeiculo.MOTO, new EntregaService.Limites(b("1"), b("2"), b("7"), b("6"), b("20")));
            var exato = new EntregaService(fabrica::abrir, dados, b("10"), parametros);
            ok(exato.aceitarEntrega(e, ultimas.get(0)), "Aceita exatamente no limite configurado");
            ok(entrega.aceitarEntrega(outro, ultimas.get(1)), "Outra parte pode usar outro entregador");
            ok(entrega.registrarColeta(e, ultimas.get(0)) && entrega.registrarColeta(outro, ultimas.get(1)), "Coletas separadas");
            // Conclusoes de entregadores distintos disputam o pedido mestre. Ambas
            // podem concluir suas ordens, mas a ultima deve observar todas as comandas
            // concluidas e finalizar a compra, sem deixa-la presa em PAGO.
            ExecutorService finais = Executors.newFixedThreadPool(2);
            try {
                CountDownLatch inicio = new CountDownLatch(1);
                Future<Boolean> f1 = finais.submit(() -> { inicio.await(); return entrega.concluirEntrega(e, ultimas.get(0)); });
                Future<Boolean> f2 = finais.submit(() -> { inicio.await(); return entrega.concluirEntrega(outro, ultimas.get(1)); });
                inicio.countDown();
                ok(f1.get(20, TimeUnit.SECONDS) && f2.get(20, TimeUnit.SECONDS), "Conclusoes simultaneas de entregadores distintos");
                ok(new PedidoMestreDao(c).buscarPorId(retry.pedido().id()).orElseThrow().statusPedido() == StatusPedidoMestre.CONCLUIDO,
                        "Bloqueio do pedido evita compra presa em PAGO");
                ok(estado(c, e) == StatusOperacaoEntregador.DISPONIVEL && estado(c, outro) == StatusOperacaoEntregador.DISPONIVEL,
                        "Ambos liberados depois das conclusoes");
            } finally { finais.shutdownNow(); }
            verificarCancelamento(c, compra, new CompraService(fabrica, b("7"), p -> false, entrega),
                    entrega, cliente, e, carrinho);
            for (Connection aberta : abertas) ok(aberta.isClosed(), "Conexao dedicada fechada");
        }
    }

    @Test
    // Cada abertura do console recebe uma entrada nova, mas compartilha o banco
    // do cenario. Assim, o dado encontrado numa segunda sessao prova persistencia
    // e nao apenas conservacao do estado em memoria da primeira instancia.
    void terminalPersisteOperacoesPorPerfil() throws Exception {
        try (var banco = BancoTeste.criar(); Connection c = banco.abrir()) {
            long cliente = usuario(c, "cliente", PerfilUsuario.CLIENTE);
            long loja = usuario(c, "loja", PerfilUsuario.LOJISTA);
            new LojistaDao(c).inserir(new LojistaRegistro(loja, "Loja A", b("5"), "Centro", "Rua A"));
            long produto = new ProdutoDao(c).inserir(new ProdutoRegistro(null, loja, "Produto A", "OUTROS",
                    null, b("1"), b("1"), 500, true));
            verificarCrudNoTerminal(banco::abrir, loja);
            verificarTerminalPorPerfil(c, banco::abrir, cliente, loja, produto);
        }
    }
}
