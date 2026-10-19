package com.nexusdispatch.model;

import com.nexusdispatch.enums.StatusPedido;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Uma compra pode reunir varios pedidos, mas cada pedido pertence a uma loja. */
public final class Pedido {
    // O ID sera integrado com o banco. Por enquanto, permanece null.
    private Long id;
    // final fixa a referencia; nao torna a propria Loja imutavel.
    private final Loja loja;
    private final List<ItemPedido> itens;
    private final LocalDateTime dataHoraCriacao;
    private StatusPedido status;

    /**
     * Cria um pedido novo com todos os itens, na confirmacao da compra.
     * Nao processa pagamento nem baixa estoque: essas operacoes cabem ao servico.
     */
    public Pedido(Loja loja, List<ItemPedido> itens) {
        if (loja == null) {
            throw new IllegalArgumentException("A loja do pedido é obrigatória.");
        }
        if (itens == null || itens.isEmpty()) {
            throw new IllegalArgumentException("O pedido deve possuir pelo menos um item.");
        }
        for (ItemPedido item : itens) {
            if (item == null) {
                throw new IllegalArgumentException("O pedido não pode conter itens nulos.");
            }
        }
        // Alterar a lista original nao altera esta copia. add/remove sao proibidos.
        // Os objetos nao sao copiados, mas ItemPedido protege seus dados historicos.
        List<ItemPedido> copiaItens = List.copyOf(itens);
        for (int i = 0; i < copiaItens.size(); i++) {
            ItemPedido item = copiaItens.get(i);
            if (!mesmaLoja(loja, item.getOferta().getLoja())) {
                throw new IllegalArgumentException("Todos os itens devem pertencer à loja do pedido.");
            }
            // Compara com os anteriores. O carrinho deve juntar quantidades repetidas.
            for (int j = 0; j < i; j++) {
                if (mesmaOferta(item.getOferta(), copiaItens.get(j).getOferta())) {
                    throw new IllegalArgumentException("A mesma oferta não pode aparecer duas vezes no pedido.");
                }
            }
        }
        this.loja = loja;
        this.itens = copiaItens;
        this.dataHoraCriacao = LocalDateTime.now();
        this.status = StatusPedido.CONFIRMADO;
    }

    private static boolean mesmaLoja(Loja primeira, Loja segunda) {
        // == identifica o mesmo objeto, mesmo antes de receber um ID.
        if (primeira == segunda) return true;
        if (primeira == null || segunda == null) return false;
        // Dois IDs ausentes nao tornam duas lojas diferentes iguais.
        return primeira.getId() != null && primeira.getId().equals(segunda.getId());
    }

    private static boolean mesmaOferta(OfertaProduto primeira, OfertaProduto segunda) {
        if (primeira == segunda) return true;
        if (primeira == null || segunda == null) return false;
        return primeira.getId() != null && primeira.getId().equals(segunda.getId());
    }

    public Long getId() { return id; }
    public Loja getLoja() { return loja; }
    public List<ItemPedido> getItens() { return itens; }
    public LocalDateTime getDataHoraCriacao() { return dataHoraCriacao; }
    public StatusPedido getStatus() { return status; }

    /** Soma os precos historicos dos itens, sem frete, comissao ou desconto. */
    public BigDecimal getSubtotal() {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (ItemPedido item : itens) {
            // add devolve outro BigDecimal: e necessario guardar o resultado.
            subtotal = subtotal.add(item.getSubtotal());
        }
        return subtotal;
    }

    public void iniciarPreparacao() {
        alterarStatus(StatusPedido.CONFIRMADO, StatusPedido.EM_PREPARACAO);
    }
    public void iniciarEntrega() {
        alterarStatus(StatusPedido.EM_PREPARACAO, StatusPedido.EM_ROTA);
    }
    public void concluirEntrega() {
        alterarStatus(StatusPedido.EM_ROTA, StatusPedido.ENTREGUE);
    }

    private void alterarStatus(StatusPedido esperado, StatusPedido novo) {
        if (status != esperado) {
            // A atribuicao nao acontece em caso de erro: preserva o estado anterior.
            throw new IllegalStateException("Não é possível mudar de " + status + " para " + novo + ".");
        }
        status = novo;
    }
    // Sem setters: itens e loja nao mudam, e estados seguem apenas a sequencia.
    // Permissoes, concorrencia e atribuicao de entregador pertencem aos servicos.
    // Cada pedido tera sua OrdemEntrega; nao exige entregadores distintos por compra.
    // A reconstituicao com ID, data e estado originais sera implementada com o DAO.
}
