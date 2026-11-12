package com.nexusdispatch.model;

import com.nexusdispatch.enums.StatusPedido;
import com.nexusdispatch.enums.StatusComanda;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.HashSet;
import java.util.Objects;

/** Uma compra pode reunir varios pedidos, mas cada pedido pertence a uma loja. */
public final class Pedido {
    private ComandaLojistaRegistro registro;

    /**
     * Pedido por loja corresponde a comanda_lojista. Recebe TODOS os itens, nao
     * uma pagina, para conferir vinculos, repeticoes e total historico.
     */
    public Pedido(Loja loja, ComandaLojistaRegistro registro, List<ItemPedido> itens) {
        Objects.requireNonNull(loja, "Loja obrigatoria.");
        Objects.requireNonNull(registro, "Comanda obrigatoria.");
        if (registro.id() == null || loja.getId() == null || loja.getId() != registro.idLojista()
                || itens == null || itens.isEmpty())
            throw new IllegalArgumentException("Comanda salva exige loja correta e itens completos.");
        List<ItemPedido> copia = List.copyOf(itens);
        // A lista deve conter todos os itens da comanda. Conjuntos recusam IDs ou
        // produtos repetidos; somar os precos historicos detecta lista incompleta/divergente.
        var produtos = new HashSet<Long>();
        var ids = new HashSet<Long>();
        BigDecimal total = BigDecimal.ZERO;
        for (ItemPedido item : copia) {
            ItemPedidoRegistro dados = item.toRegistro();
            if (dados.idComanda() != registro.id() || !ids.add(dados.id())
                    || !produtos.add(dados.idProduto()) || !mesmaLoja(loja, item.getOferta().getLoja()))
                throw new IllegalArgumentException("Item duplicado ou de outra comanda/loja.");
            total = total.add(item.getSubtotal());
        }
        if (total.compareTo(registro.valorProdutos()) != 0)
            throw new IllegalArgumentException("Itens incompletos ou total divergente da comanda.");
        this.registro = registro;
        this.id = registro.id();
        this.loja = loja;
        this.itens = copia;
        // SQL so tem data do estado, atualizada com ele; nao e data de criacao.
        this.dataHoraCriacao = null;
    }

    /** Registro da comanda original, com valores e estado sem traducao com perda. */
    public ComandaLojistaRegistro toRegistro() {
        if (registro == null) throw new IllegalStateException("Pedido local sem comanda JDBC.");
        return registro;
    }

    /** Estado SQL inclui espera de coleta/cancelamento, ausentes no enum antigo. */
    public StatusComanda getStatusComanda() { return toRegistro().statusComanda(); }

    /** Data real do status; pode ser NULL no banco. */
    public LocalDateTime getDataHoraStatus() { return toRegistro().dataHoraStatus(); }

    /** FK para a compra completa. */
    public long getIdPedidoMestre() { return toRegistro().idPedido(); }

    /** Comissao monetaria salva, sem recalcular pela taxa atual do lojista. */
    public BigDecimal getTaxaVendaRetida() { return toRegistro().taxaVendaRetida(); }

    /** Repasse historico salvo. */
    public BigDecimal getRepasseLojista() { return toRegistro().repasseLojista(); }

    /** Snapshot nao modifica estados; o service deve coordenar SQL e entregas. */
    private void exigirModeloLocal() {
        if (registro != null) throw new IllegalStateException("Snapshot JDBC: use estados SQL e service/DAO.");
    }
    // O construtor local deixa ID null; o JDBC recupera a chave de comanda_lojista.
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
            if (item.getIdComanda() != null)
                throw new IllegalArgumentException("Itens JDBC exigem o construtor com ComandaLojistaRegistro.");
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

    // No modelo local, duas ofertas podem apontar ao mesmo produto. Compara
    // referencias e IDs presentes para impedir duplicidade comercial no pedido.
    private static boolean mesmaOferta(OfertaProduto primeira, OfertaProduto segunda) {
        if (primeira == segunda) return true;
        if (primeira == null || segunda == null) return false;
        Produto a = primeira.getProduto(), b = segunda.getProduto();
        if (a == b || (a.getId() != null && a.getId().equals(b.getId()))) return true;
        return primeira.getId() != null && primeira.getId().equals(segunda.getId());
    }

    public Long getId() { return id; }
    public Loja getLoja() { return loja; }
    public List<ItemPedido> getItens() { return itens; }
    public LocalDateTime getDataHoraCriacao() { return dataHoraCriacao; }
    public StatusPedido getStatus() { exigirModeloLocal(); return status; }

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
        exigirModeloLocal();
        if (status != esperado) {
            // A atribuicao nao acontece em caso de erro: preserva o estado anterior.
            throw new IllegalStateException("Não é possível mudar de " + status + " para " + novo + ".");
        }
        status = novo;
    }
    // Sem setters: itens e loja nao mudam, e estados seguem apenas a sequencia.
    // Permissoes, concorrencia e atribuicao de entregador pertencem aos servicos.
    // Cada pedido tera sua OrdemEntrega; nao exige entregadores distintos por compra.
    // A reconstituicao usa ComandaLojistaRegistro e preserva ID, data do status e estado SQL.
}
