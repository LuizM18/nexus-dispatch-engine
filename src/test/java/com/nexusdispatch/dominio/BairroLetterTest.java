package com.nexusdispatch.dominio;

import org.junit.jupiter.api.Test;

import com.nexusdispatch.model.Bairro;
// Teste JUnit de dominio, executado em memoria com dados ficticios.
// O metodo anotado com @Test e descoberto pelo Maven/JUnit, sem metodo main.
public class BairroLetterTest {
    interface Action { void run(); }
    // Recebe uma acao adiada (lambda/referencia de metodo) e exige a excecao
    // prevista. Retorno normal significa falha do teste, pois a entrada deveria ser recusada.
    static void reject(Action action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Texto invalido aceito");
    }
    // Verifica a regra de pelo menos uma letra no construtor e nos setters.
    // Textos rejeitados nao devem mudar os dados anteriores; acentos e numeros
    // acompanhados de letras sao casos validos, nao uma exigencia de apenas letras.
    @Test
    void verificar() {
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