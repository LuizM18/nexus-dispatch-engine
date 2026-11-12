// O pacote corresponde ao caminho do arquivo a partir de src/main/java.
package com.nexusdispatch.enums;

// public permite usar este tipo em outros pacotes, como model e service.
// enum define um conjunto fixo de opcoes para o tipo de veiculo.
// O tipo participa das regras locais de CNH e das restricoes de rota/carga
// no service. A constante so identifica a modalidade; nao comprova capacidade
// util nem autorizacao para percorrer uma rua da malha.
public enum TipoVeiculo {
    // Por convencao, constantes de enums sao escritas em MAIUSCULAS.
    CARRO,
    MOTO,
    BICICLETA,
    VAN
    // O ponto e virgula e opcional porque o enum possui apenas constantes.
}
