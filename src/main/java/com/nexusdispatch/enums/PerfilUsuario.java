package com.nexusdispatch.enums;

/** Valores exatos do ENUM tipo_perfil no MySQL. */
// Perfil identifica o papel cadastrado e deve corresponder ao subtipo de Usuario.
// CLIENTE corresponde a Consumidor. Ter uma constante ADMIN nao autentica uma
// pessoa: os services e a camada de entrada continuam responsaveis pelo acesso.
public enum PerfilUsuario {
    CLIENTE, LOJISTA, ENTREGADOR, ADMIN
}
