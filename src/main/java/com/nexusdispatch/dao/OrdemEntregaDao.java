package com.nexusdispatch.dao;

import java.sql.*;
import java.util.Objects;

/** Operacoes de aceite conforme o SQL real. Nao fecha a conexao ou confirma transacoes. */
public final class OrdemEntregaDao {
    private final Connection conexao;
    public OrdemEntregaDao(Connection conexao) {
        this.conexao = Objects.requireNonNull(conexao, "Conexao obrigatoria.");
    }

    /** Leitura atual com bloqueio, apos bloquear o entregador, inclusive para dados antigos incoerentes. */
    public boolean possuiOrdemAtiva(long idEntregador) throws SQLException {
        exigirTransacao();
        try (PreparedStatement s = conexao.prepareStatement(
                "SELECT id_entrega FROM ordem_entrega WHERE id_entregador=? "
                + "AND status_entrega IN ('AGUARDANDO_COLETA','EM_TRANSITO') FOR UPDATE")) {
            s.setLong(1, idEntregador);
            try (ResultSet r = s.executeQuery()) { return r.next(); }
        }
    }

    /** Aceitar nao significa coletar: preserva AGUARDANDO_COLETA e as datas. */
    public boolean vincularDisponivel(long idEntrega, long idEntregador) throws SQLException {
        exigirTransacao();
        String sql = "UPDATE ordem_entrega o JOIN comanda_lojista c ON c.id_comanda=o.id_comanda "
                + "SET o.id_entregador=? WHERE o.id_entrega=? AND o.id_entregador IS NULL "
                + "AND o.status_entrega='AGUARDANDO_COLETA' AND c.status_comanda='AGUARDANDO_COLETA'";
        try (PreparedStatement s = conexao.prepareStatement(sql)) {
            s.setLong(1, idEntregador); s.setLong(2, idEntrega);
            return s.executeUpdate() == 1;
        }
    }

    private void exigirTransacao() throws SQLException {
        if (conexao.getAutoCommit()) throw new SQLException("Aceite exige transacao explicita.");
    }
}
