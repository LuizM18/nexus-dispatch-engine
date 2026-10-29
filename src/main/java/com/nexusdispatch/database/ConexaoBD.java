package com.nexusdispatch.database;

import io.github.cdimascio.dotenv.Dotenv;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Centraliza a abertura de conexoes JDBC, a API Java de acesso a bancos relacionais.
 * Cada chamada abre uma conexao; quem chama deve fecha-la com try-with-resources.
 * As configuracoes ficam no .env ou no ambiente, fora do codigo Java.
 */
public final class ConexaoBD {
    // Classe utilitaria: os metodos sao estaticos e nao exigem criar um objeto.
    private ConexaoBD() { }

    /** Abre a conexao selecionando o banco informado em DB_NAME (nexus_db no projeto). */
    public static Connection conectar() throws SQLException {
        return abrir(true);
    }

    /** Diagnostico do servidor, mesmo antes de importar nexus_db. */
    public static Connection conectarAoServidor() throws SQLException {
        return abrir(false);
    }

    private static Connection abrir(boolean selecionarBanco) throws SQLException {
        // Execute a partir da raiz do projeto, onde esta o .env.
        // ignoreIfMissing permite usar variaveis de ambiente sem um arquivo .env.
        // As chaves necessarias continuam sendo verificadas por obrigatoria().
        Dotenv env = Dotenv.configure().ignoreIfMissing().load();
        String host = obrigatoria(env, "DB_HOST").trim();
        String portaTexto = obrigatoria(env, "DB_PORT").trim();
        String usuario = obrigatoria(env, "DB_USER").trim();
        String senha = obrigatoria(env, "DB_PASS"); // Nao remover espacos da senha.
        // Valida o formato antes de montar a URL; isso nao testa se o host existe.
        if (!host.matches("[A-Za-z0-9.-]+")) {
            throw new IllegalArgumentException("DB_HOST deve conter um hostname ou IPv4.");
        }
        int porta;
        // A porta vem como texto e precisa ser um inteiro dentro da faixa TCP.
        try {
            porta = Integer.parseInt(portaTexto);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("DB_PORT deve ser um numero de 1 a 65535.");
        }
        if (porta < 1 || porta > 65535) {
            throw new IllegalArgumentException("DB_PORT deve ser um numero de 1 a 65535.");
        }
        // No diagnostico do servidor, DB_NAME nao e exigido e nenhum banco e selecionado.
        String banco = selecionarBanco ? obrigatoria(env, "DB_NAME").trim() : "";
        if (selecionarBanco && !banco.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("DB_NAME deve conter letras, numeros ou sublinhado.");
        }
        // Credenciais e opcoes sao passadas ao driver separadamente da URL JDBC.
        Properties propriedades = new Properties();
        propriedades.setProperty("user", usuario);
        propriedades.setProperty("password", senha);
        boolean local = host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1");
        // Local: TLS obrigatorio. Remoto: exige tambem certificado confiavel e hostname.
        // REQUIRED exige criptografia, mas nao verifica a identidade do servidor.
        propriedades.setProperty("sslMode", local ? "REQUIRED" : "VERIFY_IDENTITY");
        // Tempos em milissegundos: estabelecer conexao (5 s) e operacoes de rede (10 s).
        propriedades.setProperty("connectTimeout", "5000");
        propriedades.setProperty("socketTimeout", "10000");
        // O DriverManager usa o driver MySQL disponivel para abrir a conexao.
        // Formato da URL: jdbc:mysql://host:porta/banco. Falhas JDBC sobem ao chamador.
        return DriverManager.getConnection(
                "jdbc:mysql://" + host + ":" + porta + "/" + banco, propriedades);
    }

    /** Rejeita configuracoes ausentes ou em branco antes de tentar acessar o MySQL. */
    private static String obrigatoria(Dotenv env, String nome) {
        String valor = env.get(nome);
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Preencha " + nome + " no .env ou no ambiente.");
        }
        return valor;
    }
}
