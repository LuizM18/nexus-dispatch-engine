package com.nexusdispatch.enums;
// public permite usar este tipo em outros pacotes, como model e service.
// enum define um conjunto fixo de opcoes para o status do pedido.
// Estados do pedido LOCAL em memoria. A sequencia de Pedido e CONFIRMADO,
// EM_PREPARACAO, EM_ROTA e ENTREGUE, independentemente da ordem das constantes
// aqui. Comandas persistidas usam StatusComanda, que possui estados adicionais.
public enum StatusPedido {
    CONFIRMADO,
    EM_ROTA,
    EM_PREPARACAO,
    ENTREGUE
}
