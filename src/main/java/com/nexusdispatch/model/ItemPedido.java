package com.nexusdispatch.model;

import java.math.BigDecimal;

/** Item comprado com nome, descricao e preco preservados no momento da criacao. */
public final class ItemPedido {
    // null na criacao; o DAO podera reconstituir o item com o ID salvo no banco.
    private final Long id;
    // final impede trocar a referencia, mas a oferta continua sendo mutavel.
    private final OfertaProduto oferta;
    // String e BigDecimal sao imutaveis: estes valores historicos nao mudam.
    private final String nomeProduto;
    private final String descricaoProduto;
    private final int quantidade;
    private final BigDecimal precoUnitario;

    /** Cria um item novo. Valida o saldo, mas nao retira unidades do estoque. */
    public ItemPedido(OfertaProduto oferta, int quantidade) {
        validarOferta(oferta);
        validarQuantidade(quantidade);
        // Pode comprar todo o saldo. Somente quantidades acima dele sao recusadas.
        // O servico ainda deve somar itens repetidos e conferir o saldo na transacao.
        if (quantidade > oferta.getQuantidadeDisponivelParaVenda()) {
            throw new IllegalArgumentException(
                    "A quantidade solicitada excede o estoque disponível para venda.");
        }
        Produto produto = oferta.getProduto();
        if (produto == null) {
            throw new IllegalArgumentException("A oferta deve estar associada a um produto.");
        }
        String nomeAtual = produto.getNome();
        String descricaoAtual = produto.getDescricao();
        BigDecimal precoAtual = oferta.getPreco();
        validarNomeProduto(nomeAtual);
        validarPreco(precoAtual);
        this.id = null;
        this.oferta = oferta;
        this.nomeProduto = nomeAtual;
        this.descricaoProduto = descricaoAtual;
        this.quantidade = quantidade;
        this.precoUnitario = precoAtual;
    }

    // Recuperar uma compra antiga nao depende do estoque ou do preco de hoje.
    private ItemPedido(Long id, OfertaProduto oferta, int quantidade,
            String nomeProduto, String descricaoProduto, BigDecimal precoUnitario) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("O ID do item salvo deve ser maior que zero.");
        }
        validarOferta(oferta);
        validarQuantidade(quantidade);
        validarNomeProduto(nomeProduto);
        validarPreco(precoUnitario);
        this.id = id;
        this.oferta = oferta;
        this.quantidade = quantidade;
        this.nomeProduto = nomeProduto;
        this.descricaoProduto = descricaoProduto;
        this.precoUnitario = precoUnitario;
    }

    /**
     * Futuro DAO: recebe os dados historicos salvos, sem consultar o saldo atual.
     * Nao usar para criar compras novas. Esta separacao nao substitui autorizacao
     * no servico; dados enviados pelo cliente nao devem definir o preco da compra.
     */
    public static ItemPedido reconstituir(Long id, OfertaProduto oferta, int quantidade,
            String nomeProduto, String descricaoProduto, BigDecimal precoUnitario) {
        return new ItemPedido(id, oferta, quantidade, nomeProduto, descricaoProduto, precoUnitario);
    }

    private static void validarOferta(OfertaProduto oferta) {
        if (oferta == null) throw new IllegalArgumentException("A oferta é obrigatória.");
    }

    private static void validarQuantidade(int quantidade) {
        if (quantidade <= 0) {
            throw new IllegalArgumentException("A quantidade deve ser maior que zero.");
        }
    }

    private static void validarNomeProduto(String nomeProduto) {
        if (nomeProduto == null || nomeProduto.isBlank()) {
            throw new IllegalArgumentException("O nome histórico do produto é obrigatório.");
        }
    }

    private static void validarPreco(BigDecimal precoUnitario) {
        if (precoUnitario == null || precoUnitario.signum() < 0) {
            throw new IllegalArgumentException("O preço unitário é obrigatório e não pode ser negativo.");
        }
    }

    public Long getId() { return id; }
    // Associacoes atuais: para exibir a compra, use os getters historicos abaixo.
    public OfertaProduto getOferta() { return oferta; }
    public Produto getProduto() { return oferta.getProduto(); }
    public String getNomeProduto() { return nomeProduto; }
    public String getDescricaoProduto() { return descricaoProduto; }
    public int getQuantidade() { return quantidade; }
    public BigDecimal getPrecoUnitario() { return precoUnitario; }

    /** Quantidade vezes preco historico; nao inclui frete e nao arredonda. */
    public BigDecimal getSubtotal() {
        return precoUnitario.multiply(BigDecimal.valueOf(quantidade));
    }
    // Sem setters: os dados historicos do item nao podem ser reatribuidos.
}
