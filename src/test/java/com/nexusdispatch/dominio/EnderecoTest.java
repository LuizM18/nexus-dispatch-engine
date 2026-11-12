package com.nexusdispatch.dominio;

import org.junit.jupiter.api.Test;

import com.nexusdispatch.model.Bairro;
import com.nexusdispatch.model.Endereco;
import java.util.Objects;

// Verificacao de dominio executada pelo JUnit, sem conexao com banco.
public class EnderecoTest {
    private static int checks;
    private static final Bairro BAIRRO = new Bairro("Centro", "Sao Paulo", true);

    // Compara valores com Objects.equals, que aceita null. Para BigDecimal,
    // equals tambem considera a escala; os valores esperados expressam esse contrato.
    private static void equal(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Esperado: " + expected + "; recebido: " + actual);
        }
        checks++;
    }

    // Recebe uma acao adiada (lambda/referencia de metodo) e exige a excecao
    // prevista. Retorno normal significa falha do teste, pois a entrada deveria ser recusada.
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

    // Testa os formatos definidos pelo projeto e a normalizacao de cada campo.
    // Construtor e setters devem recusar entradas invalidas; essas verificacoes
    // nao consultam uma base de CEP nem comprovam existencia do endereco.
    @Test
    void verificar() {
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
