package com.nexusdispatch.enums;

/** Valores de entregador.status_operacao no MySQL. */
// DISPONIVEL expressa disponibilidade operacional; o aceite ainda confere
// atividade, ordens e elegibilidade. EM_ROTA ocupa o entregador ate a ultima
// ordem ativa. INATIVO impede novos aceites e nao equivale a apagar o usuario.
public enum StatusOperacaoEntregador {
    DISPONIVEL,
    EM_ROTA,
    INATIVO
}
