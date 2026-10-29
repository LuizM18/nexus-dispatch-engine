package com.nexusdispatch.service;

import com.nexusdispatch.dao.EntregadorDao;
import com.nexusdispatch.dao.OrdemEntregaDao;
import com.nexusdispatch.database.ConexaoBD;
import java.sql.*;
import java.util.Objects;

/** Coordena o aceite atomico. Autorizacao do ator e elegibilidade territorial ainda pertencem a integracao futura. */
public final class EntregaService {
    /** Cada chamada deve fornecer uma conexao nova, dedicada e com autoCommit=true. */
    @FunctionalInterface
    public interface FabricaConexao { Connection abrir() throws SQLException; }
    private final FabricaConexao fabrica;
    public EntregaService() { this(ConexaoBD::conectar); }
    public EntregaService(FabricaConexao fabrica) {
        this.fabrica = Objects.requireNonNull(fabrica, "Fabrica obrigatoria.");
    }

    /** false indica disputa ou indisponibilidade; falhas JDBC sao propagadas apos rollback. */
    public boolean aceitarEntrega(long idEntregador, long idEntrega) throws SQLException {
        validarId(idEntregador); validarId(idEntrega);
        try (Connection c = fabrica.abrir()) {
            if (!c.getAutoCommit()) throw new SQLException("Use conexao nova sem transacao em andamento.");
            c.setAutoCommit(false);
            try {
                EntregadorDao entregadores = new EntregadorDao(c);
                OrdemEntregaDao ordens = new OrdemEntregaDao(c);
                // Ordem fixa de bloqueios: entregador, depois ordem.
                // O UPDATE condicional serializa tentativas do mesmo entregador.
                if (!entregadores.ocuparDisponivel(idEntregador)
                        || ordens.possuiOrdemAtiva(idEntregador)
                        || !ordens.vincularDisponivel(idEntrega, idEntregador)) {
                    c.rollback();
                    return false;
                }
                c.commit();
                return true;
            } catch (SQLException | RuntimeException erro) {
                try { c.rollback(); } catch (SQLException falhaRollback) { erro.addSuppressed(falhaRollback); }
                throw erro;
            }
            // try-with-resources fecha a conexao dedicada inclusive em erro.
        }
    }
    private static void validarId(long id) {
        if (id <= 0 || id > Integer.MAX_VALUE) throw new IllegalArgumentException("ID fora da faixa INT.");
    }
}
