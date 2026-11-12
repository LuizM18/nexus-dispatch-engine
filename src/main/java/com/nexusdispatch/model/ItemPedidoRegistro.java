package com.nexusdispatch.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Dados de item_pedido; preserva o preco aplicado, sem SQL ou baixa de estoque. */
// record gera acessores, equals/hashCode e campos finais. O construtor
// compacto abaixo valida os argumentos antes de o Java armazena-los. A mesma
// validacao vale para dados novos e para linhas reconstruidas pelo DAO.
public record ItemPedidoRegistro(Long id, long idComanda, long idProduto,
        int quantidade, BigDecimal precoUnitarioAplicado, String observacao) {
    private static final BigDecimal PRECO_MAXIMO = new BigDecimal("99999999.99");

    /** Valida tanto criacao quanto leitura de registros do banco. */
    public ItemPedidoRegistro {
        // Long permite ID null antes do INSERT. IDs informados precisam caber em
        // INT positivo; a validacao numerica nao comprova existencia nem permissao.
        if (id != null) validarId(id, "ID do item");
        validarId(idComanda, "ID da comanda");
        validarId(idProduto, "ID do produto");
        if (quantidade <= 0) throw new IllegalArgumentException("Quantidade deve ser positiva.");
        if (precoUnitarioAplicado == null || precoUnitarioAplicado.signum() <= 0
                || precoUnitarioAplicado.compareTo(PRECO_MAXIMO) > 0) {
            throw new IllegalArgumentException("Preco aplicado fora da faixa permitida.");
        }
        try {
            // Normaliza centavos sem arredondar silenciosamente.
            precoUnitarioAplicado = precoUnitarioAplicado.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException erro) {
            throw new IllegalArgumentException("Preco deve ter duas casas decimais exatas.", erro);
        }
        // Observacao e opcional: espacos externos sao removidos e texto vazio vira
        // null. O DAO envia esse NULL explicitamente, sem aplicar o default textual do SQL.
        if (observacao != null) {
            observacao = observacao.strip();
            if (observacao.isBlank()) observacao = null;
            else if (observacao.codePointCount(0, observacao.length()) > 255) {
                throw new IllegalArgumentException("Observacao deve ter no maximo 255 caracteres.");
            }
        }
    }

    /** O service deve obter o preco confiavel e verificar vinculos e estoque. */
    public static ItemPedidoRegistro novoItem(long idComanda, long idProduto,
            int quantidade, BigDecimal precoUnitarioAplicado, String observacao) {
        return new ItemPedidoRegistro(null, idComanda, idProduto, quantidade,
                precoUnitarioAplicado, observacao);
    }

    /** Sem frete; o service confere se subtotais e somas cabem no pedido/comanda. */
    public BigDecimal subtotal() {
        // Usa o preco salvo no item, nao o preco atual do catalogo. O subtotal pode
        // exceder a faixa de uma coluna; o service/registro agregado confere o total final.
        return precoUnitarioAplicado.multiply(BigDecimal.valueOf(quantidade));
    }

    private static void validarId(long id, String campo) {
        if (id < 1 || id > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(campo + " fora da faixa INT positiva.");
        }
    }
}
