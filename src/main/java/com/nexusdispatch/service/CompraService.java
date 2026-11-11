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
 * Checkout JDBC com pagamento SIMULADO. O chamador fornece o ID do cliente
 * autenticado; este service confere perfil/atividade e propriedade do pedido,
 * mas nao implementa login. Cada chamada usa uma conexao nova e dedicada.
 * Com EntregaService configurado, cria ordens na mesma transacao do pagamento.
 * Construtores anteriores preservam checkout isolado para compatibilidade;
 * o fluxo completo deve usar o construtor com cadastro logistico.
 */
public final class CompraService {
    @FunctionalInterface
    public interface FabricaConexao { Connection abrir() throws SQLException; }

    /** Implementacao interna, rapida e sem rede; nunca receber aprovacao do cliente HTTP. */
    @FunctionalInterface
    public interface SimuladorPagamento { boolean autorizar(PagamentoRegistro tentativa); }

    /** O carrinho informa identidade/quantidade, nunca preco ou comissao. */
    public record ItemCarrinho(long idProduto, int quantidade, String observacao) {
        public ItemCarrinho {
            validarId(idProduto);
            if (quantidade <= 0) throw new IllegalArgumentException("Quantidade deve ser positiva.");
            // Reutiliza o contrato de tamanho/normalizacao da observacao.
            observacao = ItemPedidoRegistro.novoItem(1, idProduto, quantidade,
                    BigDecimal.ONE, observacao).observacao();
        }
    }

    /** Recusa e um resultado persistido, diferente de uma falha de infraestrutura. */
    public record Resultado(PedidoMestreRegistro pedido, PagamentoRegistro pagamento,
            List<ComandaLojistaRegistro> comandas) {
        public Resultado { comandas = List.copyOf(comandas); }
    }

    /** Total exibido ao cliente antes da confirmacao do pedido. */
    public record Cotacao(BigDecimal produtos, BigDecimal taxa) {
        public BigDecimal total() { return produtos.add(taxa); }
    }

    /** Parte da compra com precos aplicados e entregas persistidas. */
    public record DetalheComanda(ComandaLojistaRegistro comanda, List<ItemPedidoRegistro> itens,
            List<OrdemEntregaRegistro> entregas) {
        public DetalheComanda {
            itens = List.copyOf(itens);
            entregas = List.copyOf(entregas);
        }
    }

    /** Compras antigas ou ainda nao processadas podem estar sem pagamento. */
    public record DetalheCompra(PedidoMestreRegistro pedido, Optional<PagamentoRegistro> pagamento,
            List<DetalheComanda> comandas) {
        public DetalheCompra { comandas = List.copyOf(comandas); }
    }

    private record Reposicao(long lojista, ItemPedidoRegistro item) { }

    // Linha associa a quantidade solicitada ao produto lido sob bloqueio.
    // Preco e lojista vem do banco; o carrinho de entrada nao escolhe esses valores.
    private record Linha(ItemCarrinho entrada, ProdutoRegistro produto) {
        BigDecimal subtotal() {
            return produto.precoUnitario().multiply(BigDecimal.valueOf(entrada.quantidade()));
        }
    }
    // Agrupa as linhas validadas, os lojistas e seus subtotais. Esse resultado
    // intermediario alimenta pagamento e comandas sem recalcular cada fonte separadamente.
    private record Carrinho(List<Linha> linhas, Map<Long, LojistaRegistro> lojistas,
            Map<Long, BigDecimal> subtotais, BigDecimal produtos, BigDecimal taxa) { }

    private final FabricaConexao fabrica;
    private final SimuladorPagamento simulador;
    private final BigDecimal percentualTaxa;
    private final EntregaService entregas;

    /** Sem percentual padrao: a configuracao precisa informar o valor explicitamente. */
    public CompraService(BigDecimal percentualTaxa, SimuladorPagamento simulador) {
        this(ConexaoBD::conectar, percentualTaxa, simulador);
    }

    public CompraService(FabricaConexao fabrica, BigDecimal percentualTaxa,
            SimuladorPagamento simulador) {
        this(fabrica, percentualTaxa, simulador, null);
    }

