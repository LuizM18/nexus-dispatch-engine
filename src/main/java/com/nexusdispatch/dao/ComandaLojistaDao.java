package com.nexusdispatch.dao;

import com.nexusdispatch.enums.StatusComanda;
import com.nexusdispatch.model.ComandaLojistaRegistro;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Grava e consulta a tabela comanda_lojista usando a conexao do chamador.
 * O service controla permissoes, pagamento, calculos, duplicidade por pedido/loja
 * e a transacao que coordena itens, estoque e entregas.
 * O DAO fecha comandos/resultados, mas nao fecha a Connection nem faz commit.
 */
public final class ComandaLojistaDao {
    // Colunas reais reutilizadas pelos SELECTs; nenhuma entrada externa aqui.
    private static final String COLUNAS = "id_comanda, id_pedido, id_lojista, valor_produtos, "
            + "taxa_venda_retida, repasse_lojista, status_comanda, data_hora_status";
    private final Connection conexao;

    /** A mesma Connection permite compartilhar uma transacao com os demais DAOs. */
    public ComandaLojistaDao(Connection conexao) {
        this.conexao = Objects.requireNonNull(conexao, "A conexao e obrigatoria.");
    }

    /** Insere uma comanda nova e retorna outro registro com ID e data do banco. */
    public ComandaLojistaRegistro inserir(ComandaLojistaRegistro comanda) throws SQLException {
        if (comanda == null) throw new IllegalArgumentException("A comanda e obrigatoria.");
        if (comanda.id() != null) throw new IllegalArgumentException("Comanda nova deve ter ID null.");
        if (comanda.dataHoraStatus() != null) {
            throw new IllegalArgumentException("A data inicial sera gerada pelo banco.");
        }
        if (comanda.statusComanda() != StatusComanda.PAGO) {
            throw new IllegalArgumentException("Comanda nova deve comecar em PAGO.");
        }
        // O record ja validou valores. ID/data omitidos usam os padroes do banco.
        exigirTransacao();
        String sql = "INSERT INTO comanda_lojista (id_pedido, id_lojista, valor_produtos, "
                + "taxa_venda_retida, repasse_lojista, status_comanda) VALUES (?, ?, ?, ?, ?, ?)";
        long idGerado;
        try (PreparedStatement comando = conexao.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            // Indices seguem a ordem dos ?. Parametros separam valores do SQL;
            // BigDecimal preserva valores monetarios sem conversao para double.
            comando.setLong(1, comanda.idPedido());
            comando.setLong(2, comanda.idLojista());
            comando.setBigDecimal(3, comanda.valorProdutos());
            comando.setBigDecimal(4, comanda.taxaVendaRetida());
            comando.setBigDecimal(5, comanda.repasseLojista());
            comando.setString(6, comanda.statusComanda().name());
            if (comando.executeUpdate() != 1) throw new SQLException("Comanda nao inserida.");
            try (ResultSet chaves = comando.getGeneratedKeys()) {
                if (!chaves.next()) throw new SQLException("O banco nao retornou o ID da comanda.");
                idGerado = chaves.getLong(1);
            }
        }
        // Recupera a data efetivamente gravada. O service deve usar transacao
        // para reverter a insercao se esta leitura ou outra etapa falhar.
        return buscarPorId(idGerado).orElseThrow(
                () -> new SQLException("Comanda inserida, mas nao encontrada na leitura."));
    }

