package com.nexusdispatch.dao;

import com.nexusdispatch.enums.StatusOperacaoEntregador;
import com.nexusdispatch.enums.TipoVeiculo;
import com.nexusdispatch.model.EntregadorRegistro;
import java.sql.*;
import java.util.*;

/**
 * Le e altera o perfil complementar na tabela entregador.
 *
 * A Connection e a transacao pertencem ao chamador; os comandos e resultados
 * criados aqui sao fechados com try-with-resources. Este DAO aplica condicoes
 * de integridade no SQL, mas autorizacao da pessoa autenticada e regras de fluxo
 * continuam no service. Metodos de aceite exigem uma transacao explicita.
 */
public final class EntregadorDao {
    // Alias e prefixo deixam explicito que as colunas pertencem a tabela entregador.
    private static final String COLUNAS = "e.id_entregador, e.cnh, e.tipo_veiculo, "
            + "e.status_operacao, e.bairro_operacao";
    private final Connection conexao;

    /** Recebe uma conexao existente para poder participar de transacoes do service. */
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
        // INSERT SELECT verifica perfil e usuario ativo no proprio banco. Assim,
        // nao confiamos apenas em dados de perfil enviados pelo chamador.
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

    /** Primeiro bloqueio de qualquer gestao operacional, igual a ordem do aceite. */
    public Optional<EntregadorRegistro> buscarPorIdParaAtualizacao(long id) throws SQLException {
        validarId(id);
        if (conexao.getAutoCommit()) throw new SQLException("Bloqueio exige transacao explicita.");
        try (PreparedStatement s = conexao.prepareStatement("SELECT " + COLUNAS + " FROM entregador e WHERE e.id_entregador=? FOR UPDATE")) {
            s.setLong(1, id);
            try (ResultSet r = s.executeQuery()) { return r.next() ? Optional.of(mapear(r)) : Optional.empty(); }
        }
    }

    /** Inativa inclusive estado antigo NULL, mas nunca EM_ROTA ou ordem ativa. */
    public boolean inativarSemOrdem(long id) throws SQLException {
        validarId(id);
        if (conexao.getAutoCommit()) throw new SQLException("Inativacao exige transacao explicita.");
        String sql = """
                UPDATE entregador e SET e.status_operacao='INATIVO'
                WHERE e.id_entregador=? AND (e.status_operacao IS NULL OR e.status_operacao<>'EM_ROTA')
                AND NOT EXISTS(SELECT 1 FROM ordem_entrega o WHERE o.id_entregador=e.id_entregador
                    AND o.status_entrega IN ('AGUARDANDO_COLETA','EM_TRANSITO'))
                """;
        try (PreparedStatement s = conexao.prepareStatement(sql)) {
            s.setLong(1, id); return s.executeUpdate() == 1;
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
        // uma mudanca concorrente. Para DISPONIVEL, exige usuario ativo, perfil e
        // bairro; o service ainda deve verificar ordens/entregas em andamento.
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
    /** Libera apenas apos a ultima ordem ativa; usuario inativo permanece INATIVO. */
    public boolean liberarSemOrdens(long id) throws SQLException {
        validarId(id);
        if (conexao.getAutoCommit()) throw new SQLException("Liberacao exige transacao explicita.");
        // CASE escolhe o estado de retorno: so usuario ativo e cadastro apto voltam
        // a DISPONIVEL. NOT EXISTS impede liberar enquanto outra ordem ainda esta ativa.
        String sql = """
                UPDATE entregador e JOIN usuario u ON u.id_usuario=e.id_entregador
                SET e.status_operacao=CASE WHEN u.status_ativo=TRUE AND u.tipo_perfil='ENTREGADOR'
                    AND CHAR_LENGTH(TRIM(e.bairro_operacao))>0 THEN 'DISPONIVEL' ELSE 'INATIVO' END
                WHERE e.id_entregador=? AND e.status_operacao='EM_ROTA'
                AND NOT EXISTS(SELECT 1 FROM ordem_entrega o WHERE o.id_entregador=e.id_entregador
                    AND o.status_entrega IN ('AGUARDANDO_COLETA','EM_TRANSITO'))
                """;
        try (PreparedStatement s = conexao.prepareStatement(sql)) {
            s.setLong(1, id); return s.executeUpdate() == 1;
        }
    }

    /** Converte a linha e preserva status_operacao SQL NULL quando existente. */
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

    /** Confere a faixa INT positiva usada pelas chaves do banco. */
    private static void validarId(long id) {
        if (id <= 0 || id > Integer.MAX_VALUE) throw new IllegalArgumentException("ID fora da faixa INT.");
    }
}
