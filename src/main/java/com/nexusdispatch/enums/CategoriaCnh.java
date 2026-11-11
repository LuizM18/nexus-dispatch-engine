package com.nexusdispatch.enums;

// Enum que será utilizado na validação da Cnh do entregador e fixa os tipos aceitos
// Categorias aceitas pelo modelo local Entregador: A para moto; B/C e
// combinacoes segundo as regras do veiculo. O SQL guarda o NUMERO da CNH,
// nao esta categoria; a reconstituicao JDBC nao inventa uma categoria ausente.
public enum CategoriaCnh {
    A,
    B,
    AB,
    C,
    AC

}
// Categorias  de cnh utilizadas pelos entregadores do nosso sistema
