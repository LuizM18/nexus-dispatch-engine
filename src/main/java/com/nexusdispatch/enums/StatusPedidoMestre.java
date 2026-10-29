package com.nexusdispatch.enums;

/** Estados da compra completa, conforme pedido_mestre.status_pedido. */
public enum StatusPedidoMestre {
    // Compra registrada; pagamento ainda nao confirmado.
    CRIADO,
    // Pagamento confirmado; nao significa que a entrega terminou.
    PAGO,
    // Compra concluida segundo as regras do futuro service.
    CONCLUIDO,
    // Compra cancelada; devolucao de estoque exige transacao no service.
    CANCELADO
}
