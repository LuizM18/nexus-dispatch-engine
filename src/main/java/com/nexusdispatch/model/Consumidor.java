package com.nexusdispatch.model;

/** Usuario que realiza compras, cada uma podendo reunir pedidos de varias lojas. */
public class Consumidor extends Usuario {
    public Consumidor(String nome, String email, String telefone, String senhaHash) {
        // super chama o construtor de Usuario e suas validacoes.
        super(nome, email, telefone, senhaHash);
    }
}
