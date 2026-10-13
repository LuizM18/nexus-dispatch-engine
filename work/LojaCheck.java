import com.nexusdispatch.model.*;
import java.util.Objects;

// Testes locais da entidade. Nao simulam persistencia nem garantem CNPJ unico.
public class LojaCheck {
    private static int checks;
    private static final Endereco ENDERECO = new Endereco(
            new Bairro("Centro", "Cidade", true), "01234567", null, "Rua Um", "10");
    private static final Lojista LOJISTA = new Lojista(
            "Ana", "ana@example.com", null, "hash-ficticio-teste");

    private static void equal(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) throw new AssertionError("Valor inesperado");
        checks++;
    }

    private static void reject(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError("Entrada invalida foi aceita");
    }

    public static void main(String[] args) {
        // Numeros ficticios: nao existe verificacao de digitos ou consulta oficial.
        Loja loja = new Loja("  Mercado  ", "  01234567890123  ", ENDERECO, LOJISTA);
        equal(null, loja.getId());
        equal("Mercado", loja.getNome());
        equal("01234567890123", loja.getCnpj());
        equal(ENDERECO, loja.getEndereco());
        equal(LOJISTA, loja.getLojista());
        equal(false, loja.isAberta());
        loja.abrir();
        equal(true, loja.isAberta());
        loja.abrir();
        equal(true, loja.isAberta());
        loja.fechar();
        equal(false, loja.isAberta());
        loja.fechar();
        equal(false, loja.isAberta());

        for (String cnpj : new String[] {null, "", " \t", "0123456789012",
                "012345678901234", "0123456789012A", "0123456789012 ",
                "012345 7890123", "01.234.567/8901-23", "０１２３４５６７８９０１２３",
                "0123456789012-", "012345678901\n3"}) {
            reject(() -> new Loja("Mercado", cnpj, ENDERECO, LOJISTA));
        }
        for (String nome : new String[] {null, "", " \t\n"}) {
            reject(() -> new Loja(nome, "01234567890123", ENDERECO, LOJISTA));
            reject(() -> loja.setNome(nome));
            equal("Mercado", loja.getNome());
        }
        reject(() -> new Loja("Mercado", "01234567890123", null, LOJISTA));
        reject(() -> new Loja("Mercado", "01234567890123", ENDERECO, null));
        reject(() -> loja.setEndereco(null));
        equal(ENDERECO, loja.getEndereco());
        loja.setNome("  Mercado Novo  ");
        equal("Mercado Novo", loja.getNome());
        Endereco outro = new Endereco(new Bairro("Bairro", "Cidade", true),
                "12345678", null, "Rua Dois", "20");
        loja.setEndereco(outro);
        equal(outro, loja.getEndereco());
        equal("01234567890123", loja.getCnpj());
        equal(LOJISTA, loja.getLojista());

        // Limites conhecidos: formato nao comprova unicidade nem digitos validos.
        Loja duplicada = new Loja("Outra", loja.getCnpj(), ENDERECO, LOJISTA);
        equal(loja.getCnpj(), duplicada.getCnpj());
        equal("00000000000000", new Loja("Teste", "00000000000000",
                ENDERECO, LOJISTA).getCnpj());
        System.out.println("Loja: " + checks + " verificacoes passaram; unicidade no banco pendente.");
    }
}
