import com.nexusdispatch.model.Bairro;

public class BairroSetterCheck {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        Bairro bairro = new Bairro("  Centro  ", "  Sorocaba  ", false);
        check(bairro.getNome().equals("Centro"), "Nome inicial normalizado");
        check(bairro.getCidade().equals("Sorocaba"), "Cidade inicial normalizada");
        bairro.setNome("  Jardim America  ");
        bairro.setCidade("  Sao Paulo  ");
        check(bairro.getNome().equals("Jardim America"), "Setter de nome normaliza");
        check(bairro.getCidade().equals("Sao Paulo"), "Setter de cidade normaliza");
        for (String invalid : new String[] {null, "", "   "}) {
            try {
                bairro.setNome(invalid);
                throw new AssertionError("Nome invalido aceito");
            } catch (IllegalArgumentException expected) {
                check(bairro.getNome().equals("Jardim America"), "Nome anterior preservado");
            }
            try {
                bairro.setCidade(invalid);
                throw new AssertionError("Cidade invalida aceita");
            } catch (IllegalArgumentException expected) {
                check(bairro.getCidade().equals("Sao Paulo"), "Cidade anterior preservada");
            }
        }
        bairro.setAtendido(true);
        check(bairro.isAtendido(), "Ativacao do atendimento");
        bairro.setAtendido(false);
        check(!bairro.isAtendido(), "Desativacao do atendimento");
        check(bairro.getId() == null, "Id nao atribuido");
        System.out.println("Validacoes e alteracoes de Bairro conferidas com sucesso.");
    }
}