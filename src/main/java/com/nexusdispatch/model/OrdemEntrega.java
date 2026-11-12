package com.nexusdispatch.model;

import com.nexusdispatch.enums.StatusEntrega;
import com.nexusdispatch.enums.StatusPedido;
import com.nexusdispatch.enums.StatusOrdemEntrega;
import java.math.BigDecimal;
import java.util.Objects;
import java.time.LocalDateTime;

/** Entrega independente de um pedido. Exclusividade e permissoes cabem ao servico. */
public final class OrdemEntrega {
    private OrdemEntregaRegistro registro;

    /** Reconstitui a ordem da comanda correta, sem datas/endereco inexistentes. */
    public OrdemEntrega(Pedido pedido, OrdemEntregaRegistro registro, Entregador entregador) {
        Objects.requireNonNull(pedido, "Comanda obrigatoria.");
        Objects.requireNonNull(registro, "Ordem obrigatoria.");
        if (registro.id() == null || !pedido.toRegistro().id().equals(registro.idComanda()))
            throw new IllegalArgumentException("Ordem salva deve pertencer a comanda consultada.");
        // O ternario separa ordem ainda sem vinculo de ordem atribuida. Na primeira,
        // nao pode aparecer entregador; na segunda, o objeto deve ter exatamente o ID salvo.
        if (registro.idEntregador() == null ? entregador != null
                : entregador == null || !registro.idEntregador().equals(entregador.getId()))
            throw new IllegalArgumentException("Entregador nao corresponde ao vinculo salvo.");
        this.registro = registro;
        this.id = registro.id();
        this.pedido = pedido;
        this.entregador = entregador;
        this.enderecoEntrega = null;
        this.dataHoraCriacao = null;
        this.dataHoraAceite = null;
        this.dataHoraRetirada = registro.dataHoraColeta();
        this.dataHoraConclusao = registro.dataHoraEntrega();
    }

    /** Linha original, incluindo valores financeiros e estados finais do SQL. */
    public OrdemEntregaRegistro toRegistro() {
        if (registro == null) throw new IllegalStateException("Ordem local sem registro JDBC.");
        return registro;
    }

    /** Estado SQL, sem converter DEVOLVIDO/CANCELADO para estados antigos. */
    public StatusOrdemEntrega getStatusEntrega() { return toRegistro().statusEntrega(); }

    /** Modalidade gravada, sem impor um novo default na consulta. */
    public String getModalidadeFrete() { return toRegistro().modalidadeFrete(); }

    /** Distancia historica da ordem. */
    public BigDecimal getDistanciaKm() { return toRegistro().distanciaKm(); }

    /** Valor salvo para o entregador. */
    public BigDecimal getValorRepasseEntregador() { return toRegistro().valorRepasseEntregador(); }

    /** Taxa retida salva, independente da configuracao atual. */
    public BigDecimal getTaxaFreteRetida() { return toRegistro().taxaFreteRetida(); }

    /** Snapshot nao realiza transicoes locais concorrentes com o banco. */
    private void exigirModeloLocal() {
        if (registro != null) throw new IllegalStateException("Snapshot JDBC: use estados SQL e service/DAO.");
    }
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

    // Transicao apenas do modelo local: o pedido precisa avancar primeiro.
    // Se ele recusar o estado, a ordem conserva status e data anteriores.
    public void registrarRetirada() {
        exigirStatus(StatusEntrega.ACEITA);
        // Se o pedido nao estiver EM_PREPARACAO, falha antes de alterar a ordem.
        pedido.iniciarEntrega();
        dataHoraRetirada = LocalDateTime.now();
        status = StatusEntrega.EM_ROTA;
    }

    // Conclui o pedido local antes de registrar a conclusao da ordem. Em objetos
    // JDBC, exigirStatus bloqueia estas mutacoes: a coordenacao pertence ao service.
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
        exigirModeloLocal();
        if (status != esperado) throw new IllegalStateException(
                "A operação exige " + esperado + ", mas a entrega está em " + status + ".");
    }

    // No SQL, AGUARDANDO_COLETA so ocupa um entregador se houver vinculo.
    // EM_TRANSITO e trabalho em andamento; no modelo local usamos ACEITA/EM_ROTA.
    public boolean isEmAndamento() {
        if (registro != null) return registro.statusEntrega() == StatusOrdemEntrega.EM_TRANSITO
                || (registro.statusEntrega() == StatusOrdemEntrega.AGUARDANDO_COLETA && registro.idEntregador() != null);
        return status == StatusEntrega.ACEITA || status == StatusEntrega.EM_ROTA;
    }
    public Long getId() { return id; }
    public Pedido getPedido() { return pedido; }
    public Compra.EnderecoEntrega getEnderecoEntrega() { return enderecoEntrega; }
    public Entregador getEntregador() { return entregador; }
    public StatusEntrega getStatus() { exigirModeloLocal(); return status; }
    public LocalDateTime getDataHoraCriacao() { return dataHoraCriacao; }
    public LocalDateTime getDataHoraAceite() { return dataHoraAceite; }
    public LocalDateTime getDataHoraRetirada() { return dataHoraRetirada; }
    public LocalDateTime getDataHoraConclusao() { return dataHoraConclusao; }
    // Nao garante exclusividade entre ordens, concorrencia ou bloqueio de veiculo.
    // Persistencia deve coordenar as transicoes da ordem e do pedido na transacao.
}