    /** Checkout completo: falha ao criar qualquer ordem reverte pagamento, estoque e comandas. */
    public CompraService(FabricaConexao fabrica, BigDecimal percentualTaxa,
            SimuladorPagamento simulador, EntregaService entregas) {
        this.fabrica = Objects.requireNonNull(fabrica, "Fabrica obrigatoria.");
        this.simulador = Objects.requireNonNull(simulador, "Simulador obrigatorio.");
        this.percentualTaxa = validarPercentual(percentualTaxa);
        this.entregas = entregas;
    }

    /** Calcula taxa/comissao uma vez sobre o total correspondente, com HALF_UP. */
    public static BigDecimal calcularPercentual(BigDecimal base, BigDecimal percentual) {
        if (base == null || base.signum() < 0) throw new IllegalArgumentException("Base invalida.");
        return base.multiply(validarPercentual(percentual)).movePointLeft(2)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Recusa confirma apenas pedido CRIADO e pagamento RECUSADO, sem baixar estoque. */
    public Resultado comprar(long idCliente, List<ItemCarrinho> itens, MetodoPagamento metodo)
            throws SQLException {
        return executar(idCliente, null, null, itens, metodo);
    }

    /** Verifica estoque e precos para a revisao, sem reservar nem gravar uma compra. */
    public Cotacao cotar(long idCliente, List<ItemCarrinho> itens) throws SQLException {
        validarId(idCliente);
        List<ItemCarrinho> entradas = validarCarrinho(itens);
        return TransacaoJdbc.executar(fabrica::abrir, c -> {
            exigirClienteAtivo(c, idCliente);
            var carrinho = carregarCarrinho(c, entradas);
            return new Cotacao(carrinho.produtos(), carrinho.taxa());
        });
    }

    /** Confirma a compra somente se os valores revisados ainda forem validos. */
    public Resultado comprarConfirmado(long idCliente, List<ItemCarrinho> itens, MetodoPagamento metodo,
            Cotacao cotacao) throws SQLException {
        return executar(idCliente, null, null, itens, metodo, Objects.requireNonNull(cotacao));
    }

    /**
     * Reenvia o carrinho: o schema nao guarda itens de pedidos recusados.
     * Recalcula precos/taxa atuais e exige os mesmos totais do pedido salvo.
     * Carrinho diferente com os mesmos totais e aceito explicitamente neste contrato;
     * nao ha promessa de preservar a composicao original nem historico de tentativas.
     */
    public Resultado tentarNovamente(long idCliente, long idPedido, String codigoRecusado,
            List<ItemCarrinho> itens, MetodoPagamento metodo) throws SQLException {
        validarId(idPedido);
        if (codigoRecusado == null || codigoRecusado.isBlank())
            throw new IllegalArgumentException("Codigo da recusa obrigatorio.");
        return executar(idCliente, idPedido, codigoRecusado, itens, metodo);
    }

    /** Historico paginado, do mais recente ao mais antigo, restrito ao cliente. */
    public List<PedidoMestreRegistro> listarHistorico(long idCliente, int limite, int deslocamento)
            throws SQLException {
        validarId(idCliente);
        if (limite < 1 || limite > 100 || deslocamento < 0)
            throw new IllegalArgumentException("Paginacao invalida.");
        return TransacaoJdbc.executar(fabrica::abrir, c -> {
            exigirClienteAtivo(c, idCliente);
            return new PedidoMestreDao(c).listarPorCliente(idCliente, limite, deslocamento);
        });
    }

    /** Le o detalhe completo de uma compra propria, sincronizado com seus fluxos de escrita. */
    public Optional<DetalheCompra> buscarCompra(long idCliente, long idPedido) throws SQLException {
        validarId(idCliente);
        validarId(idPedido);
        return TransacaoJdbc.executar(fabrica::abrir, c -> {
            exigirClienteAtivo(c, idCliente);
            var pedidos = new PedidoMestreDao(c);
            if (pedidos.buscarPorId(idPedido).filter(p -> p.idCliente() == idCliente).isEmpty())
                return Optional.empty();
            var pedido = pedidos.buscarPorIdParaAtualizacao(idPedido)
                    .filter(p -> p.idCliente() == idCliente);
            if (pedido.isEmpty()) return Optional.empty();
            return Optional.of(carregarDetalhe(c, pedido.orElseThrow()));
        });
    }

    /** Consulta administrativa inclui compras de clientes que ja foram desativados. */
    public Optional<DetalheCompra> buscarCompraComoAdministrador(long idAdmin, long idPedido) throws SQLException {
        validarId(idAdmin);
        validarId(idPedido);
        return TransacaoJdbc.executar(fabrica::abrir, c -> {
            var admin = new UsuarioDao(c).buscarPorId(idAdmin)
                    .filter(u -> u.perfil() == PerfilUsuario.ADMIN && Boolean.TRUE.equals(u.ativo()));
            if (admin.isEmpty()) throw new IllegalArgumentException("Selecione um administrador ativo.");
            var pedido = new PedidoMestreDao(c).buscarPorIdParaAtualizacao(idPedido);
            if (pedido.isEmpty()) return Optional.empty();
            return Optional.of(carregarDetalhe(c, pedido.orElseThrow()));
        });
    }

    private static DetalheCompra carregarDetalhe(Connection c, PedidoMestreRegistro pedido) throws SQLException {
        var partes = new ComandaLojistaDao(c).listarPorPedido(pedido.id());
        return new DetalheCompra(pedido, new PagamentoDao(c).buscarPorPedido(pedido.id()), detalharComandas(c, partes));
    }

    /**
     * Cancela a compra inteira antes do primeiro preparo. Ja cancelada retorna true
     * sem repetir estorno ou estoque. Compra alheia, ausente ou fora do prazo retorna false.
     */
    public boolean cancelarCompra(long idCliente, long idPedido) throws SQLException {
        validarId(idCliente);
        validarId(idPedido);
        return TransacaoJdbc.executar(fabrica::abrir, c -> {
            exigirClienteAtivo(c, idCliente);
            var pedidos = new PedidoMestreDao(c);
            var inicial = pedidos.buscarPorId(idPedido).filter(p -> p.idCliente() == idCliente);
            if (inicial.isEmpty()) return false;
            var comandas = new ComandaLojistaDao(c);
            var ordens = new OrdemEntregaDao(c);
            var entregadores = new EntregadorDao(c);

            // Aceite/coleta bloqueiam entregador antes do pedido. Mantemos essa ordem
            // tambem para atribuicoes antigas que ainda estejam ligadas a comanda PAGO.
            Map<Long, EntregadorRegistro> bloqueados = new TreeMap<>();
            if (inicial.orElseThrow().statusPedido() == StatusPedidoMestre.PAGO) {
                var vinculados = new TreeSet<Long>();
                for (var parte : comandas.listarPorPedido(idPedido)) {
                    for (var ordem : listarOrdens(ordens, parte.id())) {
                        if (ordem.idEntregador() != null) vinculados.add(ordem.idEntregador());
                    }
                }
                for (long id : vinculados) {
                    entregadores.buscarPorIdParaAtualizacao(id).ifPresent(e -> bloqueados.put(id, e));
                }
            }

            // Preparo e nova tentativa disputam a mesma linha. Validar novamente sob o bloqueio.
            var atual = pedidos.buscarPorIdParaAtualizacao(idPedido).filter(p -> p.idCliente() == idCliente);
            if (atual.isEmpty()) return false;
            var estado = atual.orElseThrow().statusPedido();
            if (estado == StatusPedidoMestre.CANCELADO) return true;
            if (estado != StatusPedidoMestre.PAGO && estado != StatusPedidoMestre.CRIADO) return false;
            var partes = comandas.listarPorPedido(idPedido);
            var pagamentos = new PagamentoDao(c);
            if (estado == StatusPedidoMestre.CRIADO) {
                var pagamento = pagamentos.buscarPorPedido(idPedido);
                if (!partes.isEmpty() || pagamento.filter(p -> p.statusPagamento() != StatusPagamento.RECUSADO).isPresent())
                    return false;
            } else {
                if (partes.isEmpty() || partes.stream().anyMatch(p -> p.statusComanda() != StatusComanda.PAGO)) return false;
                if (!cancelarCompraPaga(c, idPedido, partes, bloqueados)) return false;
            }
            exigirAlteracao(pedidos.atualizarStatus(idPedido, estado, StatusPedidoMestre.CANCELADO),
                    "Estado da compra mudou durante o cancelamento.");
            return true;
        });
    }

    /** Confere todas as partes antes de estornar e reverter os efeitos do checkout. */
    private boolean cancelarCompraPaga(Connection c, long idPedido, List<ComandaLojistaRegistro> partes,
            Map<Long, EntregadorRegistro> bloqueados) throws SQLException {
        var detalhes = detalharComandas(c, partes);
        // Primeiro confere todas as comandas e suas ordens; so depois executa estorno
        // e reposicao. Assim, uma condicao que retorna false nao deixa escrita parcial.
        Set<Long> vinculados = new TreeSet<>();
        List<Reposicao> reposicoes = new ArrayList<>();
        for (var detalhe : detalhes) {
            if (detalhe.itens().isEmpty()) throw new SQLException("Comanda paga sem itens para recompor estoque.");
            for (var ordem : detalhe.entregas()) {
                if (ordem.statusEntrega() != StatusOrdemEntrega.AGUARDANDO_COLETA
                        || ordem.dataHoraColeta() != null || ordem.dataHoraEntrega() != null) return false;
                if (ordem.idEntregador() != null) vinculados.add(ordem.idEntregador());
            }
            for (var item : detalhe.itens()) reposicoes.add(new Reposicao(detalhe.comanda().idLojista(), item));
        }
        // Uma atribuicao nova em dados antigos exige repetir a operacao com os bloqueios corretos.
        if (!bloqueados.keySet().containsAll(vinculados)) return false;
        exigirAlteracao(new PagamentoDao(c).registrarEstornoIntegral(idPedido), "Pagamento nao pode ser estornado.");
        var produtos = new ProdutoDao(c);
        // Mesma ordem de bloqueio dos produtos no checkout.
        reposicoes.sort(Comparator.comparingLong(r -> r.item().idProduto()));
        for (var reposicao : reposicoes) {
            var item = reposicao.item();
            exigirAlteracao(produtos.reporEstoque(item.idProduto(), reposicao.lojista(), item.quantidade()),
                    "Nao foi possivel recompor o estoque do produto " + item.idProduto() + ".");
        }
        var ordens = new OrdemEntregaDao(c);
        var comandas = new ComandaLojistaDao(c);
        for (var detalhe : detalhes) {
            for (var ordem : detalhe.entregas()) {
                exigirAlteracao(ordens.cancelarAntesDaColeta(ordem.id()), "Ordem mudou durante o cancelamento.");
            }
            exigirAlteracao(comandas.atualizarStatus(detalhe.comanda().id(), StatusComanda.PAGO, StatusComanda.CANCELADO),
                    "Comanda mudou durante o cancelamento.");
        }
        var entregadores = new EntregadorDao(c);
        for (long id : vinculados) {
            if (bloqueados.get(id).statusOperacao() == StatusOperacaoEntregador.EM_ROTA && !ordens.possuiOrdemAtiva(id)) {
                exigirAlteracao(entregadores.liberarSemOrdens(id), "Nao foi possivel liberar o entregador.");
            }
        }
        return true;
    }

    /** Carrega os registros de cada comanda sem truncar dados antigos com mais de uma pagina. */
    private static List<DetalheComanda> detalharComandas(Connection c, List<ComandaLojistaRegistro> comandas)
            throws SQLException {
        var itens = new ItemPedidoDao(c);
        var ordens = new OrdemEntregaDao(c);
        List<DetalheComanda> detalhes = new ArrayList<>();
        for (var comanda : comandas) {
            List<ItemPedidoRegistro> todos = new ArrayList<>();
            // A pagina tem no maximo 100 itens. Continua ate uma pagina menor para montar
            // o detalhe inteiro; uma primeira pagina cheia nao representa todos os itens.
            for (int inicio = 0; ; inicio += 100) {
                var pagina = itens.listarPorComanda(comanda.id(), 100, inicio);
                todos.addAll(pagina);
                if (pagina.size() < 100) break;
            }
            detalhes.add(new DetalheComanda(comanda, todos, listarOrdens(ordens, comanda.id())));
        }
        return detalhes;
    }

    /** Normalmente ha uma ordem por comanda. Registros antigos podem ter mais. */
    private static List<OrdemEntregaRegistro> listarOrdens(OrdemEntregaDao dao, long idComanda) throws SQLException {
        List<OrdemEntregaRegistro> ordens = new ArrayList<>();
        for (int inicio = 0; ; inicio += 100) {
            var pagina = dao.listarPorComanda(idComanda, 100, inicio);
            ordens.addAll(pagina);
            if (pagina.size() < 100) return ordens;
        }
    }

    /** Qualquer escrita condicional perdida reverte o cancelamento inteiro. */
    private static void exigirAlteracao(boolean alterado, String mensagem) throws SQLException {
        if (!alterado) throw new SQLException(mensagem);
    }

    /** Aplica a mesma regra de acesso na compra, nas consultas e no cancelamento. */
    private static void exigirClienteAtivo(Connection c, long idCliente) throws SQLException {
        var usuario = new UsuarioDao(c).buscarPorId(idCliente).orElseThrow(
                () -> new IllegalArgumentException("Cliente inexistente."));
        if (usuario.perfil() != PerfilUsuario.CLIENTE || !Boolean.TRUE.equals(usuario.ativo()))
            throw new IllegalArgumentException("Cliente deve estar ativo e possuir perfil CLIENTE.");
    }

    // Sobrecarga para compra sem cotacao anterior: delega ao fluxo unico abaixo.
    // pedidoExistente null cria compra; um ID exige validar a tentativa recusada salva.
    private Resultado executar(long cliente, Long pedidoExistente, String codigoRecusado,
            List<ItemCarrinho> itens, MetodoPagamento metodo) throws SQLException {
        return executar(cliente, pedidoExistente, codigoRecusado, itens, metodo, null);
    }

    private Resultado executar(long cliente, Long pedidoExistente, String codigoRecusado,
            List<ItemCarrinho> itens, MetodoPagamento metodo, Cotacao cotacao) throws SQLException {
        validarId(cliente);
        Objects.requireNonNull(metodo, "Metodo obrigatorio.");
        List<ItemCarrinho> entradas = validarCarrinho(itens);
        try (Connection c = fabrica.abrir()) {
            if (!c.getAutoCommit()) throw new SQLException("Use conexao nova sem transacao em andamento.");
            // Leituras comuns apos bloqueios devem enxergar a versao atual, sem snapshot antigo.
            c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            c.setAutoCommit(false);
            try {
                var pedidos = new PedidoMestreDao(c);
                var pagamentos = new PagamentoDao(c);
                PedidoMestreRegistro pedido = null;
                // Na nova tentativa, bloqueia primeiro o pedido existente. Confere dono, estado
                // e ausencia de comandas antes de reutiliza-lo, para nao confirmar duas vezes.
                if (pedidoExistente != null) {
                    pedido = pedidos.buscarPorIdParaAtualizacao(pedidoExistente).orElseThrow(
                            () -> new IllegalArgumentException("Pedido indisponivel para nova tentativa deste cliente."));
                    if (pedido.idCliente() != cliente || pedido.statusPedido() != StatusPedidoMestre.CRIADO)
                        throw new IllegalArgumentException("Pedido indisponivel para nova tentativa deste cliente.");
                    if (!new ComandaLojistaDao(c).listarPorPedido(pedidoExistente).isEmpty())
                        throw new IllegalArgumentException("Pedido ja possui comandas; nova tentativa bloqueada.");
                }
                // ID vem da camada autenticada. Consulta de perfil nao substitui autenticacao.
                exigirClienteAtivo(c, cliente);

                // Le produtos/lojistas atuais sob bloqueio e calcula os valores confiaveis.
                // A cotacao e uma revisao anterior, nao uma reserva de estoque ou preco.
                Carrinho carrinho = carregarCarrinho(c, entradas);
                if (cotacao != null && (cotacao.produtos().compareTo(carrinho.produtos()) != 0
                        || cotacao.taxa().compareTo(carrinho.taxa()) != 0)) {
                    throw new IllegalStateException("O total mudou. Revise os valores e confirme novamente.");
                }
                // Compra nova grava o pedido mestre; retry conserva seus totais originais.
                // Se os valores atuais diferirem, o cliente precisa iniciar outra compra.
                if (pedido == null) {
                    pedido = pedidos.inserir(PedidoMestreRegistro.novoPedido(
                            cliente, carrinho.produtos(), carrinho.taxa()));
                } else if (pedido.valorProdutos().compareTo(carrinho.produtos()) != 0
                        || pedido.valorFretes().compareTo(carrinho.taxa()) != 0) {
                    throw new IllegalArgumentException("Total mudou: inicie uma nova compra com os valores atuais.");
                }
                // Cada pedido possui uma linha de pagamento. Nova tentativa reutiliza a linha
                // recusada e troca sua referencia; o codigo antigo nao pode concluir a nova tentativa.
                PagamentoRegistro pagamento;
                if (pedidoExistente == null) {
                    pagamento = pagamentos.inserir(PagamentoRegistro.novoPagamento(
                            pedido.id(), metodo, pedido.valorTotal()));
                } else {
                    var anterior = pagamentos.buscarPorPedido(pedido.id()).orElseThrow(
                            () -> new IllegalArgumentException("Pagamento anterior ausente."));
                    pagamento = pagamentos.prepararNovaTentativa(anterior.id(), codigoRecusado, metodo)
                            .orElseThrow(() -> new IllegalArgumentException("Tentativa desatualizada ou nao recusada."));
                }
                // Simulacao local dentro da transacao; integracao financeira real exige outro desenho.
                boolean autorizado = simulador.autorizar(pagamento);
                StatusPagamento status = autorizado ? StatusPagamento.AUTORIZADO : StatusPagamento.RECUSADO;
                if (!pagamentos.registrarResultado(pagamento.id(), pagamento.codigoAutorizacao(),
                        status, "SIM-" + status.name() + "-" + UUID.randomUUID()))
                    throw new SQLException("Pagamento mudou durante o processamento.");
                List<ComandaLojistaRegistro> comandas = new ArrayList<>();
                // Somente autorizacao cria as partes comerciais e baixa estoque. A recusa
                // ainda e confirmada no banco como resultado da operacao: nao e erro JDBC.
                if (autorizado) {
                    // Produtos permanecem bloqueados desde a leitura ate commit/rollback.
                    var produtos = new ProdutoDao(c);
                    for (Linha linha : carrinho.linhas()) {
                        if (!produtos.baixarEstoque(linha.produto().id(), linha.produto().idLojista(),
                                linha.entrada().quantidade())) throw new SQLException("Estoque indisponivel.");
                    }
                    var daoComandas = new ComandaLojistaDao(c);
                    var daoItens = new ItemPedidoDao(c);
                    // Uma comanda por lojista: aplica a comissao daquele cadastro sobre o subtotal
                    // da loja. Os itens guardam o preco aplicado agora para preservar o historico.
                    for (var subtotal : carrinho.subtotais().entrySet()) {
                        long lojista = subtotal.getKey();
                        BigDecimal comissao = calcularPercentual(subtotal.getValue(),
                                carrinho.lojistas().get(lojista).taxaComissaoPct());
                        var comanda = daoComandas.inserir(ComandaLojistaRegistro.novaComanda(
                                pedido.id(), lojista, subtotal.getValue(), comissao));
                        comandas.add(comanda);
                        for (Linha linha : carrinho.linhas()) {
                            if (linha.produto().idLojista() == lojista) {
                                daoItens.inserir(ItemPedidoRegistro.novoItem(comanda.id(),
                                        linha.produto().id(), linha.entrada().quantidade(),
                                        linha.produto().precoUnitario(), linha.entrada().observacao()));
                            }
                        }
                    }
                    if (!pedidos.atualizarStatus(pedido.id(), StatusPedidoMestre.CRIADO, StatusPedidoMestre.PAGO))
                        throw new SQLException("Pedido mudou durante a confirmacao.");
                    // A integracao logistica recebe a mesma conexao, sem commit proprio.
                    // Qualquer falha ao criar ordens reverte tambem pagamento, estoque, itens e comandas.
                    if (entregas != null) entregas.criarOrdens(c,
                            pedidos.buscarPorId(pedido.id()).orElseThrow(), comandas);
                }
                // Rele os estados efetivamente gravados antes de confirmar. O retorno so sai
                // apos commit; o chamador recebe registros, nao uma conexao ainda aberta.
                Resultado resultado = new Resultado(pedidos.buscarPorId(pedido.id()).orElseThrow(),
                        pagamentos.buscarPorId(pagamento.id()).orElseThrow(), comandas);
                c.commit();
                return resultado;
            } catch (SQLException | RuntimeException erro) {
                try { c.rollback(); } catch (SQLException falha) { erro.addSuppressed(falha); }
                throw erro;
            }
        }
    }

    // Monta os dados de checkout a partir do banco. A entrada ja esta ordenada
    // por ID de produto; essa ordem e compartilhada com a reposicao no cancelamento.
    private Carrinho carregarCarrinho(Connection c, List<ItemCarrinho> entradas) throws SQLException {
        var produtos = new ProdutoDao(c);
        List<Linha> linhas = new ArrayList<>();
        Map<Long, BigDecimal> subtotais = new TreeMap<>();
        // Ordem crescente de IDs em todas as compras reduz ciclos de bloqueio.
        for (ItemCarrinho entrada : entradas) {
            bloquear(c, "produto", "id_produto", entrada.idProduto());
            var produto = produtos.buscarPorId(entrada.idProduto()).orElseThrow();
            if (!Boolean.TRUE.equals(produto.ativo()) || produto.estoqueDisponivel() < entrada.quantidade())
                throw new IllegalArgumentException("Produto inativo ou estoque insuficiente.");
            Linha linha = new Linha(entrada, produto);
            linhas.add(linha);
            // merge cria o subtotal do lojista na primeira linha e soma nas seguintes.
            // TreeMap mantem os lojistas em ordem crescente para a etapa de bloqueio abaixo.
            subtotais.merge(produto.idLojista(), linha.subtotal(), BigDecimal::add);
        }
        Map<Long, LojistaRegistro> lojistas = new TreeMap<>();
        for (long id : subtotais.keySet()) {
            bloquear(c, "lojista", "id_lojista", id);
            var usuario = new UsuarioDao(c).buscarPorId(id).orElseThrow(
                    () -> new IllegalArgumentException("Usuario do lojista inexistente."));
            if (usuario.perfil() != PerfilUsuario.LOJISTA || !Boolean.TRUE.equals(usuario.ativo()))
                throw new IllegalArgumentException("Produtos de lojista inativo nao podem ser comprados.");
            lojistas.put(id, new LojistaDao(c).buscarPorId(id).orElseThrow());
        }
        BigDecimal total = subtotais.values().stream().reduce(new BigDecimal("0.00"), BigDecimal::add);
        BigDecimal taxa = calcularPercentual(total, percentualTaxa);
        // Valida DECIMAL(10,2) antes de gravar ou chamar o simulador.
        PedidoMestreRegistro.novoPedido(1, total, taxa);
        return new Carrinho(linhas, lojistas, subtotais, total, taxa);
    }

    /** Tabela/coluna sao constantes internas; valores usam parametros JDBC. */
    private static void bloquear(Connection c, String tabela, String coluna, long id) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(
                "SELECT " + coluna + " FROM " + tabela + " WHERE " + coluna + "=? FOR UPDATE")) {
            s.setLong(1, id);
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) throw new IllegalArgumentException("Registro inexistente: " + tabela);
            }
        }
    }

    // Copia e ordena sem modificar a lista do chamador. Produtos repetidos sao
    // recusados: o carrinho deve reuni-los em uma quantidade antes do checkout.
    private static List<ItemCarrinho> validarCarrinho(List<ItemCarrinho> itens) {
        if (itens == null || itens.isEmpty() || itens.size() > 100)
            throw new IllegalArgumentException("Carrinho deve conter de 1 a 100 produtos distintos.");
        List<ItemCarrinho> copia = new ArrayList<>(List.copyOf(itens));
        copia.sort(Comparator.comparingLong(ItemCarrinho::idProduto));
        // IDs validos sao positivos. Apos ordenar, repeticoes ficam vizinhas,
        // permitindo identifica-las em uma unica passagem pela lista.
        long anterior = 0;
        for (ItemCarrinho item : copia) {
            if (item.idProduto() == anterior) throw new IllegalArgumentException("Agrupe quantidades do mesmo produto.");
            anterior = item.idProduto();
        }
        return copia;
    }

    // Percentual usa unidades de 0 a 100 (7 significa 7%). UNNECESSARY permite
    // normalizar zeros extras, mas recusa casas significativas alem de duas.
    private static BigDecimal validarPercentual(BigDecimal percentual) {
        if (percentual == null || percentual.signum() < 0 || percentual.compareTo(new BigDecimal("100")) > 0)
            throw new IllegalArgumentException("Percentual explicito entre 0 e 100 obrigatorio.");
        try { return percentual.setScale(2, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException erro) { throw new IllegalArgumentException("Percentual exige ate duas casas.", erro); }
    }

    private static void validarId(long id) {
        if (id < 1 || id > Integer.MAX_VALUE) throw new IllegalArgumentException("ID fora da faixa INT positiva.");
    }
}
