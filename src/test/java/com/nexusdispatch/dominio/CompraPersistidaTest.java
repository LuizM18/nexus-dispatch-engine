package com.nexusdispatch.dominio;

import org.junit.jupiter.api.Test;

import com.nexusdispatch.enums.*;
import com.nexusdispatch.model.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Grafo persistido: identidade, historico, listas completas e estados SQL. */
public class CompraPersistidaTest {
    static int checks;
    // Constroi o decimal a partir de texto, sem aproximacao binaria de double.
    // Assim, o valor esperado pode ser comparado com o contrato monetario do modelo.
    static BigDecimal b(String v) { return new BigDecimal(v); }
    // Transforma uma condicao falsa em AssertionError para interromper o teste.
    // A mensagem/contador identifica a verificacao; imprimir sucesso nao substitui a assercao.
    static void ok(boolean v) { if (!v) throw new AssertionError("Grafo persistido incorreto"); checks++; }
    // Executa a acao e confere a excecao esperada. Excecoes de outro tipo
    // nao devem ser confundidas com uma rejeicao correta da regra testada.
    static void rejeitar(Runnable acao, Class<? extends RuntimeException> tipo) {
        try { acao.run(); } catch (RuntimeException e) { if (!tipo.isInstance(e)) throw e; checks++; return; }
        throw new AssertionError("Operacao indevida aceita");
    }
    static UsuarioRegistro usuario(long id, PerfilUsuario perfil) {
        return new UsuarioRegistro(id, "Ana", "DOC", "ana@example.test", perfil, null, true);
    }
    // Monta registros sinteticos como se tivessem sido lidos do banco, sem executar SQL.
    // Confere identidade, totais historicos, listas completas e bloqueio de mutacoes
    // locais. Percorrer os enums verifica preservacao dos estados, nao autoriza transicoes.
    @Test
    void verificar() {
        var cliente = new Consumidor(usuario(1, PerfilUsuario.CLIENTE));
        var lojista = new Lojista(usuario(2, PerfilUsuario.LOJISTA), new LojistaRegistro(2, "Loja", b("10"), "Centro", "Rua"));
        var loja = new Loja(lojista);
        // Produto hoje custa 99 e nao tem saldo; item historico continua custando 10.
        var produto = new Produto(loja, new ProdutoRegistro(3L, 2, "Nome atual", "OUTROS", "Descricao atual", b("99"), b("1"), 0, false));
        var oferta = new OfertaProduto(produto);
        var dadosItem = new ItemPedidoRegistro(4L, 5, 3, 2, b("10"), "Sem sal");
        var item = new ItemPedido(oferta, dadosItem);
        ok(item.toRegistro().equals(dadosItem));
        ok(item.getPrecoUnitario().equals(b("10.00")) && item.getSubtotal().equals(b("20.00")));
        ok(item.getNomeProduto() == null && item.getDescricaoProduto() == null && !item.possuiTextosHistoricos());
        ok(item.getNomeProdutoAtual().equals("Nome atual") && item.getDescricaoProdutoAtual().equals("Descricao atual"));
        ok(item.getIdComanda().equals(5L) && item.getObservacao().equals("Sem sal"));
        rejeitar(() -> new ItemPedido(oferta, 1), IllegalArgumentException.class);
        rejeitar(() -> ItemPedido.reconstituir(4L, oferta, 1, "Inventado", null, b("1")), IllegalArgumentException.class);
        rejeitar(() -> new ItemPedido(oferta, new ItemPedidoRegistro(4L, 5, 99, 1, b("1"), null)), IllegalArgumentException.class);
        rejeitar(() -> new ItemPedido(oferta, ItemPedidoRegistro.novoItem(5, 3, 1, b("1"), null)), IllegalArgumentException.class);
        rejeitar(() -> new Pedido(loja, List.of(item)), IllegalArgumentException.class);
        var produtoLocal = new Produto(loja, "Local", null);
        var itemA = new ItemPedido(new OfertaProduto(produtoLocal, b("1"), 1), 1);
        var itemB = new ItemPedido(new OfertaProduto(produtoLocal, b("1"), 1), 1);
        rejeitar(() -> new Pedido(loja, List.of(itemA, itemB)), IllegalArgumentException.class);
        LocalDateTime data = LocalDateTime.of(2026, 11, 3, 10, 0);
        for (StatusComanda estado : StatusComanda.values()) {
            var comanda = new ComandaLojistaRegistro(5L, 6, 2, b("20"), b("2"), b("18"), estado, data);
            var pedido = new Pedido(loja, comanda, List.of(item));
            ok(pedido.toRegistro().equals(comanda) && pedido.getStatusComanda() == estado);
            ok(pedido.getDataHoraCriacao() == null && pedido.getDataHoraStatus().equals(data));
            ok(pedido.getIdPedidoMestre() == 6 && pedido.getSubtotal().equals(b("20.00")));
            ok(pedido.getTaxaVendaRetida().equals(b("2.00")) && pedido.getRepasseLojista().equals(b("18.00")));
            rejeitar(pedido::getStatus, IllegalStateException.class);
            rejeitar(pedido::iniciarPreparacao, IllegalStateException.class);
            rejeitar(pedido::iniciarEntrega, IllegalStateException.class);
            rejeitar(pedido::concluirEntrega, IllegalStateException.class);
        }
        var comanda = new ComandaLojistaRegistro(5L, 6, 2, b("20"), b("2"), b("18"), StatusComanda.PAGO, null);
        var pedido = new Pedido(loja, comanda, List.of(item));
        rejeitar(() -> new Pedido(loja, comanda, List.of()), IllegalArgumentException.class);
        rejeitar(() -> new Pedido(loja, comanda, List.of(item, item)), IllegalArgumentException.class);
        rejeitar(() -> new Pedido(loja, comanda, List.of(new ItemPedido(oferta, new ItemPedidoRegistro(9L, 99, 3, 2, b("10"), null)))), IllegalArgumentException.class);
        rejeitar(() -> new Pedido(loja, comanda, List.of(new ItemPedido(oferta, new ItemPedidoRegistro(9L, 5, 3, 1, b("10"), null)))), IllegalArgumentException.class);
        rejeitar(() -> pedido.getItens().clear(), UnsupportedOperationException.class);
        for (StatusPedidoMestre estado : StatusPedidoMestre.values()) {
            var mestre = new PedidoMestreRegistro(6L, 1, data, b("20"), b("21"), estado);
            var compra = new Compra(cliente, mestre, List.of(pedido));
            ok(compra.toRegistro().equals(mestre) && compra.getStatusPedidoMestre() == estado);
            ok(compra.getDataHoraCriacao().equals(data) && compra.getEnderecoEntrega() == null);
            ok(compra.getSubtotal().equals(b("20.00")) && compra.getValorTotal().equals(b("21.00")) && compra.getValorTaxaCompra().equals(b("1.00")));
        }
        var mestre = new PedidoMestreRegistro(6L, 1, null, b("20"), b("21"), StatusPedidoMestre.PAGO);
        rejeitar(() -> new Compra(cliente, mestre, List.of()), IllegalArgumentException.class);
        rejeitar(() -> new Compra(cliente, mestre, List.of(pedido, pedido)), IllegalArgumentException.class);
        rejeitar(() -> new Compra(new Consumidor(usuario(9, PerfilUsuario.CLIENTE)), mestre, List.of(pedido)), IllegalArgumentException.class);
        var recusado = new Compra(cliente, new PedidoMestreRegistro(6L, 1, null, b("20"), b("21"), StatusPedidoMestre.CRIADO), List.of());
        ok(recusado.getPedidos().isEmpty() && recusado.getSubtotal().equals(b("20.00")) && recusado.getDataHoraCriacao() == null);
        var entregador = new Entregador(usuario(7, PerfilUsuario.ENTREGADOR), new EntregadorRegistro(7, null, TipoVeiculo.BICICLETA, StatusOperacaoEntregador.EM_ROTA, "Centro"));
        for (StatusOrdemEntrega estado : StatusOrdemEntrega.values()) {
            for (boolean vinculado : new boolean[]{false, true}) {
                var dados = new OrdemEntregaRegistro(8L, 5, vinculado ? 7L : null, "FIXO", b("2"), b("1"), b("0"), estado, data, data);
                var ordem = new OrdemEntrega(pedido, dados, vinculado ? entregador : null);
                ok(ordem.toRegistro().equals(dados) && ordem.getStatusEntrega() == estado);
                ok(ordem.getDataHoraCriacao() == null && ordem.getDataHoraAceite() == null && ordem.getEnderecoEntrega() == null);
                ok(ordem.getDataHoraRetirada().equals(data) && ordem.getDataHoraConclusao().equals(data));
                ok(ordem.isEmAndamento() == (estado == StatusOrdemEntrega.EM_TRANSITO || (estado == StatusOrdemEntrega.AGUARDANDO_COLETA && vinculado)));
                ok(ordem.getDistanciaKm().equals(b("2.00")) && ordem.getValorRepasseEntregador().equals(b("1.00")) && ordem.getTaxaFreteRetida().equals(b("0.00")));
                rejeitar(ordem::getStatus, IllegalStateException.class);
                rejeitar(() -> ordem.aceitar(entregador), IllegalStateException.class);
                rejeitar(ordem::registrarRetirada, IllegalStateException.class);
                rejeitar(ordem::concluirEntrega, IllegalStateException.class);
            }
        }
        var ordemSemVinculo = new OrdemEntregaRegistro(8L, 5, null, "FIXO", b("2"), b("1"), b("0"), StatusOrdemEntrega.AGUARDANDO_COLETA, null, null);
        rejeitar(() -> new OrdemEntrega(pedido, ordemSemVinculo, entregador), IllegalArgumentException.class);
        rejeitar(() -> new OrdemEntrega(pedido, new OrdemEntregaRegistro(8L, 99, null, "FIXO", b("2"), b("1"), b("0"), StatusOrdemEntrega.CANCELADO, null, null), null), IllegalArgumentException.class);
        System.out.println("Compra persistida: " + checks + " verificacoes aprovadas.");
    }
}
