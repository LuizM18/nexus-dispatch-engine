// O pacote corresponde ao caminho do arquivo a partir de src/main/java.
package com.nexusdispatch.enums;
// Enum utilizada para validação e escolha de metodo de pagamento e fixa os modulos de pagamento.
// public permite usar este tipo em outros pacotes, como model e service.

public enum MetodoPagamento {
    PIX,
    CARTAO,
    DINHEIRO
    
}

