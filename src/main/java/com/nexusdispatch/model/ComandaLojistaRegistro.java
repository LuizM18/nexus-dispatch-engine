package com.nexusdispatch.model;

import com.nexusdispatch.enums.StatusComanda;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Representa a parte de uma compra pertencente a um lojista.
 * O record gera acessores, como id(), sem setters. Os itens ficam separados.
 * Valida os dados, mas nao acessa o banco, processa pagamento ou autoriza acesso.
 */
public record ComandaLojistaRegistro(
        Long id, long idPedido, long idLojista,
        BigDecimal valorProdutos, BigDecimal taxaVendaRetida,
        BigDecimal repasseLojista, StatusComanda statusComanda,
        LocalDateTime dataHoraStatus) {

    // DECIMAL(10,2): oito digitos inteiros e dois decimais.
    private static final BigDecimal VALOR_MAXIMO = new BigDecimal("99999999.99");

    /** Valida tanto criacao quanto reconstrucao de registros consultados pelo DAO. */
    public ComandaLojistaRegistro {
        // ID null antes da insercao; o banco gera o identificador.
        if (id != null) validarId(id, "ID da comanda");
        // A faixa numerica nao comprova existencia; as FKs fazem essa verificacao.
        validarId(idPedido, "ID do pedido");
        validarId(idLojista, "ID do lojista");
        valorProdutos = validarValor(valorProdutos, "Valor dos produtos");
        taxaVendaRetida = validarValor(taxaVendaRetida, "Taxa de venda retida");
        repasseLojista = validarValor(repasseLojista, "Repasse do lojista");
        if (taxaVendaRetida.compareTo(valorProdutos) > 0) {
            throw new IllegalArgumentException("A taxa retida nao pode superar o valor dos produtos.");
        }
        // compareTo compara valores numericos, independentemente da escala original.
        BigDecimal repasseEsperado = valorProdutos.subtract(taxaVendaRetida);
        if (repasseLojista.compareTo(repasseEsperado) != 0) {
            throw new IllegalArgumentException("Repasse deve ser igual aos produtos menos a taxa retida.");
        }
        if (statusComanda == null) {
            throw new IllegalArgumentException("O estado da comanda e obrigatorio.");
        }
        // Data aceita null conforme o SQL. Na insercao, o DAO omite a coluna para
        // aplicar CURRENT_TIMESTAMP. O construtor preserva estados historicos.
        // ON UPDATE no SQL pode alterar a data por mudancas em outros campos.
    }

    /**
     * Cria dados novos em PAGO. O service deve confirmar o pagamento antes.
     * Este metodo nao comprova pagamento: apenas monta e valida o objeto.
     */
    public static ComandaLojistaRegistro novaComanda(long idPedido, long idLojista,
            BigDecimal valorProdutos, BigDecimal taxaVendaRetida) {
        BigDecimal produtos = validarValor(valorProdutos, "Valor dos produtos");
        BigDecimal taxa = validarValor(taxaVendaRetida, "Taxa de venda retida");
        if (taxa.compareTo(produtos) > 0) {
            throw new IllegalArgumentException("A taxa retida nao pode superar o valor dos produtos.");
        }
        // A taxa ja e um valor monetario; a porcentagem e calculada pelo service.
        return new ComandaLojistaRegistro(null, idPedido, idLojista, produtos, taxa,
                produtos.subtract(taxa), StatusComanda.PAGO, null);
    }

    /** long comporta mais valores que INT do MySQL; restringimos a faixa. */
    private static void validarId(long id, String campo) {
        if (id <= 0 || id > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(campo + " deve estar na faixa INT positiva.");
        }
    }

    /** Normaliza duas casas sem arredondar silenciosamente calculos financeiros. */
    private static BigDecimal validarValor(BigDecimal valor, String campo) {
        if (valor == null || valor.signum() < 0 || valor.compareTo(VALOR_MAXIMO) > 0) {
            throw new IllegalArgumentException(campo + " fora da faixa permitida.");
        }
        try {
            // 10.5 e 10.500 viram 10.50; 10.501 exige arredondamento e e recusado.
            return valor.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException erro) {
            throw new IllegalArgumentException(campo + " deve ter duas casas decimais exatas.", erro);
        }
    }
}
