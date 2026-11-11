package com.nexusdispatch.integracao;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.UUID;

/** Cria um catálogo vazio por cenário e injeta conexões restritas a esse catálogo. */
final class BancoTeste implements AutoCloseable {
    // UUID produz um nome exclusivo por cenario, separado dos catalogos de negocio.
    // Somente esse nome gerado internamente e usado no CREATE/DROP, nunca entrada do terminal.
    private final String catalogo = "nexus_test_" + UUID.randomUUID().toString().replace("-", "");
    private final String urlServidor;
    private final Properties propriedades = new Properties();
    private final Connection administrativa;

    private BancoTeste() throws SQLException {
        String porta = ambiente("NEXUS_TEST_DB_PORT");
        if (!porta.matches("[0-9]{1,5}") || Integer.parseInt(porta) < 1 || Integer.parseInt(porta) > 65535) {
            throw new IllegalArgumentException("NEXUS_TEST_DB_PORT deve estar entre 1 e 65535.");
        }
        urlServidor = "jdbc:mysql://127.0.0.1:" + porta + "/";
        propriedades.setProperty("user", ambiente("NEXUS_TEST_DB_USER"));
        propriedades.setProperty("password", ambiente("NEXUS_TEST_DB_PASS"));
        propriedades.setProperty("sslMode", "REQUIRED");
        propriedades.setProperty("connectTimeout", "5000");
        propriedades.setProperty("socketTimeout", "10000");
        administrativa = DriverManager.getConnection(urlServidor, propriedades);
    }

    // Cria o catalogo e carrega o esquema antes de devolver o recurso ao teste.
    // Se a preparacao falhar, limpa o que chegou a criar e preserva a causa original;
    // a flag distingue falha no CREATE de falha posterior na carga das tabelas.
    static BancoTeste criar() throws SQLException, IOException {
        BancoTeste banco = new BancoTeste();
        boolean criado = false;
        try {
            try (var comando = banco.administrativa.createStatement()) {
                comando.execute("CREATE DATABASE `" + banco.catalogo + "` CHARACTER SET utf8mb4");
                criado = true;
            }
            banco.carregarEsquema();
            return banco;
        } catch (SQLException | IOException | RuntimeException | Error erro) {
            try {
                if (criado) banco.close();
                else banco.administrativa.close();
            } catch (SQLException falhaNaLimpeza) {
                erro.addSuppressed(falhaNaLimpeza);
            }
            throw erro;
        }
    }

    // Cada chamada abre outra conexao para o MESMO catalogo descartavel.
    // Isso permite testar concorrencia real e visibilidade de commit entre conexoes.
    Connection abrir() throws SQLException {
        return DriverManager.getConnection(urlServidor + catalogo, propriedades);
    }

    // Le o recurso versionado em UTF-8. O separador aceita DDL terminado por
    // ponto e virgula no fim da linha; nao e um interpretador SQL geral e nao
    // suporta automaticamente corpos de triggers com delimitadores internos.
    private void carregarEsquema() throws IOException, SQLException {
        try (var arquivo = BancoTeste.class.getResourceAsStream("/esquema-mysql-teste.sql");
                var conexao = abrir();
                var comando = conexao.createStatement()) {
            if (arquivo == null) throw new IOException("Recurso esquema-mysql-teste.sql ausente.");
            String esquema = new String(arquivo.readAllBytes(), StandardCharsets.UTF_8);
            // O recurso contém DDL de tabelas, com cada comando encerrado no fim de uma linha.
            for (String ddl : esquema.split("(?m);[ \\t]*$")) {
                if (!ddl.isBlank()) comando.execute(ddl);
            }
        }
    }

    // AutoCloseable integra o banco ao try-with-resources: sair do cenario remove
    // o catalogo mesmo apos uma assercao falhar e fecha a conexao administrativa.
    // Encerramento forcado do processo pode impedir essa limpeza.
    @Override
    public void close() throws SQLException {
        try (var conexao = administrativa; var comando = conexao.createStatement()) {
            comando.execute("DROP DATABASE `" + catalogo + "`");
        }
    }

    // Exige configuracao NEXUS_TEST_DB_* explicita. Nao reutiliza silenciosamente
    // o .env da aplicacao para escolher as credenciais dos testes de integracao.
    private static String ambiente(String nome) {
        String valor = System.getenv(nome);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Defina " + nome + " para executar os testes de integração.");
        }
        return valor;
    }
}
