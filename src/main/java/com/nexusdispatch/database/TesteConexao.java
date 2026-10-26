package com.nexusdispatch.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import io.github.cdimascio.dotenv.DotenvException;

/**
 * Diagnostico manual que acessa o MySQL real por JDBC.
 * Executa apenas uma consulta de verificacao: nao cria tabelas nem altera dados.
 * Ajuda a confirmar o acesso, a versao do servidor e o banco selecionado.
 */
public final class TesteConexao {
    // A execucao comeca em main; nao e necessario instanciar esta classe.
    private TesteConexao() { }

    public static void main(String[] args) {
        // Sem argumentos, testa o banco configurado; --servidor testa apenas o servidor.
        boolean somenteServidor = args.length == 1 && args[0].equals("--servidor");
        // Argumentos desconhecidos encerram o programa com codigo de falha (1).
        if (args.length > 1 || (args.length == 1 && !somenteServidor)) {
            System.err.println("Uso: TesteConexao [--servidor]");
            System.exit(1);
        }
        // try-with-resources fecha resultado, comando e conexao, nessa ordem,
        // inclusive se ocorrer uma excecao durante a consulta ou a leitura.
        // SELECT 1 verifica a resposta; VERSION() e DATABASE() identificam o destino.
        try (Connection conexao = somenteServidor
                ? ConexaoBD.conectarAoServidor() : ConexaoBD.conectar();
             PreparedStatement comando = conexao.prepareStatement(
                     "SELECT 1, VERSION(), DATABASE()");
             ResultSet resultado = comando.executeQuery()) {
            // O cursor inicia antes da primeira linha; next() avanca para ela.
            // Os indices JDBC comecam em 1 e seguem a ordem das colunas do SELECT.
            if (!resultado.next() || resultado.getInt(1) != 1) {
                throw new SQLException("Resposta inesperada do servidor.");
            }
            System.out.println("Conexao JDBC realizada com sucesso.");
            System.out.println("Versao: " + resultado.getString(2));
            // DATABASE() retorna null quando a conexao nao selecionou um banco.
            String banco = resultado.getString(3);
            System.out.println("Banco: " + (banco == null ? "nenhum (teste do servidor)" : banco));
        } catch (IllegalArgumentException e) {
            // Erro de validacao das configuracoes, como porta invalida ou chave ausente.
            System.err.println(e.getMessage());
            System.exit(1);
        } catch (DotenvException e) {
            // Erro de leitura ou interpretacao do arquivo de configuracao.
            System.err.println("Nao foi possivel ler o .env. Confira o formato e a pasta de execucao.");
            System.exit(1);
        } catch (SQLException e) {
            // SQLState e codigo do MySQL ajudam a identificar a causa da falha JDBC.
            // Nao imprimir URL, credenciais ou stack trace com dados de configuracao.
            System.err.println("Falha JDBC. SQLState: " + e.getSQLState()
                    + "; codigo: " + e.getErrorCode());
            // 1045 indica acesso negado; 1049 indica que o banco informado nao existe.
            switch (e.getErrorCode()) {
                case 1045 -> System.err.println("Confira usuario, senha e permissoes no MySQL.");
                case 1049 -> System.err.println("Banco inexistente. Importe o SQL do grupo; use --servidor para testar somente o acesso.");
                default -> System.err.println("Confira o driver Maven, servidor, porta e configuracao TLS.");
            }
            System.exit(1);
        }
    }
}
