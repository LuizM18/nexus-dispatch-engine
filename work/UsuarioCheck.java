import com.nexusdispatch.model.*;
import com.nexusdispatch.enums.TipoVeiculo;
import java.util.Objects;

// Verificacoes independentes, sem biblioteca externa; nenhum dado real e usado.
public class UsuarioCheck {
    private static int checks;
    private static final String HASH = "hash-ficticio-para-testes";
    interface Factory { Usuario create(String n, String e, String t, String h); }

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
        Factory[] factories = {Consumidor::new, Lojista::new, Administrador::new,
            (n, e, t, h) -> new Entregador(n, e, t, h, TipoVeiculo.BICICLETA, null, null, null)};
        for (Factory f : factories) {
            Usuario u = f.create("  João Silva  ", "  joao@example.com  ", "  11999990000  ", HASH);
            equal(null, u.getId());
            equal("João Silva", u.getNome());
            equal("joao@example.com", u.getEmail());
            equal("11999990000", u.getTelefone());
            equal(HASH, u.getSenhaHash());
            for (String n : new String[] {null, "", " \t\n", "123", "---"}) {
                reject(() -> f.create(n, "a@example.com", null, HASH));
                reject(() -> u.setNome(n));
                equal("João Silva", u.getNome());
            }
            for (String e : new String[] {null, "", "  ", "ana", "@example.com",
                    "ana@", "ana@example", "ana@@example.com", "a b@example.com",
                    "a@example .com", "a\u00a0b@example.com"}) {
                reject(() -> f.create("Ana", e, null, HASH));
            }
            for (String h : new String[] {null, "", " \t"}) {
                reject(() -> f.create("Ana", "ana@example.com", null, h));
            }
            for (String t : new String[] {null, "", " \t"}) {
                equal(null, f.create("Ana", "ana@example.com", t, HASH).getTelefone());
                u.setTelefone(t);
                equal(null, u.getTelefone());
            }
            u.setNome("  Maria d'Ávila  ");
            equal("Maria d'Ávila", u.getNome());
            u.setTelefone("  +55 (11) 99999-0000  ");
            equal("+55 (11) 99999-0000", u.getTelefone());
            equal("joao@example.com", u.getEmail());
            equal(HASH, u.getSenhaHash());
            // Preservacao literal: a entidade nao modifica nem autentica o hash.
            equal(" hash-ficticio ", f.create("Ana", "ana+teste@example.com", null,
                    " hash-ficticio ").getSenhaHash());
        }
        System.out.println("Usuario: " + checks + " verificacoes passaram nos quatro perfis.");
    }
}