    /** Optional vazio indica ausencia; falhas SQL continuam sendo excecoes. */
    public Optional<ComandaLojistaRegistro> buscarPorId(long idComanda) throws SQLException {
        validarId(idComanda);
        String sql = "SELECT " + COLUNAS + " FROM comanda_lojista WHERE id_comanda = ?";
        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setLong(1, idComanda);
            try (ResultSet resultado = comando.executeQuery()) {
                return resultado.next() ? Optional.of(mapear(resultado)) : Optional.empty();
            }
        }
    }

    /** Recupera as partes de uma compra, uma por loja conforme a regra do service. */
    public List<ComandaLojistaRegistro> listarPorPedido(long idPedido) throws SQLException {
        validarId(idPedido);
        String sql = "SELECT " + COLUNAS
                + " FROM comanda_lojista WHERE id_pedido = ? ORDER BY id_comanda";
        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setLong(1, idPedido);
            try (ResultSet resultado = comando.executeQuery()) {
                List<ComandaLojistaRegistro> comandas = new ArrayList<>();
                while (resultado.next()) comandas.add(mapear(resultado));
                return List.copyOf(comandas);
            }
        }
    }

    /**
     * Lista uma pagina por lojista; o service deve autorizar o acesso.
     * Usa ID decrescente: data_hora_status muda e nao e uma data de criacao.
     * limite define tamanho da pagina; deslocamento define quantas linhas pular.
     */
    public List<ComandaLojistaRegistro> listarPorLojista(long idLojista, int limite, int deslocamento)
            throws SQLException {
        validarId(idLojista);
        if (limite < 1 || limite > 100 || deslocamento < 0) {
            throw new IllegalArgumentException("Limite deve ser de 1 a 100 e deslocamento nao negativo.");
        }
        String sql = "SELECT " + COLUNAS + " FROM comanda_lojista WHERE id_lojista = ? "
                + "ORDER BY id_comanda DESC LIMIT ? OFFSET ?";
        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setLong(1, idLojista);
            comando.setInt(2, limite);
            comando.setInt(3, deslocamento);
            try (ResultSet resultado = comando.executeQuery()) {
                List<ComandaLojistaRegistro> comandas = new ArrayList<>();
                while (resultado.next()) comandas.add(mapear(resultado));
                // Lista vazia se nao houver resultados; copia impede adicionar/remover.
                return List.copyOf(comandas);
            }
        }
    }

    /**
     * Exige transicao permitida pelo enum e estado esperado ainda presente no banco.
     * O service coordena os efeitos nos demais registros e verifica permissoes.
     */
    public boolean atualizarStatus(long idComanda, StatusComanda statusEsperado,
            StatusComanda novoStatus) throws SQLException {
        validarId(idComanda);
        if (statusEsperado == null || novoStatus == null) {
            throw new IllegalArgumentException("Os estados esperado e novo sao obrigatorios.");
        }
        if (!statusEsperado.permiteTransicaoPara(novoStatus)) {
            throw new IllegalArgumentException("Transicao de estado nao permitida.");
        }
        // Verifica e altera no mesmo UPDATE, evitando sobrescrever mudanca concorrente.
        exigirTransacao();
        String sql = "UPDATE comanda_lojista SET status_comanda = ? "
                + "WHERE id_comanda = ? AND status_comanda = ?";
        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, novoStatus.name());
            comando.setLong(2, idComanda);
            comando.setString(3, statusEsperado.name());
            // MySQL atualiza data_hora_status. false indica ID ausente/estado divergente.
            return comando.executeUpdate() == 1;
        }
    }

    /** Usa o construtor para preservar o estado armazenado; novaComanda iniciaria PAGO. */
    private static ComandaLojistaRegistro mapear(ResultSet resultado) throws SQLException {
        Timestamp data = resultado.getTimestamp("data_hora_status");
        try {
            return new ComandaLojistaRegistro(resultado.getLong("id_comanda"),
                    resultado.getLong("id_pedido"), resultado.getLong("id_lojista"),
                    resultado.getBigDecimal("valor_produtos"), resultado.getBigDecimal("taxa_venda_retida"),
                    resultado.getBigDecimal("repasse_lojista"),
                    StatusComanda.valueOf(resultado.getString("status_comanda")),
                    data == null ? null : data.toLocalDateTime());
        } catch (IllegalArgumentException | NullPointerException erro) {
            // Dados antigos invalidos sao erro de leitura; a causa e preservada.
            throw new SQLException("Comanda armazenada incompativel com o modelo Java.", "22000", erro);
        }
    }

    /** Confere faixa numerica; existencia do registro depende do banco. */
    private static void validarId(long id) {
        if (id <= 0 || id > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("O ID deve estar na faixa INT positiva.");
        }
    }
    /** Escritas participam da transacao controlada pelo service. */
    private void exigirTransacao() throws SQLException {
        if (conexao.getAutoCommit()) {
            throw new SQLException("A escrita exige transacao explicita.");
        }
    }

}
