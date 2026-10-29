package com.nexusdispatch.dao;

import com.nexusdispatch.enums.StatusOperacaoEntregador;
import com.nexusdispatch.enums.TipoVeiculo;
import com.nexusdispatch.model.EntregadorRegistro;
import java.sql.*;
import java.util.*;

/** SQL da tabela entregador. A Connection e a transacao pertencem ao chamador. */
public final class EntregadorDao {
    private static final String COLUNAS = "e.id_entregador, e.cnh, e.tipo_veiculo, "
            + "e.status_operacao, e.bairro_operacao";
    private final Connection conexao;

    public EntregadorDao(Connection conexao) {
        this.conexao = Objects.requireNonNull(conexao, "Conexao obrigatoria.");
    }

    /** Insere o perfil complementar; o ID ja foi gerado na tabela usuario. */
    public void inserir(EntregadorRegistro entregador) throws SQLException {
        Objects.requireNonNull(entregador, "Entregador obrigatorio.");
        // Reconstrui como cadastro novo: exige bairro e inicia INATIVO.
        var novo = EntregadorRegistro.novoCadastro(entregador.id(), entregador.cnh(),
                entregador.tipoVeiculo(), entregador.bairroOperacao());
        if (entregador.statusOperacao() != novo.statusOperacao()) {
            throw new IllegalArgumentException("Novo entregador deve iniciar INATIVO.");
        }
        // INSERT SELECT tambem exige usuario ativo com perfil ENTREGADOR.
        String sql = "INSERT INTO entregador (id_entregador, cnh, tipo_veiculo, "
                + "status_operacao, bairro_operacao) "
                + "SELECT id_usuario, ?, ?, ?, ? FROM usuario "
                + "WHERE id_usuario = ? AND tipo_perfil = 'ENTREGADOR' AND status_ativo = TRUE";
        try (PreparedStatement s = conexao.prepareStatement(sql)) {
            s.setString(1, novo.cnh());
            s.setString(2, novo.tipoVeiculo().name());
            s.setString(3, novo.statusOperacao().name());
            s.setString(4, novo.bairroOperacao());
            s.setLong(5, novo.id());
            if (s.executeUpdate() != 1) {
                throw new SQLException("Usuario ativo com perfil ENTREGADOR nao encontrado.");
            }
        }
    }

