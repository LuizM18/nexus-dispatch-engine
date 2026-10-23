package dev.lm18.nexus.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConexaoBD {

    private static ConexaoBD instancia;
    private final String url;
    private final String user;
    private final String password;

    private ConexaoBD() {
        String host = obterVariavelAmbiente("DB_HOST");
        String port = obterVariavelAmbiente("DB_PORT"); 
        String dbName = obterVariavelAmbiente("DB_NAME");
        
        this.url = String.format("jdbc:mysql://%s:%s/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", host, port, dbName);
        this.user = obterVariavelAmbiente("DB_USER");
        this.password = obterVariavelAmbiente("DB_PASS");

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Driver de conexão não foi encontrado.", e);
        }
    }

    private String obterVariavelAmbiente(String chave) {
        String valor = System.getenv(chave);
        if (valor == null || valor.trim().isEmpty()) {
            throw new IllegalStateException("Erro Crítico: A variável de ambiente " + chave + " não está configurada.");
        }
        return valor.trim();
    }

    public static synchronized ConexaoBD getInstancia() {
        if (instancia == null) {
            instancia = new ConexaoBD();
        }
        return instancia;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(this.url, this.user, this.password);
    }
}
