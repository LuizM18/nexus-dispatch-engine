package com.nexusdispatch.enums;
// public permite usar este tipo em outros pacotes, como model e service.
// enum define um conjunto fixo de opcoes para o status da entrega.
// Ciclo LOCAL: AGUARDANDO_ENTREGADOR -> ACEITA -> EM_ROTA -> ENTREGUE.
// OrdemEntrega controla essas transicoes em memoria. A tabela ordem_entrega
// usa StatusOrdemEntrega; nao converter nomes entre os dois enums automaticamente.
public enum StatusEntrega {
    AGUARDANDO_ENTREGADOR,
    ACEITA,
    EM_ROTA,
    ENTREGUE

}