    /** Optional vazio significa cadastro ausente; falhas SQL continuam sendo erros. */
    public Optional<EntregadorRegistro> buscarPorId(long id) throws SQLException {
        validarId(id);
        try (PreparedStatement s = conexao.prepareStatement(
                "SELECT " + COLUNAS + " FROM entregador e WHERE e.id_entregador = ?")) {
            s.setLong(1, id);
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? Optional.of(mapear(r)) : Optional.empty();
            }
        }
    }

    /** Altera CNH, veiculo e bairro juntos; nunca troca ID ou estado operacional. */
    public boolean atualizarCadastro(EntregadorRegistro entregador) throws SQLException {
        Objects.requireNonNull(entregador, "Entregador obrigatorio.");
        // Valida bairro obrigatorio tambem para atualizacoes feitas pelo sistema.
        EntregadorRegistro.novoCadastro(entregador.id(), entregador.cnh(),
                entregador.tipoVeiculo(), entregador.bairroOperacao());
        String sql = "UPDATE entregador SET cnh = ?, tipo_veiculo = ?, bairro_operacao = ? "
                + "WHERE id_entregador = ? AND (status_operacao IS NULL OR status_operacao <> 'EM_ROTA')";
        try (PreparedStatement s = conexao.prepareStatement(sql)) {
            s.setString(1, entregador.cnh());
            s.setString(2, entregador.tipoVeiculo().name());
            s.setString(3, entregador.bairroOperacao());
            s.setLong(4, entregador.id());
            return s.executeUpdate() > 0;
        }
    }

    /** Disponibilidade manual: nao aceita sair de EM_ROTA nem receber EM_ROTA. */
    public boolean alterarDisponibilidade(long id, StatusOperacaoEntregador esperado,
            StatusOperacaoEntregador novo) throws SQLException {
        validarId(id);
        if (esperado == null || novo == null || esperado == novo
                || esperado == StatusOperacaoEntregador.EM_ROTA
                || novo == StatusOperacaoEntregador.EM_ROTA) {
            throw new IllegalArgumentException("Informe uma troca entre INATIVO e DISPONIVEL.");
        }
        // O WHERE compara o estado atual no proprio UPDATE, evitando sobrescrever
        // uma mudanca concorrente. O service deve consultar a ordem de entrega.
        String sql = "UPDATE entregador e JOIN usuario u ON u.id_usuario = e.id_entregador "
                + "SET e.status_operacao = ? WHERE e.id_entregador = ? AND e.status_operacao = ? "
                + "AND (? = 'INATIVO' OR (u.status_ativo = TRUE AND u.tipo_perfil = 'ENTREGADOR' "
                + "AND e.bairro_operacao IS NOT NULL AND CHAR_LENGTH(TRIM(e.bairro_operacao)) > 0))";
        try (PreparedStatement s = conexao.prepareStatement(sql)) {
            s.setString(1, novo.name());
            s.setLong(2, id);
            s.setString(3, esperado.name());
            s.setString(4, novo.name());
            return s.executeUpdate() > 0;
        }
    }

    /** Bairro exato segundo a collation do MySQL; proximidade fica para o service. */
    public List<EntregadorRegistro> listarDisponiveisPorBairro(
            String bairro, int limite, int deslocamento) throws SQLException {
        if (bairro == null || bairro.isBlank()) throw new IllegalArgumentException("Bairro obrigatorio.");
        bairro = bairro.strip();
        if (bairro.codePointCount(0, bairro.length()) > 100
                || limite < 1 || limite > 100 || deslocamento < 0) {
            throw new IllegalArgumentException("Bairro ou paginacao invalida.");
        }
        String sql = "SELECT " + COLUNAS + " FROM entregador e "
                + "JOIN usuario u ON u.id_usuario = e.id_entregador "
                + "WHERE e.status_operacao = 'DISPONIVEL' AND e.bairro_operacao = ? "
                + "AND u.status_ativo = TRUE AND u.tipo_perfil = 'ENTREGADOR' "
                + "ORDER BY e.id_entregador LIMIT ? OFFSET ?";
        try (PreparedStatement s = conexao.prepareStatement(sql)) {
            s.setString(1, bairro);
            s.setInt(2, limite);
            s.setInt(3, deslocamento);
            try (ResultSet r = s.executeQuery()) {
                List<EntregadorRegistro> lista = new ArrayList<>();
                while (r.next()) lista.add(mapear(r));
                return List.copyOf(lista);
            }
        }
    }

    /** Reserva exclusiva. Usar na mesma transacao que vincula a ordem. */
    public boolean ocuparDisponivel(long id) throws SQLException {
        validarId(id);
        if (conexao.getAutoCommit()) throw new SQLException("Aceite exige transacao explicita.");
        String sql = "UPDATE entregador e JOIN usuario u ON u.id_usuario=e.id_entregador "
                + "SET e.status_operacao='EM_ROTA' WHERE e.id_entregador=? "
                + "AND e.status_operacao='DISPONIVEL' AND u.status_ativo=TRUE "
                + "AND u.tipo_perfil='ENTREGADOR' AND CHAR_LENGTH(TRIM(e.bairro_operacao))>0";
        try (PreparedStatement s = conexao.prepareStatement(sql)) {
            s.setLong(1, id);
            return s.executeUpdate() == 1;
        }
    }
    private static EntregadorRegistro mapear(ResultSet r) throws SQLException {
        try {
            String status = r.getString("status_operacao");
            return new EntregadorRegistro(r.getLong("id_entregador"), r.getString("cnh"),
                    TipoVeiculo.valueOf(r.getString("tipo_veiculo")),
                    status == null ? null : StatusOperacaoEntregador.valueOf(status),
                    r.getString("bairro_operacao"));
        } catch (IllegalArgumentException | NullPointerException erro) {
            throw new SQLException("Entregador armazenado incompativel com o modelo Java.", "22000", erro);
        }
    }

    private static void validarId(long id) {
        if (id <= 0 || id > Integer.MAX_VALUE) throw new IllegalArgumentException("ID fora da faixa INT.");
    }
}
