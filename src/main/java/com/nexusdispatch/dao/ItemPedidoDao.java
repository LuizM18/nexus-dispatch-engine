package com.nexusdispatch.dao;

import com.nexusdispatch.model.ItemPedidoRegistro;
import java.sql.*;
import java.util.*;

/** SQL de item_pedido. O chamador controla conexao, transacao e autorizacao. */
public final class ItemPedidoDao {
    private static final String COLUNAS = "id_item, id_comanda, id_produto, quantidade, "
            + "preco_unitario_aplicado, observacao";
    // Conexao emprestada pelo service: este DAO fecha apenas comandos/resultados.
    // A mesma Connection permite que suas escritas sejam revertidas junto das demais tabelas.
    private final Connection conexao;

    // Recebe a conexao ja aberta. requireNonNull acusa erro de configuracao antes
    // de qualquer SQL; criar o DAO nao inicia nem confirma uma transacao.
    public ItemPedidoDao(Connection conexao) {
        this.conexao = Objects.requireNonNull(conexao, "A conexao e obrigatoria.");
    }

    /** Retorna outro record com ID; nao altera a entrada, o estoque ou os totais. */
    public ItemPedidoRegistro inserir(ItemPedidoRegistro item) throws SQLException {
        Objects.requireNonNull(item, "O item e obrigatorio.");
        if (item.id() != null) throw new IllegalArgumentException("Item novo deve estar sem ID.");
        exigirTransacao();
        // Condicoes verificadas no INSERT: produto ativo do lojista da comanda,
        // ainda PAGO. O service coordena preco, estoque e disputas de estado.
        String sql = """
                INSERT INTO item_pedido
                    (id_comanda, id_produto, quantidade, preco_unitario_aplicado, observacao)
                SELECT c.id_comanda, p.id_produto, ?, ?, ?
                FROM comanda_lojista c
                JOIN produto p ON p.id_lojista = c.id_lojista
                WHERE c.id_comanda = ? AND p.id_produto = ?
                  AND c.status_comanda = 'PAGO' AND p.status_ativo = TRUE
                """;
        // INSERT SELECT insere somente se a consulta encontrar o par produto/comanda
        // permitido. Os indices JDBC comecam em 1 e seguem os ? na ordem do texto SQL.
        try (PreparedStatement s = conexao.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            s.setInt(1, item.quantidade());
            s.setBigDecimal(2, item.precoUnitarioAplicado());
            // NULL explicito evita o DEFAULT textual 'Nenhuma', sem mudar o SQL.
            if (item.observacao() == null) s.setNull(3, Types.VARCHAR);
            else s.setString(3, item.observacao());
            s.setLong(4, item.idComanda());
            s.setLong(5, item.idProduto());
            if (s.executeUpdate() != 1) {
                throw new SQLException("Item nao inserido: confira produto ativo, lojista e comanda PAGO.");
            }
            // A chave gerada vem em outro ResultSet. next avanca ate a primeira linha;
            // wasNull distingue SQL NULL do zero devolvido por getLong nesse caso.
            try (ResultSet r = s.getGeneratedKeys()) {
                if (!r.next()) throw new SQLException("O banco nao retornou o ID do item.");
                long id = r.getLong(1);
                if (r.wasNull() || id < 1 || id > Integer.MAX_VALUE) {
                    throw new SQLException("ID de item gerado invalido.");
                }
                return new ItemPedidoRegistro(id, item.idComanda(), item.idProduto(),
                        item.quantidade(), item.precoUnitarioAplicado(), item.observacao());
            }
        }
    }

    /** Ausencia vira Optional vazio; erros JDBC sao propagados. */
    public Optional<ItemPedidoRegistro> buscarPorId(long id) throws SQLException {
        validarId(id);
        try (PreparedStatement s = conexao.prepareStatement(
                "SELECT " + COLUNAS + " FROM item_pedido WHERE id_item = ?")) {
            s.setLong(1, id);
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? Optional.of(mapear(r)) : Optional.empty();
            }
        }
    }

    /** Preserva historico mesmo se o produto for desativado. Acesso cabe ao service. */
    public List<ItemPedidoRegistro> listarPorComanda(long idComanda, int limite, int deslocamento)
            throws SQLException {
        validarId(idComanda);
        if (limite < 1 || limite > 100 || deslocamento < 0) {
            throw new IllegalArgumentException("Paginacao invalida.");
        }
        try (PreparedStatement s = conexao.prepareStatement("SELECT " + COLUNAS
                + " FROM item_pedido WHERE id_comanda = ? ORDER BY id_item LIMIT ? OFFSET ?")) {
            s.setLong(1, idComanda); s.setInt(2, limite); s.setInt(3, deslocamento);
            try (ResultSet r = s.executeQuery()) {
                List<ItemPedidoRegistro> itens = new ArrayList<>();
                while (r.next()) itens.add(mapear(r));
                return List.copyOf(itens);
            }
        }
    }

    // Converte a linha atual sem mover o cursor. O construtor do record reaplica
    // as validacoes; dado salvo invalido vira erro JDBC de dados (SQLState 22000),
    // com a causa original preservada, e nao um Optional vazio.
    private static ItemPedidoRegistro mapear(ResultSet r) throws SQLException {
        try {
            return new ItemPedidoRegistro(r.getLong("id_item"), r.getLong("id_comanda"),
                    r.getLong("id_produto"), r.getInt("quantidade"),
                    r.getBigDecimal("preco_unitario_aplicado"), r.getString("observacao"));
        } catch (IllegalArgumentException erro) {
            throw new SQLException("Item armazenado incompativel com o modelo Java.", "22000", erro);
        }
    }

    // A assinatura aceita long, mas a coluna MySQL e INT assinado positivo.
    // Validar a faixa evita enviar um identificador que nao cabe no esquema.
    private static void validarId(long id) {
        if (id < 1 || id > Integer.MAX_VALUE) throw new IllegalArgumentException("ID fora da faixa INT positiva.");
    }

    // A insercao de item deve acompanhar estoque e totais. Com autoCommit ligado,
    // o banco confirmaria cada comando antes de o service terminar o checkout.
    private void exigirTransacao() throws SQLException {
        if (conexao.getAutoCommit()) throw new SQLException("Insercao exige transacao explicita.");
    }
}
