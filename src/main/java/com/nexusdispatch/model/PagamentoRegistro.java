package com.nexusdispatch.model;

import com.nexusdispatch.enums.MetodoPagamento;
import com.nexusdispatch.enums.StatusPagamento;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

/** Dados de pagamento; nao autoriza cobrancas nem executa SQL. */
// record gera acessores, equals/hashCode e campos finais. O construtor
// compacto abaixo valida os argumentos antes de o Java armazena-los. A mesma
// validacao vale para dados novos e para linhas reconstruidas pelo DAO.
public record PagamentoRegistro(Long id, long idPedido, MetodoPagamento metodoPagamento,
        BigDecimal valorProcessado, String codigoAutorizacao,
        LocalDateTime dataProcessamento, StatusPagamento statusPagamento) {
    public PagamentoRegistro {
        // Long permite ID null antes do INSERT. IDs informados precisam caber em
        // INT positivo; a validacao numerica nao comprova existencia nem permissao.
        if (id != null) validarId(id);
        validarId(idPedido);
        if (metodoPagamento == null) throw new IllegalArgumentException("Metodo obrigatorio.");
        // DECIMAL(10,2) comporta oito digitos inteiros e dois decimais. Exige valor
        // positivo e normaliza a escala sem arredondar: 10.500 passa, 10.501 e recusado.
        if (valorProcessado == null || valorProcessado.signum() <= 0
                || valorProcessado.compareTo(new BigDecimal("99999999.99")) > 0)
            throw new IllegalArgumentException("Valor processado invalido.");
        try { valorProcessado = valorProcessado.setScale(2, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException e) { throw new IllegalArgumentException("Valor exige centavos exatos.", e); }
        if (codigoAutorizacao == null || codigoAutorizacao.isBlank())
            throw new IllegalArgumentException("Codigo obrigatorio.");
        // O codigo identifica a tentativa/resultado; nesta versao ele e simulado.
        // Conta pontos de codigo para respeitar o limite textual sem confundir UTF-16 com caracteres.
        codigoAutorizacao = codigoAutorizacao.strip();
        if (codigoAutorizacao.codePointCount(0, codigoAutorizacao.length()) > 100)
            throw new IllegalArgumentException("Codigo excede 100 caracteres.");
        // SQL permite status/data NULL em registros antigos. Nao representam aprovacao.
    }

    /** Identificador SIM e apenas referencia interna, nao autorizacao financeira. */
    public static PagamentoRegistro novoPagamento(long idPedido, MetodoPagamento metodo,
            BigDecimal valor) {
        return new PagamentoRegistro(null, idPedido, metodo, valor,
                "SIM-PENDENTE-" + UUID.randomUUID(), null, StatusPagamento.PENDENTE);
    }

    private static void validarId(long id) {
        if (id < 1 || id > Integer.MAX_VALUE) throw new IllegalArgumentException("ID invalido.");
    }
}
