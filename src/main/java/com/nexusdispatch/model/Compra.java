package com.nexusdispatch.model;

import com.nexusdispatch.enums.StatusPedido;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Compra de um consumidor, com um pedido por loja e destino historico comum. */
public final class Compra {
    private Long id; // Atribuicao e reconstituicao serao integradas com o banco.
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
        BigDecimal subtotal = BigDecimal.ZERO;
        for (Pedido pedido : pedidos) subtotal = subtotal.add(pedido.getSubtotal());
        return subtotal;
    }
    // Lista fixa, mas cada Pedido pode avancar independentemente em seus estados.
    // A futura reconstituicao devera preservar ID, data e endereco originais.
}
