package com.nexusdispatch.model;

import com.nexusdispatch.enums.StatusPedidoMestre;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Representa os dados gerais de uma compra na tabela pedido_mestre.
 * O record gera acessores como id() e valorTotal(), sem setters.
 * Nao executa SQL, pagamento, baixa de estoque ou cancelamento.
 */
public record PedidoMestreRegistro(
        Long id,
        long idCliente,
        LocalDateTime dataCriacao,
        BigDecimal valorProdutos,
        BigDecimal valorTotal,
        StatusPedidoMestre statusPedido) {

    // Limite das colunas DECIMAL(10,2): oito digitos inteiros e dois decimais.
    private static final BigDecimal VALOR_MAXIMO = new BigDecimal("99999999.99");

    /** Construtor compacto: valida antes de armazenar os componentes. */
    public PedidoMestreRegistro {
        // Long aceita null: o banco gera o ID quando o pedido e inserido.
        if (id != null) validarId(id);
        // O consumidor ja precisa ter um identificador.
        // Existencia, perfil CLIENTE e permissao serao conferidos na integracao.
        validarId(idCliente);

        valorProdutos = validarValor(valorProdutos, "Valor dos produtos");
        valorTotal = validarValor(valorTotal, "Valor total");

        // Total = produtos + fretes. Frete negativo nao e permitido.
        if (valorTotal.compareTo(valorProdutos) < 0) {
            throw new IllegalArgumentException(
                    "O valor total nao pode ser menor que o valor dos produtos.");
        }
        if (statusPedido == null) {
            throw new IllegalArgumentException("O estado do pedido e obrigatorio.");
        }
        // dataCriacao aceita null, assim como a coluna TIMESTAMP atual.
        // O futuro INSERT deve omitir a coluna para aplicar CURRENT_TIMESTAMP.
        // LocalDateTime nao guarda fuso; a conversao JDBC exige padrao definido.
    }

    /** Cria o registro de uma compra nova, antes de receber ID e data do banco. */
    public static PedidoMestreRegistro novoPedido(
            long idCliente, BigDecimal valorProdutos, BigDecimal valorFretes) {
        // O service deve calcular esses valores com os produtos e fretes reais.
        BigDecimal produtos = validarValor(valorProdutos, "Valor dos produtos");
        BigDecimal fretes = validarValor(valorFretes, "Valor dos fretes");
        // add soma valores decimais sem passar por double.
        // O construtor verifica tambem se a soma cabe em DECIMAL(10,2).
        return new PedidoMestreRegistro(
                null, idCliente, null, produtos,
                produtos.add(fretes), StatusPedidoMestre.CRIADO);
    }

    /** Calcula o frete agregado, sem criar uma coluna inexistente no SQL. */
    public BigDecimal valorFretes() {
        return valorTotal.subtract(valorProdutos);
    }

    private static void validarId(long id) {
        // Java long e maior que INT do MySQL; restringimos a faixa aceita.
        if (id <= 0 || id > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("ID fora da faixa INT positiva.");
        }
    }

    private static BigDecimal validarValor(BigDecimal valor, String campo) {
        // Zero e representavel. A compra com itens validos sera exigida no service.
        if (valor == null || valor.signum() < 0
                || valor.compareTo(VALOR_MAXIMO) > 0) {
            throw new IllegalArgumentException(campo + " fora da faixa permitida.");
        }
        try {
            // 10.5 vira 10.50; 10.500 vira 10.50; 10.501 e recusado.
            // Nao arredondamos silenciosamente o valor da compra.
            return valor.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException erro) {
            throw new IllegalArgumentException(
                    campo + " deve ser representavel com duas casas decimais.", erro);
        }
    }
}
