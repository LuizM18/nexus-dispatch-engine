package com.nexusdispatch.model;

import com.nexusdispatch.enums.PerfilUsuario;
import java.time.LocalDateTime;

/**
 * Representa uma linha da tabela usuario, sem expor senha_hash nas consultas.
 * Nao substitui os perfis de dominio: estes exigem outros dados, como endereco.
 * Boolean e LocalDateTime permitem representar NULL existente no banco.
 * O record fornece acessores (id(), nome(), etc.) e nao possui setters.
 */
public record UsuarioRegistro(long id, String nome, String documentoFiscal,
        String email, PerfilUsuario perfil, LocalDateTime dataCadastro, Boolean ativo) {
}
