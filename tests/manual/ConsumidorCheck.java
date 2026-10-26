import com.nexusdispatch.model.*;
import java.util.Objects;

// Testes locais da entidade, sem banco ou dados reais de usuarios.
public class ConsumidorCheck {
    private static int checks;
    private static final String HASH = "hash-ficticio-teste";

    private static Endereco endereco(String numero) {
        return new Endereco(new Bairro("Centro", "Cidade", true),
                "01234567", null, "Rua Um", numero);
    }

    private static Consumidor consumidor(Endereco endereco) {
        return new Consumidor("  Ana  ", "  ana@example.com  ", null, HASH, endereco);
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
        Endereco anterior = endereco("10");
        Consumidor c = consumidor(anterior);
        equal(null, c.getId());
        equal("Ana", c.getNome());
        equal("ana@example.com", c.getEmail());
        equal(null, c.getTelefone());
        equal(HASH, c.getSenhaHash());
        equal(true, anterior == c.getEndereco());
        reject(() -> consumidor(null));
        reject(() -> c.setEndereco(null));
        equal(true, anterior == c.getEndereco());
        equal("10", c.getEndereco().getNumero());

        // Referencias compartilhadas enxergam alteracoes no mesmo objeto.
        Endereco referenciaAntiga = c.getEndereco();
        c.getEndereco().setNumero("25");
        equal("25", referenciaAntiga.getNumero());
        reject(() -> c.getEndereco().setNumero(""));
        equal("25", c.getEndereco().getNumero());
        reject(() -> c.getEndereco().setCep("abc"));
        equal("01234567", c.getEndereco().getCep());

        // Trocar a referencia do consumidor nao troca referencias anteriores.
        Endereco novo = endereco("30");
        c.setEndereco(novo);
        equal(true, novo == c.getEndereco());
        equal(false, referenciaAntiga == c.getEndereco());
        equal("25", referenciaAntiga.getNumero());
        equal("30", c.getEndereco().getNumero());
        reject(() -> c.setEndereco(null));
        equal(true, novo == c.getEndereco());
        referenciaAntiga.setNumero("26");
        equal("30", c.getEndereco().getNumero());
        c.setEndereco(novo);
        equal(true, novo == c.getEndereco());

        // Compartilhar residencia e permitido; nao ha unicidade de endereco.
        Consumidor outro = consumidor(novo);
        equal(true, outro.getEndereco() == c.getEndereco());
        novo.setNumero("31");
        equal("31", outro.getEndereco().getNumero());
        equal("31", c.getEndereco().getNumero());

        // Confirma que o novo construtor continua aplicando as regras de Usuario.
        reject(() -> new Consumidor(null, "ana@example.com", null, HASH, novo));
        reject(() -> new Consumidor("Ana", "invalido", null, HASH, novo));
        reject(() -> new Consumidor("Ana", "ana@example.com", null, null, novo));
        equal("Ana", c.getNome());
        equal("ana@example.com", c.getEmail());
        equal(HASH, c.getSenhaHash());
        System.out.println("Consumidor: " + checks + " verificacoes passaram.");
    }
}
