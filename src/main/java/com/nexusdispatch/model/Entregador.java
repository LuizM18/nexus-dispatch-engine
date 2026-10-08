package com.nexusdispatch.model;

import com.nexusdispatch.enums.TipoVeiculo;
import com.nexusdispatch.enums.CategoriaCnh;

/**
 * Usuario responsavel pelas entregas, com CNH conforme o veiculo; bicicleta dispensa CNH.
 * VAN representa van de carga a combustao, sem reboque, com ate oito passageiros.
 * Essas caracteristicas ainda precisam de verificacao no futuro cadastro.
 * Dados pessoais sao herdados de Usuario; disponibilidade continua pendente.
 */
public class Entregador extends Usuario {
    private TipoVeiculo tipoVeiculo;
    private CategoriaCnh categoriaCnh;
    // Integer permite null: estes dados se aplicam somente a VAN nesta versao.
    private Integer pesoBrutoTotalKg;
    private Integer capacidadePassageiros;

    public Entregador(String nome, String email, String telefone, String senhaHash,
            TipoVeiculo tipoVeiculo, CategoriaCnh categoriaCnh,
            Integer pesoBrutoTotalKg, Integer capacidadePassageiros) {
        // Inicializa e valida os dados comuns na superclasse antes dos especificos.
        super(nome, email, telefone, senhaHash);
        definirVeiculoECnh(tipoVeiculo, categoriaCnh, pesoBrutoTotalKg, capacidadePassageiros);
    }

    public TipoVeiculo getTipoVeiculo() {
        return tipoVeiculo;
    }

    public CategoriaCnh getCategoriaCnh() {
        return categoriaCnh;
    }

    public Integer getPesoBrutoTotalKg() {
        return pesoBrutoTotalKg;
    }

    public Integer getCapacidadePassageiros() {
        return capacidadePassageiros;
    }

    // Substitui setters separados para impedir combinacoes inconsistentes.
    public void atualizarVeiculoECnh(TipoVeiculo tipoVeiculo, CategoriaCnh categoriaCnh,
            Integer pesoBrutoTotalKg, Integer capacidadePassageiros) {
        definirVeiculoECnh(tipoVeiculo, categoriaCnh, pesoBrutoTotalKg, capacidadePassageiros);
    }

    // Construtor e atualizacao compartilham a mesma validacao privada.
    private void definirVeiculoECnh(TipoVeiculo tipoVeiculo, CategoriaCnh categoriaCnh,
            Integer pesoBrutoTotalKg, Integer capacidadePassageiros) {
        validarVeiculoECnh(tipoVeiculo, categoriaCnh, pesoBrutoTotalKg, capacidadePassageiros);

        // So altera apos validar tudo; uma excecao preserva os valores anteriores.
        this.tipoVeiculo = tipoVeiculo;
        this.categoriaCnh = categoriaCnh;
        this.pesoBrutoTotalKg = pesoBrutoTotalKg;
        this.capacidadePassageiros = capacidadePassageiros;
    }

    private void validarVeiculoECnh(TipoVeiculo tipoVeiculo, CategoriaCnh categoriaCnh,
            Integer pesoBrutoTotalKg, Integer capacidadePassageiros) {
        if (tipoVeiculo == null) {
            throw new IllegalArgumentException("O tipo de veículo é obrigatório.");
        }

        // Ao sair de VAN, tambem e necessario remover seus dados especificos.
        if (tipoVeiculo != TipoVeiculo.VAN
                && (pesoBrutoTotalKg != null || capacidadePassageiros != null)) {
            throw new IllegalArgumentException(
                    "Peso bruto total e capacidade de passageiros devem ser informados somente para van.");
        }

        // Comparar enums com == e seguro mesmo quando categoriaCnh e null.
        boolean permiteMoto = categoriaCnh == CategoriaCnh.A
                || categoriaCnh == CategoriaCnh.AB || categoriaCnh == CategoriaCnh.AC;
        boolean permiteCategoriaB = categoriaCnh == CategoriaCnh.B
                || categoriaCnh == CategoriaCnh.AB || categoriaCnh == CategoriaCnh.C
                || categoriaCnh == CategoriaCnh.AC;
        boolean permiteCategoriaC = categoriaCnh == CategoriaCnh.C
                || categoriaCnh == CategoriaCnh.AC;

        switch (tipoVeiculo) {
            case BICICLETA -> {
                // Bicicleta aceita qualquer categoria do enum ou null.
            }
            case MOTO -> {
                if (!permiteMoto) {
                    throw new IllegalArgumentException("Para moto, informe CNH A, AB ou AC.");
                }
            }
            case CARRO -> {
                // CARRO representa um automovel enquadrado na categoria B.
                if (!permiteCategoriaB) {
                    throw new IllegalArgumentException("Para carro, informe CNH B, AB, C ou AC.");
                }
            }
            case VAN -> {
                // PBT e o total permitido do veiculo, nao o peso da entrega atual.
                // || evita comparar numericamente um Integer null.
                if (pesoBrutoTotalKg == null || pesoBrutoTotalKg <= 0) {
                    throw new IllegalArgumentException("Informe o PBT da van em kg, maior que zero.");
                }
                // Capacidade exclui o motorista; zero e oito sao aceitos.
                if (capacidadePassageiros == null || capacidadePassageiros < 0
                        || capacidadePassageiros > 8) {
                    throw new IllegalArgumentException(
                            "A van deve permitir de zero a oito passageiros, sem contar o motorista.");
                }
                if (pesoBrutoTotalKg <= 3500) {
                    if (!permiteCategoriaB) {
                        throw new IllegalArgumentException("Para van até 3.500 kg, informe CNH B, AB, C ou AC.");
                    }
                } else if (!permiteCategoriaC) {
                    throw new IllegalArgumentException("Para van de carga acima de 3.500 kg, informe CNH C ou AC.");
                }
            }
            default -> throw new IllegalArgumentException("Tipo de veículo ainda não suportado.");
        }
    }
}
