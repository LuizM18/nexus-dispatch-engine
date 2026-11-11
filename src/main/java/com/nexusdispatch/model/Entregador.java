package com.nexusdispatch.model;

import com.nexusdispatch.enums.TipoVeiculo;
import com.nexusdispatch.enums.CategoriaCnh;
import com.nexusdispatch.enums.PerfilUsuario;
import com.nexusdispatch.enums.StatusOperacaoEntregador;
import java.util.Objects;

/**
 * Usuario responsavel pelas entregas, com CNH conforme o veiculo; bicicleta dispensa CNH.
 * VAN representa van de carga a combustao, sem reboque, com ate oito passageiros.
 * Essas caracteristicas ainda precisam de verificacao no futuro cadastro.
 * Dados pessoais sao herdados de Usuario; disponibilidade e ativada manualmente.
 */
public class Entregador extends Usuario {
    /** Identidade do perfil, independente do estado operacional de entrega. */
    @Override
    public PerfilUsuario getPerfil() { return PerfilUsuario.ENTREGADOR; }

    private TipoVeiculo tipoVeiculo;
    private CategoriaCnh categoriaCnh;
    // Integer permite null: estes dados se aplicam somente a VAN nesta versao.
    private Integer pesoBrutoTotalKg;
    private Integer capacidadePassageiros;
    // Estado operacional do SQL, incluindo EM_ROTA e NULL nas consultas antigas.
    private StatusOperacaoEntregador statusOperacao;
    private EntregadorRegistro registro;

    /**
     * Reconstroi snapshot JDBC sem inventar categoria da CNH, PBT ou passageiros.
     * Esses dados nao estao no SQL; ausencia nao comprova elegibilidade do veiculo.
     */
    public Entregador(UsuarioRegistro usuario, EntregadorRegistro complemento) {
        super(usuario, PerfilUsuario.ENTREGADOR);
        Objects.requireNonNull(complemento, "Complemento do entregador obrigatorio.");
        if (usuario.id() != complemento.id())
            throw new IllegalArgumentException("Usuario e entregador devem ter o mesmo ID.");
        this.registro = complemento;
        this.tipoVeiculo = complemento.tipoVeiculo();
        this.statusOperacao = complemento.statusOperacao();
    }

    /** Estado real, preservando NULL do SQL; nao converter ausencia em DISPONIVEL. */
    public StatusOperacaoEntregador getStatusOperacao() { return statusOperacao; }

    /** Numero da CNH; nao confundir com getCategoriaCnh(). */
    public String getCnh() { return registro == null ? null : registro.cnh(); }

    /** Bairro textual do SQL, sem criar um cadastro Bairro ficticio. */
    public String getBairroOperacao() { return registro == null ? null : registro.bairroOperacao(); }

    /** Snapshot do complemento salvo; modelo local antigo nao possui esse contrato. */
    public EntregadorRegistro toEntregadorRegistro() {
        if (registro == null) throw new IllegalStateException("Entregador local sem complemento persistido.");
        return registro;
    }

    /** Impede simular alteracao persistente fora do service e de sua transacao. */
    private void exigirModeloLocal() {
        if (registro != null)
            throw new IllegalStateException("Snapshot JDBC: altere pelo service/DAO e consulte novamente.");
    }

    public Entregador(String nome, String email, String telefone, String senhaHash,
            TipoVeiculo tipoVeiculo, CategoriaCnh categoriaCnh,
            Integer pesoBrutoTotalKg, Integer capacidadePassageiros) {
        // Inicializa e valida os dados comuns na superclasse antes dos especificos.
        super(nome, email, telefone, senhaHash);
        definirVeiculoECnh(tipoVeiculo, categoriaCnh, pesoBrutoTotalKg, capacidadePassageiros);
        // Comeca indisponivel; o cadastro nao o libera automaticamente para entregas.
        this.statusOperacao = StatusOperacaoEntregador.INATIVO;
    }

    // Getter booleano: consulta o estado sem modifica-lo.
    public boolean isDisponivel() {
        return statusOperacao == StatusOperacaoEntregador.DISPONIVEL
                && (registro == null || isAtivo());
    }

    public void ficarDisponivel() {
        exigirModeloLocal();
        // Repetir esta operacao mantem true, sem inverter o estado.
        this.statusOperacao = StatusOperacaoEntregador.DISPONIVEL;
    }

    public void ficarIndisponivel() {
        exigirModeloLocal();
        // Afeta apenas a disponibilidade; nao cancela entregas existentes.
        this.statusOperacao = StatusOperacaoEntregador.INATIVO;
    }

