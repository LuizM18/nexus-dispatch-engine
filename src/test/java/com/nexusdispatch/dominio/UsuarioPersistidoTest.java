package com.nexusdispatch.dominio;

import org.junit.jupiter.api.Test;

import com.nexusdispatch.enums.PerfilUsuario;
import com.nexusdispatch.model.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Verifica identidade, ausencia de credenciais e composicao dos perfis lidos. */
public class UsuarioPersistidoTest {
    private static int checks;
    // Transforma uma condicao falsa em AssertionError para interromper o teste.
    // A mensagem/contador identifica a verificacao; imprimir sucesso nao substitui a assercao.
    private static void ok(boolean valor) {
        if (!valor) throw new AssertionError("Contrato de reconstituicao violado");
        checks++;
    }
    // Executa a acao e confere a excecao esperada. Excecoes de outro tipo
    // nao devem ser confundidas com uma rejeicao correta da regra testada.
    private static void rejeitar(Runnable acao) {
        try { acao.run(); } catch (IllegalArgumentException esperado) { checks++; return; }
        throw new AssertionError("Registro invalido aceito");
    }
    private static UsuarioRegistro registro(long id, PerfilUsuario perfil, Boolean ativo) {
        return new UsuarioRegistro(id, "Ana", "DOC-1", "ana@example.test", perfil,
                LocalDateTime.of(2026, 11, 3, 10, 0), ativo);
    }
    // Reconstitui perfis a partir de registros sinteticos sem carregar credenciais.
    // Confere identidade, campos opcionais e despacho polimorfico de getPerfil,
    // inclusive quando o objeto e consultado por uma referencia Usuario.
    @Test
    void verificar() {
        var cliente = registro(1, PerfilUsuario.CLIENTE, true);
        var admin = registro(2, PerfilUsuario.ADMIN, false);
        var lojista = registro(3, PerfilUsuario.LOJISTA, null);
        var complemento = new LojistaRegistro(3, "Loja Ana", new BigDecimal("5.00"), "Centro", "Rua A");
        // Chamadas via Usuario demonstram despacho para cada sobrescrita.
        List<Usuario> perfis = List.of(new Consumidor(cliente), new Administrador(admin), new Lojista(lojista, complemento));
        List<UsuarioRegistro> registros = List.of(cliente, admin, lojista);
        for (int i = 0; i < perfis.size(); i++) {
            Usuario usuario = perfis.get(i);
            ok(usuario.toRegistro().equals(registros.get(i)));
            ok(usuario.getPerfil() == registros.get(i).perfil());
            ok(usuario.getId().equals(registros.get(i).id()));
            ok(usuario.getDocumentoFiscal().equals("DOC-1"));
            ok(usuario.getDataCadastro().equals(registros.get(i).dataCadastro()));
            ok(usuario.getSenhaHash() == null && usuario.getTelefone() == null);
        }
        ok(perfis.getFirst().isAtivo());
        ok(!perfis.get(1).isAtivo());
        ok(!perfis.get(2).isAtivo() && perfis.get(2).getAtivo() == null);
        ok(((Consumidor)perfis.getFirst()).getEndereco() == null);
        ok(((Lojista)perfis.get(2)).getDadosLojista().orElseThrow().equals(complemento));
        rejeitar(() -> new Administrador(cliente));
        rejeitar(() -> new Consumidor(admin));
        rejeitar(() -> new Lojista(cliente, complemento));
        rejeitar(() -> new Lojista(registro(4, PerfilUsuario.LOJISTA, true), complemento));
        rejeitar(() -> new Consumidor(registro(0, PerfilUsuario.CLIENTE, true)));
        rejeitar(() -> new Consumidor(new UsuarioRegistro(1, "Ana", " ", "ana@example.test", PerfilUsuario.CLIENTE, null, null)));
        rejeitar(() -> perfis.getFirst().setNome("a".repeat(121)));
        ok(perfis.getFirst().getNome().equals("Ana"));
        var semData = new Consumidor(new UsuarioRegistro(5, "Ana", "DOC", "ana@example.test", PerfilUsuario.CLIENTE, null, null));
        ok(semData.getDataCadastro() == null && semData.toRegistro().dataCadastro() == null);
        var antigo = new Lojista("Ana", "ana@example.test", null, "hash local");
        ok(antigo.getDadosLojista().isEmpty() && antigo.getId() == null);
        try { antigo.toRegistro(); throw new AssertionError("Identidade inventada"); }
        catch (IllegalStateException esperado) { checks++; }
        System.out.println("Usuario persistido: " + checks + " verificacoes aprovadas.");
    }
}
