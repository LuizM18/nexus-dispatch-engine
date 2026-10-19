package com.nexusdispatch.model;

import com.nexusdispatch.enums.StatusEntrega;
import com.nexusdispatch.enums.StatusPedido;
import java.time.LocalDateTime;

/** Entrega independente de um pedido. Exclusividade e permissoes cabem ao servico. */
public final class OrdemEntrega {
    private Long id;
    private final Pedido pedido;
    private final Compra.EnderecoEntrega enderecoEntrega;
    private Entregador entregador;
    private final LocalDateTime dataHoraCriacao;
    private LocalDateTime dataHoraAceite;
    private LocalDateTime dataHoraRetirada;
    private LocalDateTime dataHoraConclusao;
    private StatusEntrega status;

    public OrdemEntrega(Compra compra, Pedido pedido) {
        if (compra == null || pedido == null) {
            throw new IllegalArgumentException("A compra e o pedido são obrigatórios.");
        }
        Pedido associado = null;
        for (Pedido candidato : compra.getPedidos()) {
            if (candidato == pedido || (candidato.getId() != null
                    && candidato.getId().equals(pedido.getId()))) {
                associado = candidato;
                break;
            }
        }
        if (associado == null) throw new IllegalArgumentException("O pedido deve pertencer à compra.");
        validarAntesDaRetirada(associado);
        // Usa a instancia que esta na compra. Outra instancia com o mesmo ID
        // nao deve receber transicoes enquanto a compra permanece desatualizada.
        this.pedido = associado;
        this.enderecoEntrega = compra.getEnderecoEntrega();
        this.dataHoraCriacao = LocalDateTime.now();
        this.status = StatusEntrega.AGUARDANDO_ENTREGADOR;
    }

    /** O servico deve impedir outro aceite ativo para o mesmo entregador. */
    public void aceitar(Entregador entregador) {
        exigirStatus(StatusEntrega.AGUARDANDO_ENTREGADOR);
        if (entregador == null) throw new IllegalArgumentException("O entregador é obrigatório.");
        if (!entregador.isDisponivel()) throw new IllegalStateException("O entregador não está disponível.");
        validarAntesDaRetirada(pedido);
        this.entregador = entregador;
        this.dataHoraAceite = LocalDateTime.now();
        this.status = StatusEntrega.ACEITA;
        // Disponibilidade e desejo de receber trabalho; ocupacao depende das ordens.
        // Nao mudar o boolean aqui: ele nao substitui a consulta transacional.
    }

    public void registrarRetirada() {
        exigirStatus(StatusEntrega.ACEITA);
        // Se o pedido nao estiver EM_PREPARACAO, falha antes de alterar a ordem.
        pedido.iniciarEntrega();
        dataHoraRetirada = LocalDateTime.now();
        status = StatusEntrega.EM_ROTA;
    }

    public void concluirEntrega() {
        exigirStatus(StatusEntrega.EM_ROTA);
        pedido.concluirEntrega();
        dataHoraConclusao = LocalDateTime.now();
        status = StatusEntrega.ENTREGUE;
    }

    private static void validarAntesDaRetirada(Pedido pedido) {
        if (pedido.getStatus() != StatusPedido.CONFIRMADO
                && pedido.getStatus() != StatusPedido.EM_PREPARACAO) {
            throw new IllegalStateException("O pedido não permite criar ou aceitar a entrega.");
        }
    }

    private void exigirStatus(StatusEntrega esperado) {
        if (status != esperado) throw new IllegalStateException(
                "A operação exige " + esperado + ", mas a entrega está em " + status + ".");
    }

    public boolean isEmAndamento() {
        return status == StatusEntrega.ACEITA || status == StatusEntrega.EM_ROTA;
    }
    public Long getId() { return id; }
    public Pedido getPedido() { return pedido; }
    public Compra.EnderecoEntrega getEnderecoEntrega() { return enderecoEntrega; }
    public Entregador getEntregador() { return entregador; }
    public StatusEntrega getStatus() { return status; }
    public LocalDateTime getDataHoraCriacao() { return dataHoraCriacao; }
    public LocalDateTime getDataHoraAceite() { return dataHoraAceite; }
    public LocalDateTime getDataHoraRetirada() { return dataHoraRetirada; }
    public LocalDateTime getDataHoraConclusao() { return dataHoraConclusao; }
    // Nao garante exclusividade entre ordens, concorrencia ou bloqueio de veiculo.
    // Persistencia deve coordenar as transicoes da ordem e do pedido na transacao.
}
