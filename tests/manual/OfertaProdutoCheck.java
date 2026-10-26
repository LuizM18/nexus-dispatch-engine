import com.nexusdispatch.model.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

public class OfertaProdutoCheck {
    private static int checks;

    private static final Loja LOJA = new Loja("Loja", "01234567890123",
            new Endereco(new Bairro("Centro", "Cidade", true), "01234567", null, "Rua", "1"),
            new Lojista("Ana", "ana@example.com", null, "hash-ficticio"));

    private static final Produto PRODUTO = new Produto(LOJA, "Arroz", null);

    private static void equal(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) throw new AssertionError("Valor inesperado");
        checks++;
    }

    private static void reject(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError("Entrada invalida aceita");
    }

    public static void main(String[] args) {
        reject(() -> new OfertaProduto(null, BigDecimal.ONE, 0));

        reject(() -> new OfertaProduto(PRODUTO, null, 0));
        reject(() -> new OfertaProduto(PRODUTO, new BigDecimal("-0.01"), 0));
        reject(() -> new OfertaProduto(PRODUTO, BigDecimal.ONE, -1));
        OfertaProduto o = new OfertaProduto(PRODUTO, BigDecimal.ZERO, 0);
        equal(null, o.getId());
        equal(PRODUTO, o.getProduto());
        equal(LOJA, o.getLoja());
        equal(BigDecimal.ZERO, o.getPreco());
        equal(0, o.getEstoque());
        equal(0, o.getEntradasEstoque().size());
        equal(0, o.getQuantidadeDisponivelParaVenda());
        var snapshot = o.getEntradasEstoque();
        LocalDateTime antes = LocalDateTime.now();
        o.adicionarEstoque(10);
        LocalDateTime depois = LocalDateTime.now();
        equal(10, o.getEstoque());
        equal(10, o.getQuantidadeDisponivelParaVenda());
        equal(0, snapshot.size());
        var primeira = o.getEntradasEstoque().get(0);
        equal(10, primeira.quantidade());
        equal(0, primeira.estoqueAnterior());
        equal(10, primeira.estoqueApos());
        equal(false, primeira.dataHora().isBefore(antes));
        equal(false, primeira.dataHora().isAfter(depois));
        o.adicionarEstoque(5);
        equal(15, o.getEstoque());
        equal(2, o.getEntradasEstoque().size());
        equal(10, o.getEntradasEstoque().get(1).estoqueAnterior());
        equal(15, o.getEntradasEstoque().get(1).estoqueApos());
        for (int n : new int[] {0, -1, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            reject(() -> o.adicionarEstoque(n));
            equal(15, o.getEstoque());
            equal(2, o.getEntradasEstoque().size());
        }
        try { o.getEntradasEstoque().clear(); throw new AssertionError("Historico modificavel"); }
        catch (UnsupportedOperationException expected) { checks++; }
        equal(2, o.getEntradasEstoque().size());
        o.setPreco(new BigDecimal("12.50"));
        reject(() -> o.setPreco(null));
        reject(() -> o.setPreco(new BigDecimal("-1")));
        equal(new BigDecimal("12.50"), o.getPreco());
        o.setPreco(BigDecimal.ZERO);
        equal(BigDecimal.ZERO, o.getPreco());
        for (int n : new int[] {0, 1, 2, 3, 4, 13}) {
            var oferta = new OfertaProduto(PRODUTO, BigDecimal.ONE, n);
            equal(n, oferta.getEstoque());
            equal(n, oferta.getQuantidadeDisponivelParaVenda());
            equal(n == 0 ? 0 : 1, oferta.getEntradasEstoque().size());
            if (n > 0) equal(n, oferta.getEntradasEstoque().get(0).quantidade());
        }
        var limite = new OfertaProduto(PRODUTO, BigDecimal.ONE, Integer.MAX_VALUE);
        reject(() -> limite.adicionarEstoque(1));
        equal(Integer.MAX_VALUE, limite.getEstoque());
        equal(1, limite.getEntradasEstoque().size());
        System.out.println("OfertaProduto: " + checks + " verificacoes passaram.");
    }
}
