import com.nexusdispatch.model.*;
import java.math.BigDecimal;
import java.util.Objects;

public class ProdutoCheck {
    private static int checks;
    private static Loja loja(String nome, String cnpj) {
        return new Loja(nome, cnpj,
                new Endereco(new Bairro("Centro", "Cidade", true), "01234567", null, "Rua", "1"),
                new Lojista("Ana", "ana@example.com", null, "hash-ficticio"));
    }
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
        Loja a = loja("Loja A", "01234567890123");
        Loja b = loja("Loja B", "12345678901234");
        Produto p = new Produto(a, "  Arroz 1 kg  ", "  Tipo 1  ");
        equal(null, p.getId());
        equal(a, p.getLoja());
        equal("Arroz 1 kg", p.getNome());
        equal("Tipo 1", p.getDescricao());
        reject(() -> new Produto(null, "Arroz", null));
        for (String n : new String[] {null, "", " \t\n", "12345", "---", "🍚", "A".repeat(121)}) {
            reject(() -> new Produto(a, n, null));
            reject(() -> p.setNome(n));
            equal("Arroz 1 kg", p.getNome());
        }
        for (String n : new String[] {"Água 500 ml", "  Arroz  1 kg  ", "A".repeat(120),
                "A" + "🍚".repeat(119), "\uD801\uDC00"}) {
            equal(n.strip(), new Produto(a, n, null).getNome());
            p.setNome(n);
            equal(n.strip(), p.getNome());
        }
        String longoUnicode = "A" + "🍚".repeat(120);
        reject(() -> new Produto(a, longoUnicode, null));
        String anterior = p.getNome();
        reject(() -> p.setNome(longoUnicode));
        equal(anterior, p.getNome());
        for (String d : new String[] {null, "", " \t\n"}) {
            equal(null, new Produto(a, "Arroz", d).getDescricao());
            p.setDescricao(d);
            equal(null, p.getDescricao());
        }
        for (String d : new String[] {"  100 x 20  ", "D".repeat(1000), "🍚".repeat(1000)}) {
            equal(d.strip(), new Produto(a, "Arroz", d).getDescricao());
            p.setDescricao(d);
            equal(d.strip(), p.getDescricao());
        }
        String descricaoAnterior = p.getDescricao();
        for (String d : new String[] {"D".repeat(1001), "🍚".repeat(1001)}) {
            reject(() -> new Produto(a, "Arroz", d));
            reject(() -> p.setDescricao(d));
            equal(descricaoAnterior, p.getDescricao());
        }
        // Produtos de lojas diferentes sao objetos distintos, mesmo com nome igual.
        Produto pa = new Produto(a, "Arroz", "Original");
        Produto pb = new Produto(b, "Arroz", "Original");
        OfertaProduto oa = new OfertaProduto(pa, BigDecimal.ONE, 10);
        OfertaProduto ob = new OfertaProduto(pb, BigDecimal.TEN, 20);
        pa.setNome("Arroz integral");
        pa.setDescricao("Alterada");
        equal("Arroz", pb.getNome());
        equal("Original", pb.getDescricao());
        equal(a, oa.getLoja());
        equal(b, ob.getLoja());
        equal("Arroz integral", oa.getProduto().getNome());
        equal(BigDecimal.TEN, ob.getPreco());
        equal(20, ob.getEstoque());
        System.out.println("Produto: " + checks + " verificacoes passaram.");
    }
}
