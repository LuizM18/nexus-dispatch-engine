package com.nexusdispatch.service;

import java.sql.Connection;
import java.sql.SQLException;

/** Infraestrutura compartilhada dos novos services; nao abre transacoes aninhadas. */
public final class TransacaoJdbc {
    @FunctionalInterface
    public interface FabricaConexao { Connection abrir() throws SQLException; }
    // Interface funcional possui uma unica operacao abstrata e pode receber lambda.
    // T representa o tipo devolvido pelo trabalho: pode ser registro, lista ou Boolean.
    @FunctionalInterface
    interface Operacao<T> { T executar(Connection c) throws SQLException; }
    private TransacaoJdbc() { }

    /** Conexao dedicada, leitura atual apos bloqueios e rollback que preserva o erro original. */
    static <T> T executar(FabricaConexao fabrica, Operacao<T> operacao) throws SQLException {
        try (Connection c = fabrica.abrir()) {
            // A fabrica entrega uma conexao exclusiva. Recusar uma transacao ja iniciada
            // evita confirmar ou reverter trabalho que pertenca a outro chamador.
            if (!c.getAutoCommit()) throw new SQLException("Use conexao nova sem transacao em andamento.");
            // READ_COMMITTED permite que novas leituras vejam alteracoes ja confirmadas.
            // Nao congela varias consultas como um unico retrato: os services usam bloqueios
            // quando precisam coordenar leituras e escritas de uma mesma operacao.
            c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            c.setAutoCommit(false);
            try {
                // autoCommit false agrupa as escritas ate commit/rollback. A lambda recebe
                // a mesma conexao para todos os DAOs; so devolvemos o resultado apos o commit.
                T resultado = operacao.executar(c);
                c.commit();
                return resultado;
            } catch (SQLException | RuntimeException erro) {
                // Falha JDBC ou de regra interrompe o trabalho. Se o rollback tambem falhar,
                // addSuppressed anexa a segunda falha sem esconder a excecao que iniciou o erro.
                // O try-with-resources externo fecha a conexao inclusive neste caminho.
                try { c.rollback(); } catch (SQLException falha) { erro.addSuppressed(falha); }
                throw erro;
            }
        }
    }

    /** Range comum das chaves INT positivas, antes de abrir conexao. */
    static void validarId(long id) {
        if (id < 1 || id > Integer.MAX_VALUE) throw new IllegalArgumentException("ID fora da faixa INT positiva.");
    }
}
