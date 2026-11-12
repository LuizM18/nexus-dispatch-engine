package com.nexusdispatch.model;

import com.nexusdispatch.enums.PerfilUsuario;

/** Perfil administrativo que acessa os modulos pelos mesmos servicos dos demais perfis. */
public class Administrador extends Usuario {
    /** Reconstituicao sem inventar senha ou dados adicionais de administrador. */
    public Administrador(UsuarioRegistro registro) { super(registro, PerfilUsuario.ADMIN); }

    /** Perfil usado polimorficamente; nao concede autorizacao automaticamente. */
    @Override
    public PerfilUsuario getPerfil() { return PerfilUsuario.ADMIN; }

    public Administrador(String nome, String email, String telefone, String senhaHash) {
        // Criar este objeto nao concede permissoes: o servico devera autorizar.
        super(nome, email, telefone, senhaHash);
    }
}
