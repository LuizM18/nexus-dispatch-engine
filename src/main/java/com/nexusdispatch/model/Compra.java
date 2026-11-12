package com.nexusdispatch.model;

import com.nexusdispatch.enums.StatusPedido;
import com.nexusdispatch.enums.StatusPedidoMestre;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.HashSet;
import java.util.Objects;

/** Compra de um consumidor, com um pedido por loja e destino historico comum. */
public final class Compra {
    private PedidoMestreRegistro registro;

    /**
     * Compra corresponde a pedido_mestre. Recebe comandas completas e consumidor
     * correto. Pedido CRIADO/cancelado sem comandas pode nao ter itens salvos.
     */
    public Compra(Consumidor consumidor, PedidoMestreRegistro registro, List<Pedido> pedidos) {
        Objects.requireNonNull(consumidor, "Consumidor obrigatorio.");
        Objects.requireNonNull(registro, "Pedido mestre obrigatorio.");
        if (registro.id() == null || consumidor.getId() == null || consumidor.getId() != registro.idCliente()
                || pedidos == null)
            throw new IllegalArgumentException("Compra salva exige identidade e cliente correspondente.");
        List<Pedido> copia = List.copyOf(pedidos);
        // Conjuntos detectam repeticao: add retorna false quando o ID ja foi visto.
        // Conferimos tanto a identidade da comanda quanto a regra de uma comanda por loja.
        var lojas = new HashSet<Long>();
        var ids = new HashSet<Long>();
        BigDecimal total = BigDecimal.ZERO;
        for (Pedido pedido : copia) {
            ComandaLojistaRegistro comanda = pedido.toRegistro();
            if (comanda.idPedido() != registro.id() || !ids.add(comanda.id()) || !lojas.add(comanda.idLojista()))
                throw new IllegalArgumentException("Comanda duplicada ou de outra compra.");
            total = total.add(comanda.valorProdutos());
        }
        // Comandas fornecidas precisam somar o valor historico dos produtos. PAGO e
        // CONCLUIDO exigem partes completas; CRIADO/CANCELADO podem existir sem itens,
        // como ocorre com uma compra cujo pagamento foi recusado.
        if ((!copia.isEmpty() && total.compareTo(registro.valorProdutos()) != 0)
                || (copia.isEmpty() && (registro.statusPedido() == StatusPedidoMestre.PAGO
                || registro.statusPedido() == StatusPedidoMestre.CONCLUIDO)))
            throw new IllegalArgumentException("Comandas incompletas ou total divergente da compra.");
        this.registro = registro;
        this.id = registro.id();
        this.consumidor = consumidor;
        this.pedidos = copia;
        this.dataHoraCriacao = registro.dataCriacao();
        // O SQL nao preserva endereco de entrega do cliente.
        this.enderecoEntrega = null;
    }

    /** Dados reais do pedido mestre; modelo local nao inventa totais/estado. */
    public PedidoMestreRegistro toRegistro() {
        if (registro == null) throw new IllegalStateException("Compra local sem pedido mestre JDBC.");
        return registro;
    }

    /** Estado agregado da compra, distinto do estado de cada comanda. */
    public StatusPedidoMestre getStatusPedidoMestre() { return toRegistro().statusPedido(); }

    /** Total cobrado do cliente, incluindo a taxa salva na compra. */
    public BigDecimal getValorTotal() { return toRegistro().valorTotal(); }

    /** Taxa salva, sem aplicar novamente o percentual configurado hoje. */
    public BigDecimal getValorTaxaCompra() { return toRegistro().valorFretes(); }
    private Long id; // null no construtor local; ID do pedido mestre na reconstituicao JDBC.
    private final Consumidor consumidor;
    private final List<Pedido> pedidos;
    private final LocalDateTime dataHoraCriacao;
    private final EnderecoEntrega enderecoEntrega;

    // Strings sao imutaveis. O record nao conserva referencias ao Endereco ou Bairro.
    public record EnderecoEntrega(String cep, String logradouro, String numero,
            String complemento, String bairro, String cidade) { }

    /** O servico deve aprovar o pagamento e coordenar estoque e persistencia. */
    public Compra(Consumidor consumidor, List<Pedido> pedidos) {
        if (consumidor == null) throw new IllegalArgumentException("O consumidor é obrigatório.");
        if (pedidos == null || pedidos.isEmpty()) {
            throw new IllegalArgumentException("A compra deve possuir pelo menos um pedido.");
        }
        for (Pedido pedido : pedidos) {
            if (pedido == null) throw new IllegalArgumentException("A compra não pode conter pedidos nulos.");
        }
        List<Pedido> copia = List.copyOf(pedidos);
        for (int i = 0; i < copia.size(); i++) {
            Pedido atual = copia.get(i);
            // Este construtor cria compras novas, nao recupera historico do banco.
            if (atual.getStatus() != StatusPedido.CONFIRMADO) {
                throw new IllegalArgumentException("Uma compra nova exige pedidos confirmados ainda não preparados.");
            }
            for (int j = 0; j < i; j++) {
                if (mesmaLoja(atual.getLoja(), copia.get(j).getLoja())) {
                    throw new IllegalArgumentException("A compra deve possuir somente um pedido por loja.");
                }
            }
        }
        Endereco endereco = consumidor.getEndereco();
        if (endereco == null || endereco.getBairro() == null) {
            throw new IllegalArgumentException("O endereço e o bairro são obrigatórios.");
        }
        Bairro bairro = endereco.getBairro();
        this.enderecoEntrega = new EnderecoEntrega(endereco.getCep(), endereco.getLogradouro(),
                endereco.getNumero(), endereco.getComplemento(), bairro.getNome(), bairro.getCidade());
        this.consumidor = consumidor;
        this.pedidos = copia;
        this.dataHoraCriacao = LocalDateTime.now();
    }

    // == reconhece a mesma instancia local. Com ID presente, equals permite
    // reconhecer duas representacoes da mesma loja sem igualar objetos de ID null.
    private static boolean mesmaLoja(Loja primeira, Loja segunda) {
        return primeira == segunda || (primeira.getId() != null
                && primeira.getId().equals(segunda.getId()));
    }

    public Long getId() { return id; }
    public Consumidor getConsumidor() { return consumidor; }
    public List<Pedido> getPedidos() { return pedidos; }
    public LocalDateTime getDataHoraCriacao() { return dataHoraCriacao; }
    public EnderecoEntrega getEnderecoEntrega() { return enderecoEntrega; }

    /** Apenas produtos, sem frete, comissao ou desconto. */
    public BigDecimal getSubtotal() {
        if (registro != null) return registro.valorProdutos();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (Pedido pedido : pedidos) subtotal = subtotal.add(pedido.getSubtotal());
        return subtotal;
    }
    // Lista fixa, mas cada Pedido pode avancar independentemente em seus estados.
    // A reconstituicao preserva ID/data SQL; endereco fica null porque o esquema nao o guarda.
}
