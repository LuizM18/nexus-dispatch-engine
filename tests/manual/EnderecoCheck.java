import com.nexusdispatch.model.Bairro;
import com.nexusdispatch.model.Endereco;
import java.util.Objects;

// Verificacao independente, executavel sem bibliotecas de testes.
public class EnderecoCheck {
    private static int checks;
    private static final Bairro BAIRRO = new Bairro("Centro", "Sao Paulo", true);

    private static void equal(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Esperado: " + expected + "; recebido: " + actual);
        }
        checks++;
    }

    private static void reject(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            checks++;
            return;
        }
        throw new AssertionError("Era esperada IllegalArgumentException.");
    }

    private static Endereco create(String cep, String numero, String rua, String complemento) {
        return new Endereco(BAIRRO, cep, complemento, rua, numero);
    }

    public static void main(String[] args) {
        Endereco endereco = create(" 01234567 ", " 12A ", " Rua Sao Jose ", " Apto 2 ");
        equal(null, endereco.getId());
        equal("01234567", endereco.getCep());
        equal("12A", endereco.getNumero());
        equal("Rua Sao Jose", endereco.getLogradouro());
        equal("Apto 2", endereco.getComplemento());
        equal(true, endereco.getBairro() == BAIRRO);

        for (String cep : new String[]{"01234567", "012345678", "0123456789", " 01234567 "}) {
            equal(cep.strip(), create(cep, "1", "Rua", null).getCep());
            endereco.setCep(cep);
            equal(cep.strip(), endereco.getCep());
        }
        for (String cep : new String[]{null, "", "   ", "1234567", "01234567890", "12345-678", "1234 5678", "abcdefgh", "1234567a"}) {
            reject(() -> create(cep, "1", "Rua", null));
            String anterior = endereco.getCep();
            reject(() -> endereco.setCep(cep));
            equal(anterior, endereco.getCep());
        }
        for (String numero : new String[]{"123", " 12A ", "s/n", " S/N ", "abc1", " bloco2 "}) {
            equal(numero.strip(), create("01234567", numero, "Rua", null).getNumero());
            endereco.setNumero(numero);
            equal(numero.strip(), endereco.getNumero());
        }
        for (String numero : new String[]{null, "", "   ", "abc", "s\\n"}) {
            reject(() -> create("01234567", numero, "Rua", null));
            String anterior = endereco.getNumero();
            reject(() -> endereco.setNumero(numero));
            equal(anterior, endereco.getNumero());
        }
        for (String rua : new String[]{"Rua", " Avenida Sao Jose ", "Á", "Rua 123"}) {
            equal(rua.strip(), create("01234567", "1", rua, null).getLogradouro());
            endereco.setLogradouro(rua);
            equal(rua.strip(), endereco.getLogradouro());
        }
        for (String rua : new String[]{null, "", "   ", "123", "---"}) {
            reject(() -> create("01234567", "1", rua, null));
            String anterior = endereco.getLogradouro();
            reject(() -> endereco.setLogradouro(rua));
            equal(anterior, endereco.getLogradouro());
        }
        for (String complemento : new String[]{null, "", "   ", " Apto 3 "}) {
            String esperado = complemento == null ? "" : complemento.strip();
            equal(esperado, create("01234567", "1", "Rua", complemento).getComplemento());
            endereco.setComplemento(complemento);
            equal(esperado, endereco.getComplemento());
        }
        reject(() -> new Endereco(null, "01234567", null, "Rua", "1"));
        reject(() -> endereco.setBairro(null));
        equal(true, endereco.getBairro() == BAIRRO);
        Bairro outro = new Bairro("Jardim", "Sao Paulo", false);
        endereco.setBairro(outro);
        equal(true, endereco.getBairro() == outro);
        equal(true, new Endereco(outro, "01234567", null, "Rua", "1").getBairro() == outro);
        System.out.println("PASSOU: " + checks + " verificacoes de construtor, getters e setters.");
    }
}
