package com.nexusdispatch.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Dados da tabela lojista. Nao substitui a entidade de dominio Lojista.
 * O record cria acessores (id(), nomeFantasia(), etc.), equals e hashCode.
 * Seus campos sao imutaveis: para atualizar o cadastro, criamos outro registro.
 */
public record LojistaRegistro(long id, String nomeFantasia, BigDecimal taxaComissaoPct,
        String bairroOperacao, String enderecoOrigem) {

    /** Construtor compacto: valida antes de o Java armazenar os componentes. */
    public LojistaRegistro {
        // O ID vem de usuario.id_usuario; lojista nao possui AUTO_INCREMENT.
        if (id <= 0 || id > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("O ID deve ser positivo e estar na faixa de INT.");
        }
        nomeFantasia = validarTexto(nomeFantasia, "Nome fantasia", 120);
        bairroOperacao = validarTexto(bairroOperacao, "Bairro de operacao", 100);
        enderecoOrigem = validarTexto(enderecoOrigem, "Endereco de origem", 255);
        if (taxaComissaoPct == null) {
            throw new IllegalArgumentException("A taxa de comissao e obrigatoria.");
        }
        // 12.50 significa 12,5%. compareTo compara valores, sem depender da escala.
        if (taxaComissaoPct.compareTo(BigDecimal.ZERO) < 0
                || taxaComissaoPct.compareTo(new BigDecimal("100.00")) > 0) {
            throw new IllegalArgumentException("A comissao deve estar entre 0 e 100.");
        }
        try {
            // DECIMAL(5,2): aceita 12.5 e 12.500, mas rejeita 12.345 sem arredondar.
            taxaComissaoPct = taxaComissaoPct.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("A comissao deve ser representavel com duas casas decimais.", e);
        }
    }

    private static String validarTexto(String valor, String campo, int limite) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " e obrigatorio.");
        }
        String normalizado = valor.strip();
        // Unicode: um caractere suplementar nao deve contar como dois.
        if (normalizado.codePointCount(0, normalizado.length()) > limite) {
            throw new IllegalArgumentException(campo + " deve ter no maximo " + limite + " caracteres.");
        }
        return normalizado;
    }
}
