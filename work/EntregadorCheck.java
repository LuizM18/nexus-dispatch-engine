import com.nexusdispatch.enums.CategoriaCnh;
import com.nexusdispatch.enums.TipoVeiculo;
import com.nexusdispatch.model.Entregador;
import java.util.Objects;

// Testes executaveis sem dependencias, seguindo as verificacoes locais do projeto.
public class EntregadorCheck {
    private static int checks;

    private static void equal(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Esperado " + expected + ", recebido " + actual);
        }
        checks++;
    }

    private static void reject(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            if (expected.getMessage() == null || expected.getMessage().isBlank()) {
                throw new AssertionError("Erro sem mensagem");
            }
            checks++;
            return;
        }
        throw new AssertionError("Entrada invalida foi aceita");
    }

    private static void state(Entregador e, TipoVeiculo v, CategoriaCnh c, Integer p, Integer n) {
        equal(v, e.getTipoVeiculo());
        equal(c, e.getCategoriaCnh());
        equal(p, e.getPesoBrutoTotalKg());
        equal(n, e.getCapacidadePassageiros());
    }

    // Cada caso verifica criacao e atualizacao; falhas devem preservar os quatro atributos.
    private static void scenario(boolean valid, TipoVeiculo v, CategoriaCnh c, Integer p, Integer n) {
        Entregador existing = new Entregador("Teste", "teste@example.com", null, "hash-ficticio-de-teste", TipoVeiculo.VAN, CategoriaCnh.AC, 5000, 2);
        if (valid) {
            state(new Entregador("Teste", "teste@example.com", null, "hash-ficticio-de-teste", v, c, p, n), v, c, p, n);
            existing.atualizarVeiculoECnh(v, c, p, n);
            state(existing, v, c, p, n);
        } else {
            reject(() -> new Entregador("Teste", "teste@example.com", null, "hash-ficticio-de-teste", v, c, p, n));
            reject(() -> existing.atualizarVeiculoECnh(v, c, p, n));
            state(existing, TipoVeiculo.VAN, CategoriaCnh.AC, 5000, 2);
        }
    }

    public static void main(String[] args) {
        CategoriaCnh[] categories = {null, CategoriaCnh.A, CategoriaCnh.B,
                CategoriaCnh.AB, CategoriaCnh.C, CategoriaCnh.AC};
        // Colunas: bicicleta, moto, carro, van leve, van pesada.
        // Matriz de resultados definida pelo requisito, independente do metodo testado.
        boolean[][] expected = {
            {true, false, false, false, false},
            {true, true, false, false, false},
            {true, false, true, true, false},
            {true, true, true, true, false},
            {true, false, true, true, true},
            {true, true, true, true, true}
        };
        for (int i = 0; i < categories.length; i++) {
            CategoriaCnh c = categories[i];
            scenario(expected[i][0], TipoVeiculo.BICICLETA, c, null, null);
            scenario(expected[i][1], TipoVeiculo.MOTO, c, null, null);
            scenario(expected[i][2], TipoVeiculo.CARRO, c, null, null);
            for (int p : new int[] {1, 3499, 3500}) {
                for (int n : new int[] {0, 2, 8}) {
                    scenario(expected[i][3], TipoVeiculo.VAN, c, p, n);
                }
            }
            for (int p : new int[] {3501, 5000}) {
                scenario(expected[i][4], TipoVeiculo.VAN, c, p, 8);
            }
            scenario(false, null, c, null, null);
            for (Integer p : new Integer[] {null, Integer.MIN_VALUE, -1, 0}) {
                scenario(false, TipoVeiculo.VAN, c, p, 2);
            }
            for (Integer n : new Integer[] {null, Integer.MIN_VALUE, -1, 9, Integer.MAX_VALUE}) {
                scenario(false, TipoVeiculo.VAN, c, 3500, n);
            }
            for (TipoVeiculo v : new TipoVeiculo[] {
                    TipoVeiculo.BICICLETA, TipoVeiculo.MOTO, TipoVeiculo.CARRO}) {
                scenario(false, v, c, 3500, null);
                scenario(false, v, c, null, 0);
                scenario(false, v, c, 3500, 2);
            }
        }
        Entregador bike = new Entregador("Teste", "teste@example.com", null, "hash-ficticio-de-teste", TipoVeiculo.BICICLETA, null, null, null);
        reject(() -> bike.atualizarVeiculoECnh(TipoVeiculo.MOTO, CategoriaCnh.B, null, null));
        state(bike, TipoVeiculo.BICICLETA, null, null, null);
        bike.atualizarVeiculoECnh(TipoVeiculo.MOTO, CategoriaCnh.A, null, null);
        state(bike, TipoVeiculo.MOTO, CategoriaCnh.A, null, null);
        bike.atualizarVeiculoECnh(TipoVeiculo.VAN, CategoriaCnh.C, 5000, 0);
        state(bike, TipoVeiculo.VAN, CategoriaCnh.C, 5000, 0);
        bike.atualizarVeiculoECnh(TipoVeiculo.BICICLETA, null, null, null);
        state(bike, TipoVeiculo.BICICLETA, null, null, null);
        reject(() -> CategoriaCnh.valueOf("D"));
        reject(() -> CategoriaCnh.valueOf("<script>"));
        System.out.println("Entregador: " + checks + " verificacoes passaram.");
    }
}
