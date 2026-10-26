package com.nexusdispatch.model;

/** Perfil administrativo que acessa os modulos pelos mesmos servicos dos demais perfis. */
public class Administrador extends Usuario {
    public Administrador(String nome, String email, String telefone, String senhaHash) {
        // Criar este objeto nao concede permissoes: o servico devera autorizar.
        super(nome, email, telefone, senhaHash);
    }
}
