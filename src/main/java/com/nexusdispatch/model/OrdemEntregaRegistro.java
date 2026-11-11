package com.nexusdispatch.model;

import com.nexusdispatch.enums.StatusOrdemEntrega;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/** Dados do SQL. Malha, autorizacao, coleta e cancelamento pertencem aos services. */
// record gera acessores, equals/hashCode e campos finais. O construtor
// compacto abaixo valida os argumentos antes de o Java armazena-los. A mesma
// validacao vale para dados novos e para linhas reconstruidas pelo DAO.
public record OrdemEntregaRegistro(Long id, long idComanda, Long idEntregador,
        String modalidadeFrete, BigDecimal distanciaKm, BigDecimal valorRepasseEntregador,
        BigDecimal taxaFreteRetida, StatusOrdemEntrega statusEntrega,
        LocalDateTime dataHoraColeta, LocalDateTime dataHoraEntrega) {
    public OrdemEntregaRegistro {
        // Long permite ID null antes do INSERT. IDs informados precisam caber em
        // INT positivo; a validacao numerica nao comprova existencia nem permissao.
        if (id != null) validarId(id);
        validarId(idComanda);
        // Entregador pode estar ausente antes do aceite; FK tambem permite SET NULL.
        if (idEntregador != null) validarId(idEntregador);
        if (modalidadeFrete == null || modalidadeFrete.isBlank())
            throw new IllegalArgumentException("Modalidade obrigatoria.");
        modalidadeFrete = modalidadeFrete.strip();
        if (modalidadeFrete.codePointCount(0, modalidadeFrete.length()) > 30)
            throw new IllegalArgumentException("Modalidade excede 30 caracteres.");
        // Distancia segue DECIMAL(6,2) e deve ser positiva. Valores financeiros seguem
        // DECIMAL(10,2) e aceitam zero, pois uma parcela do rateio pode nao receber centavos.
        distanciaKm = decimal(distanciaKm, "9999.99", true);
        valorRepasseEntregador = decimal(valorRepasseEntregador, "99999999.99", false);
        taxaFreteRetida = decimal(taxaFreteRetida, "99999999.99", false);
        if (statusEntrega == null) throw new IllegalArgumentException("Estado obrigatorio.");
        // Uma entrega datada exige coleta anterior ou simultanea. Essa coerencia local
        // nao valida toda a maquina de estados: o service coordena transicoes e vinculos.
        if (dataHoraEntrega != null && (dataHoraColeta == null || dataHoraEntrega.isBefore(dataHoraColeta)))
            throw new IllegalArgumentException("Entrega exige coleta anterior ou simultanea.");
        // Transicoes e coerencia operacional das datas sao verificadas pelo service.
    }

    /** Distancia loja-consumidor vem da malha; nao inclui aproximacao do entregador. */
    public static OrdemEntregaRegistro novaOrdem(long idComanda, BigDecimal distancia,
            BigDecimal repasse, BigDecimal taxa) {
        return new OrdemEntregaRegistro(null, idComanda, null, "FIXO", distancia,
                repasse, taxa, StatusOrdemEntrega.AGUARDANDO_COLETA, null, null);
    }

    /** Frete agregado; o service valida somas contra o total do pedido. */
    public BigDecimal valorFrete() { return valorRepasseEntregador.add(taxaFreteRetida); }

    // positivo diferencia distancia (> 0) de dinheiro (>= 0). UNNECESSARY aceita
    // remover zeros excedentes, mas rejeita qualquer perda de precisao em duas casas.
    private static BigDecimal decimal(BigDecimal valor, String maximo, boolean positivo) {
        if (valor == null || valor.signum() < 0 || (positivo && valor.signum() == 0)
                || valor.compareTo(new BigDecimal(maximo)) > 0)
            throw new IllegalArgumentException("Valor decimal fora da faixa.");
        try { return valor.setScale(2, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException e) { throw new IllegalArgumentException("Valor exige duas casas exatas.", e); }
    }

    private static void validarId(long id) {
        if (id < 1 || id > Integer.MAX_VALUE) throw new IllegalArgumentException("ID invalido.");
    }
}
