import com.nexusdispatch.model.*;
import com.nexusdispatch.enums.StatusPedido;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public class PedidoCheck {
    private static int checks;
    private static void equal(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) throw new AssertionError(expected + " != " + actual);
        checks++;
    }
    private static void reject(Class<? extends RuntimeException> type, Runnable action) {
        try { action.run(); }
        catch (RuntimeException error) { if (!type.isInstance(error)) throw error; checks++; return; }
        throw new AssertionError("Operacao invalida aceita");
    }
    private static Loja loja(String nome) {
        return new Loja(nome, "01234567890123",
                new Endereco(new Bairro("Centro", "Cidade", true), "01234567", null, "Rua", "1"),
                new Lojista("Ana", "ana@example.com", null, "hash-ficticio"));
    }
    public static void main(String[] args) {
        Loja loja = loja("Mercado"), outra = loja("Padaria");
        Produto produto = new Produto(loja, "Arroz", "Pacote");
        OfertaProduto oferta = new OfertaProduto(produto, new BigDecimal("12.50"), 5);
        ItemPedido arroz = new ItemPedido(oferta, 5);
        ItemPedido feijao = new ItemPedido(new OfertaProduto(new Produto(loja, "Feijao", null),
                new BigDecimal("2.25"), 2), 2);
        reject(IllegalArgumentException.class, () -> new Pedido(null, List.of(arroz)));
        reject(IllegalArgumentException.class, () -> new Pedido(loja, null));
        reject(IllegalArgumentException.class, () -> new Pedido(loja, List.of()));
        reject(IllegalArgumentException.class, () -> new Pedido(loja, Arrays.asList(arroz, null)));
        reject(IllegalArgumentException.class, () -> new Pedido(outra, List.of(arroz)));
        reject(IllegalArgumentException.class, () -> new Pedido(loja, List.of(arroz, arroz)));
        reject(IllegalArgumentException.class, () -> new Pedido(loja, List.of(arroz, new ItemPedido(oferta, 1))));
        List<ItemPedido> original = new ArrayList<>(List.of(arroz, feijao));
        LocalDateTime antes = LocalDateTime.now();
        Pedido pedido = new Pedido(loja, original);
        equal(false, pedido.getDataHoraCriacao().isBefore(antes));
        equal(false, pedido.getDataHoraCriacao().isAfter(LocalDateTime.now()));
        equal(null, pedido.getId());
        equal(loja, pedido.getLoja());
        original.clear();
        equal(2, pedido.getItens().size());
        reject(UnsupportedOperationException.class, () -> pedido.getItens().clear());
        equal(new BigDecimal("67.00"), pedido.getSubtotal());
        oferta.setPreco(BigDecimal.TEN);
        produto.setNome("Outro arroz");
        equal(new BigDecimal("67.00"), pedido.getSubtotal());
        equal("Arroz", pedido.getItens().get(0).getNomeProduto());
        equal(5, oferta.getEstoque());
        // Mesma oferta pode aparecer em outro pedido. A transacao verificara estoque.
        equal(1, new Pedido(loja, List.of(new ItemPedido(oferta, 1))).getItens().size());
        // Estados independentes entre pedidos de lojas diferentes.
        Pedido outro = new Pedido(outra, List.of(new ItemPedido(new OfertaProduto(
                new Produto(outra, "Pao", null), BigDecimal.ONE, 1), 1)));
        equal(StatusPedido.CONFIRMADO, pedido.getStatus());
        reject(IllegalStateException.class, pedido::iniciarEntrega);
        reject(IllegalStateException.class, pedido::concluirEntrega);
        equal(StatusPedido.CONFIRMADO, pedido.getStatus());
        pedido.iniciarPreparacao();
        equal(StatusPedido.EM_PREPARACAO, pedido.getStatus());
        reject(IllegalStateException.class, pedido::iniciarPreparacao);
        reject(IllegalStateException.class, pedido::concluirEntrega);
        equal(StatusPedido.EM_PREPARACAO, pedido.getStatus());
        pedido.iniciarEntrega();
        equal(StatusPedido.EM_ROTA, pedido.getStatus());
        reject(IllegalStateException.class, pedido::iniciarPreparacao);
        reject(IllegalStateException.class, pedido::iniciarEntrega);
        equal(StatusPedido.EM_ROTA, pedido.getStatus());
        pedido.concluirEntrega();
        reject(IllegalStateException.class, pedido::iniciarPreparacao);
        reject(IllegalStateException.class, pedido::iniciarEntrega);
        reject(IllegalStateException.class, pedido::concluirEntrega);
        equal(StatusPedido.ENTREGUE, pedido.getStatus());
        equal(StatusPedido.CONFIRMADO, outro.getStatus());
        System.out.println("Pedido: " + checks + " verificacoes passaram.");
    }
}
