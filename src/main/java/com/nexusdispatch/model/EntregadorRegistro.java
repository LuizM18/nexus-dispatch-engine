package com.nexusdispatch.model;

import com.nexusdispatch.enums.StatusOperacaoEntregador;
import com.nexusdispatch.enums.TipoVeiculo;

/**
 * Dados da tabela entregador, conferida no MySQL local em 27/10/2026.
 * Nao substitui Entregador, que valida categoria de CNH e porte do veiculo.
 * O record gera acessores como id() e tipoVeiculo(), sem setters.
 * O futuro DAO executara SQL; este arquivo apenas representa e valida dados.
 */
public record EntregadorRegistro(
        long id,
        String cnh,
        TipoVeiculo tipoVeiculo,
        StatusOperacaoEntregador statusOperacao,
        String bairroOperacao) {

    /** Construtor compacto: valida antes de armazenar os componentes. */
    public EntregadorRegistro {
        // id_entregador nao e AUTO_INCREMENT: recebe usuario.id_usuario.
        // O usuario precisa existir; a FK sera conferida pelo banco.
        if (id <= 0 || id > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "O ID deve ser positivo e estar na faixa de INT.");
        }
        if (tipoVeiculo == null) {
            throw new IllegalArgumentException("O tipo de veiculo e obrigatorio.");
        }

        // cnh e o NUMERO do documento, nao a categoria A/B/AB/C/AC.
        // O DDL permite ate 20 caracteres; nao inventamos formato ou autenticacao.
        cnh = normalizarOpcional(cnh, "CNH", 20);
        if (tipoVeiculo != TipoVeiculo.BICICLETA && cnh == null) {
            throw new IllegalArgumentException(
                    "A CNH e obrigatoria para moto, carro e van.");
        }
        // O SQL guarda bairro como texto opcional, sem FK para bairros.
        bairroOperacao = normalizarOpcional(bairroOperacao, "Bairro de operacao", 100);
        // status_operacao aceita SQL NULL. Preservamos a ausencia na leitura;
        // o futuro service nao deve interpretar null como DISPONIVEL.
    }

    /**
     * Cadastro novo com bairro-base informado e inicialmente INATIVO.
     * O futuro DAO deve enviar esse status explicitamente: o DEFAULT do SQL
     * e DISPONIVEL, diferente da regra de iniciar indisponivel no Java.
     */
    public static EntregadorRegistro novoCadastro(
            long id, String cnh, TipoVeiculo tipoVeiculo, String bairroOperacao) {
        if (bairroOperacao == null || bairroOperacao.isBlank()) {
            throw new IllegalArgumentException(
                    "Informe o bairro-base para cadastrar um entregador.");
        }
        return new EntregadorRegistro(
                id, cnh, tipoVeiculo, StatusOperacaoEntregador.INATIVO, bairroOperacao);
    }

    private static String normalizarOpcional(String valor, String campo, int limite) {
        // Texto vazio vira ausencia; esses campos permitem NULL no SQL.
        if (valor == null || valor.isBlank()) return null;
        String normalizado = valor.strip();
        // Unicode: um caractere suplementar nao deve contar como dois.
        if (normalizado.codePointCount(0, normalizado.length()) > limite) {
            throw new IllegalArgumentException(
                    campo + " deve ter no maximo " + limite + " caracteres.");
        }
        return normalizado;
    }
}
