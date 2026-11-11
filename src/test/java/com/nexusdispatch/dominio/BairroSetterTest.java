package com.nexusdispatch.dominio;

import org.junit.jupiter.api.Test;

import com.nexusdispatch.model.Bairro;

// Teste JUnit de dominio, executado em memoria com dados ficticios.
// O metodo anotado com @Test e descoberto pelo Maven/JUnit, sem metodo main.
public class BairroSetterTest {
    // Falha explicitamente com AssertionError quando a condicao nao e satisfeita.
    // Isso funciona sem habilitar a palavra-chave assert com a opcao -ea do Java.
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    // Exercita normalizacao e preservacao do estado apos argumentos invalidos.
    // Alternar atendido testa ambos os valores booleanos; ID continua ausente no modelo local.
    @Test
    void verificar() {
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