    // Estes metodos nao consultam entregas nem autenticam o usuario.
    // O servico devera conferir permissoes, ordens em andamento e elegibilidade
    // antes de aceitar uma entrega. Disponivel nao significa automaticamente apto.

    public TipoVeiculo getTipoVeiculo() {
        return tipoVeiculo;
    }

    public CategoriaCnh getCategoriaCnh() {
        return categoriaCnh;
    }

    public Integer getPesoBrutoTotalKg() {
        return pesoBrutoTotalKg;
    }

    public Integer getCapacidadePassageiros() {
        return capacidadePassageiros;
    }

    // Substitui setters separados para impedir combinacoes inconsistentes.
    public void atualizarVeiculoECnh(TipoVeiculo tipoVeiculo, CategoriaCnh categoriaCnh,
            Integer pesoBrutoTotalKg, Integer capacidadePassageiros) {
        exigirModeloLocal();
        definirVeiculoECnh(tipoVeiculo, categoriaCnh, pesoBrutoTotalKg, capacidadePassageiros);
    }

    // Construtor e atualizacao compartilham a mesma validacao privada.
    private void definirVeiculoECnh(TipoVeiculo tipoVeiculo, CategoriaCnh categoriaCnh,
            Integer pesoBrutoTotalKg, Integer capacidadePassageiros) {
        validarVeiculoECnh(tipoVeiculo, categoriaCnh, pesoBrutoTotalKg, capacidadePassageiros);

        // So altera apos validar tudo; uma excecao preserva os valores anteriores.
        this.tipoVeiculo = tipoVeiculo;
        this.categoriaCnh = categoriaCnh;
        this.pesoBrutoTotalKg = pesoBrutoTotalKg;
        this.capacidadePassageiros = capacidadePassageiros;
    }

    private void validarVeiculoECnh(TipoVeiculo tipoVeiculo, CategoriaCnh categoriaCnh,
            Integer pesoBrutoTotalKg, Integer capacidadePassageiros) {
        if (tipoVeiculo == null) {
            throw new IllegalArgumentException("O tipo de veículo é obrigatório.");
        }

        // Ao sair de VAN, tambem e necessario remover seus dados especificos.
        if (tipoVeiculo != TipoVeiculo.VAN
                && (pesoBrutoTotalKg != null || capacidadePassageiros != null)) {
            throw new IllegalArgumentException(
                    "Peso bruto total e capacidade de passageiros devem ser informados somente para van.");
        }

        // Comparar enums com == e seguro mesmo quando categoriaCnh e null.
        boolean permiteMoto = categoriaCnh == CategoriaCnh.A
                || categoriaCnh == CategoriaCnh.AB || categoriaCnh == CategoriaCnh.AC;
        boolean permiteCategoriaB = categoriaCnh == CategoriaCnh.B
                || categoriaCnh == CategoriaCnh.AB || categoriaCnh == CategoriaCnh.C
                || categoriaCnh == CategoriaCnh.AC;
        boolean permiteCategoriaC = categoriaCnh == CategoriaCnh.C
                || categoriaCnh == CategoriaCnh.AC;

        switch (tipoVeiculo) {
            case BICICLETA -> {
                // Bicicleta aceita qualquer categoria do enum ou null.
            }
            case MOTO -> {
                if (!permiteMoto) {
                    throw new IllegalArgumentException("Para moto, informe CNH A, AB ou AC.");
                }
            }
            case CARRO -> {
                // CARRO representa um automovel enquadrado na categoria B.
                if (!permiteCategoriaB) {
                    throw new IllegalArgumentException("Para carro, informe CNH B, AB, C ou AC.");
                }
            }
            case VAN -> {
                // PBT e o total permitido do veiculo, nao o peso da entrega atual.
                // || evita comparar numericamente um Integer null.
                if (pesoBrutoTotalKg == null || pesoBrutoTotalKg <= 0) {
                    throw new IllegalArgumentException("Informe o PBT da van em kg, maior que zero.");
                }
                // Capacidade exclui o motorista; zero e oito sao aceitos.
                if (capacidadePassageiros == null || capacidadePassageiros < 0
                        || capacidadePassageiros > 8) {
                    throw new IllegalArgumentException(
                            "A van deve permitir de zero a oito passageiros, sem contar o motorista.");
                }
                if (pesoBrutoTotalKg <= 3500) {
                    if (!permiteCategoriaB) {
                        throw new IllegalArgumentException("Para van até 3.500 kg, informe CNH B, AB, C ou AC.");
                    }
                } else if (!permiteCategoriaC) {
                    throw new IllegalArgumentException("Para van de carga acima de 3.500 kg, informe CNH C ou AC.");
                }
            }
            default -> throw new IllegalArgumentException("Tipo de veículo ainda não suportado.");
        }
    }
}
