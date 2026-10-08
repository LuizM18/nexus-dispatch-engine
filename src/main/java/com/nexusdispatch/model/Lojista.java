package com.nexusdispatch.model;

/** Responsavel por uma loja; o CNPJ pertencera a Loja, nao a Usuario. */
public class Lojista extends Usuario {
    public Lojista(String nome, String email, String telefone, String senhaHash) {
        // Os dados comuns sao inicializados e validados na superclasse.
        super(nome, email, telefone, senhaHash);
    }
}
