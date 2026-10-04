// O pacote corresponde ao caminho do arquivo a partir de src/main/java.
package com.nexusdispatch.enums;

// public permite usar este tipo em outros pacotes, como model e service.
// enum define um conjunto fixo de opcoes para o tipo de veiculo.
public enum TipoVeiculo {
    // Por convencao, constantes de enums sao escritas em MAIUSCULAS.
    CARRO,
    MOTO,
    BICICLETA
    // O ponto e virgula e opcional porque o enum possui apenas constantes.
}
