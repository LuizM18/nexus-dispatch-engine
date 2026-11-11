package com.nexusdispatch.dominio;

import org.junit.jupiter.api.Test;

import com.nexusdispatch.model.*;
import com.nexusdispatch.enums.TipoVeiculo;
import java.util.Objects;

// Verificacoes JUnit com auxiliares de assercao locais; nenhum dado real e usado.
public class UsuarioTest {
    private static int checks;
    private static final String HASH = "hash-ficticio-para-testes";
    interface Factory { Usuario create(String n, String e, String t, String h); }

    // Compara valores com Objects.equals, que aceita null. Para BigDecimal,
    // equals tambem considera a escala; os valores esperados expressam esse contrato.
    private static void equal(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) throw new AssertionError("Valor inesperado");
        checks++;
    }

    // Recebe uma acao adiada (lambda/referencia de metodo) e exige a excecao
    // prevista. Retorno normal significa falha do teste, pois a entrada deveria ser recusada.
    private static void reject(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError("Entrada invalida foi aceita");
    }

    // Aplica o mesmo contrato de Usuario aos quatro perfis por meio de fabricas.
    // As lambdas adiam a construcao para testar argumentos invalidos; o hash ficticio
    // serve apenas para validar preservacao literal, nao seguranca de senha.
    @Test
    void verificar() {
        Factory[] factories = {
            (n, e, t, h) -> new Consumidor(n, e, t, h, new Endereco(
                    new Bairro("Centro", "Cidade", true), "01234567", null, "Rua Um", "1")),
            Lojista::new, Administrador::new,
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
