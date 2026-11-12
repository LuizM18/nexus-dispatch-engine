package com.nexusdispatch.dominio;

import org.junit.jupiter.api.Test;

import com.nexusdispatch.model.*;
import java.math.BigDecimal;
import java.util.Objects;

// Teste JUnit de dominio, executado em memoria com dados ficticios.
// O metodo anotado com @Test e descoberto pelo Maven/JUnit, sem metodo main.
public class ItemPedidoTest {
    private static int checks;
    // Compara valores com Objects.equals, que aceita null. Para BigDecimal,
    // equals tambem considera a escala; os valores esperados expressam esse contrato.
    private static void equal(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) throw new AssertionError(expected + " != " + actual);
        checks++;
    }
    // Recebe uma acao adiada (lambda/referencia de metodo) e exige a excecao
    // prevista. Retorno normal significa falha do teste, pois a entrada deveria ser recusada.
    private static void reject(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError("Entrada invalida aceita");
    }
    // Confere que o item local captura preco e textos no momento da compra.
    // Mudancas posteriores no catalogo nao podem alterar esse historico; validar
    // quantidade nao significa baixar estoque nem processar pagamento.
    @Test
    void verificar() {
        Loja loja = new Loja("Loja", "01234567890123",
                new Endereco(new Bairro("Centro", "Cidade", true), "01234567", null, "Rua", "1"),
                new Lojista("Ana", "ana@example.com", null, "hash-ficticio"));
        Produto produto = new Produto(loja, "Arroz", "Pacote");
        OfertaProduto oferta = new OfertaProduto(produto, new BigDecimal("12.50"), 10);
        reject(() -> new ItemPedido(null, 1));
        for (int n : new int[] {0, -1, Integer.MIN_VALUE, 11, Integer.MAX_VALUE}) {
            reject(() -> new ItemPedido(oferta, n));
        }
        ItemPedido item = new ItemPedido(oferta, 10);
        equal(null, item.getId());
        equal(oferta, item.getOferta());
        equal(produto, item.getProduto());
        equal(10, item.getQuantidade());
        equal(new BigDecimal("125.00"), item.getSubtotal());
        equal(10, oferta.getEstoque()); // Criacao nao efetua baixa.
        equal(1, oferta.getEntradasEstoque().size());
        produto.setNome("Feijao");
        produto.setDescricao("Outro pacote");
        oferta.setPreco(new BigDecimal("20.00"));
        equal("Arroz", item.getNomeProduto());
        equal("Pacote", item.getDescricaoProduto());
        equal(new BigDecimal("12.50"), item.getPrecoUnitario());
        equal(new BigDecimal("125.00"), item.getSubtotal());
        for (int saldo : new int[] {0, 1, 2, 3, 4, Integer.MAX_VALUE}) {
            OfertaProduto o = new OfertaProduto(produto, BigDecimal.ONE, saldo);
            equal(saldo, o.getQuantidadeDisponivelParaVenda());
            if (saldo > 0) equal(saldo, new ItemPedido(o, saldo).getQuantidade());
            else reject(() -> new ItemPedido(o, 1));
        }
        OfertaProduto vazia = new OfertaProduto(produto, BigDecimal.TEN, 0);
        ItemPedido salvo = ItemPedido.reconstituir(1L, vazia, 20, "Nome antigo", null, BigDecimal.ZERO);
        equal(1L, salvo.getId());
        equal("Nome antigo", salvo.getNomeProduto());
        equal(null, salvo.getDescricaoProduto());
        equal(BigDecimal.ZERO, salvo.getSubtotal());
        equal(0, vazia.getEstoque());
        for (Long id : new Long[] {null, 0L, -1L}) {
            reject(() -> ItemPedido.reconstituir(id, vazia, 1, "Nome", null, BigDecimal.ONE));
        }
        reject(() -> ItemPedido.reconstituir(1L, null, 1, "Nome", null, BigDecimal.ONE));
        reject(() -> ItemPedido.reconstituir(1L, vazia, 0, "Nome", null, BigDecimal.ONE));
        for (String nome : new String[] {null, "", "   "}) {
            reject(() -> ItemPedido.reconstituir(1L, vazia, 1, nome, null, BigDecimal.ONE));
        }
        reject(() -> ItemPedido.reconstituir(1L, vazia, 1, "Nome", null, null));
        reject(() -> ItemPedido.reconstituir(1L, vazia, 1, "Nome", null, new BigDecimal("-1")));
        produto.setDescricao(null);
        equal(null, new ItemPedido(oferta, 1).getDescricaoProduto());
        System.out.println("ItemPedido: " + checks + " verificacoes passaram.");
    }
}
