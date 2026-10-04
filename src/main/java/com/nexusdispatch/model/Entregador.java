package com.nexusdispatch.model;

import com.nexusdispatch.enums.TipoVeiculo;

/**
 * Usuario responsavel pelas entregas, com CNH conforme o veiculo; bicicleta dispensa CNH.
 * A disponibilidade sera representada por boolean.
 */
public class Entregador extends Usuario {
    private TipoVeiculo tipoVeiculo;

}
