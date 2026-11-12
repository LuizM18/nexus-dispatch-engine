package com.nexusdispatch.enums;
/** Estados persistidos. Preserva o StatusEntrega usado pelo dominio antigo. */
// AGUARDANDO_COLETA pode estar sem entregador ou ja atribuido; o vinculo
// e separado do estado. EM_TRANSITO indica coleta; ENTREGUE indica conclusao.
// DEVOLVIDO/CANCELADO preservam valores SQL, sem implicar que todo fluxo esteja exposto no menu.
public enum StatusOrdemEntrega {
    AGUARDANDO_COLETA, EM_TRANSITO, ENTREGUE, DEVOLVIDO, CANCELADO
}
