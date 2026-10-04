import com.nexusdispatch.model.Bairro;
public class BairroLetterCheck {
    interface Action { void run(); }
    static void reject(Action action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Texto invalido aceito");
    }
    public static void main(String[] args) {
        Bairro bairro = new Bairro("Centro", "Sorocaba", true);
        for (String text : new String[] {null, "", "   ", "123", "---", "123 !"}) {
            reject(() -> new Bairro(text, "Sorocaba", true));
            reject(() -> new Bairro("Centro", text, true));
            reject(() -> bairro.setNome(text));
            reject(() -> bairro.setCidade(text));
        }
        if (!bairro.getNome().equals("Centro") || !bairro.getCidade().equals("Sorocaba"))
            throw new AssertionError("Valor anterior alterado");
        for (String text : new String[] {"25 de Agosto", "São Paulo", "Á", "Setor 2"}) {
            new Bairro(text, text, false);
            bairro.setNome(text);
            bairro.setCidade(text);
        }
        System.out.println("Construtor e setters: textos sem letras rejeitados; acentos e nomes com numeros aceitos.");
    }
}