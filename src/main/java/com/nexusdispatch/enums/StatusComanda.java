package com.nexusdispatch.enums;

/** Estados da tabela comanda_lojista e transicoes permitidas pelo fluxo aprovado. */
public enum StatusComanda {
    // Pagamento aprovado; a loja ainda nao iniciou o preparo.
    PAGO,
    // Produtos em preparo ou separacao.
    PREPARANDO,
    // Produtos prontos para retirada; o prazo de cancelamento ja encerrou.
    AGUARDANDO_COLETA,
    // Produtos coletados e em transporte.
    EM_TRANSITO,
    // Estados finais do fluxo.
    CONCLUIDO,
    CANCELADO;

    /**
     * Consulta uma regra; nao modifica objetos nem executa SQL.
     * O service verifica tambem permissoes e efeitos da mudanca na compra.
     * Cancelamento exige verificar todas as entregas sob a mesma transacao;
     * este metodo sozinho nao autoriza cancelar nem impede coleta concorrente.
     */
    public boolean permiteTransicaoPara(StatusComanda proximo) {
        if (proximo == null || proximo == this) return false;
        // this e o estado usado para chamar o metodo.
        return switch (this) {
            case PAGO -> proximo == PREPARANDO || proximo == CANCELADO;
            case PREPARANDO -> proximo == AGUARDANDO_COLETA;
            case AGUARDANDO_COLETA -> proximo == EM_TRANSITO;
            case EM_TRANSITO -> proximo == CONCLUIDO;
            case CONCLUIDO, CANCELADO -> false;
        };
    }
}
