package com.nexusdispatch.model;

import java.math.BigDecimal;

/** Item comprado com nome, descricao e preco preservados no momento da criacao. */
public final class ItemPedido {
    private ItemPedidoRegistro registro;

    /**
     * Le o item salvo sem exigir estoque atual. O SQL preserva preco/observacao,
     * mas nao nome/descricao historicos: esses campos ficam desconhecidos.
     */
    public ItemPedido(OfertaProduto oferta, ItemPedidoRegistro registro) {
        validarOferta(oferta);
        if (registro == null || registro.id() == null || !oferta.getProduto().isPersistido()
                || !oferta.getProduto().getId().equals(registro.idProduto()))
            throw new IllegalArgumentException("Item salvo deve corresponder ao produto consultado.");
        this.registro = registro;
        this.id = registro.id();
        this.oferta = oferta;
        this.quantidade = registro.quantidade();
        this.precoUnitario = registro.precoUnitarioAplicado();
        // O SQL preserva preco e observacao, mas nao os textos da data da venda.
        // null evita apresentar o nome atual como se fosse historico; os getters
        // getNomeProdutoAtual/getDescricaoProdutoAtual consultam explicitamente o catalogo.
        this.nomeProduto = null;
        this.descricaoProduto = null;
    }

    /** Dados historicos realmente armazenados, sem nomes atuais disfarçados de historico. */
    public ItemPedidoRegistro toRegistro() {
        if (registro == null) throw new IllegalStateException("Item local sem registro JDBC.");
        return registro;
    }

    /** FK da comanda; null no modelo local antigo. */
    public Long getIdComanda() { return registro == null ? null : registro.idComanda(); }

    /** Observacao salva; null tambem pode significar ausencia no banco. */
    public String getObservacao() { return registro == null ? null : registro.observacao(); }

    /** Nome do cadastro consultado agora; nao e nome historico da venda. */
    public String getNomeProdutoAtual() { return getProduto().getNome(); }

    /** Descricao do cadastro atual, separada da descricao historica indisponivel. */
    public String getDescricaoProdutoAtual() { return getProduto().getDescricao(); }

    /** Distingue o historico local antigo de leitura SQL que nao guarda esses textos. */
    public boolean possuiTextosHistoricos() { return registro == null; }
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
        if (oferta.getProduto().isPersistido())
            throw new IllegalArgumentException("Para item JDBC use o construtor com ItemPedidoRegistro.");
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
     * Reconstituicao do modelo local com textos historicos, sem consultar saldo atual.
     * Nao usar para criar compras novas. Esta separacao nao substitui autorizacao
     * no servico; dados enviados pelo cliente nao devem definir o preco da compra.
     */
    public static ItemPedido reconstituir(Long id, OfertaProduto oferta, int quantidade,
            String nomeProduto, String descricaoProduto, BigDecimal precoUnitario) {
        if (oferta != null && oferta.getProduto().isPersistido())
            throw new IllegalArgumentException("Textos historicos nao existem no SQL: use ItemPedidoRegistro.");
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
