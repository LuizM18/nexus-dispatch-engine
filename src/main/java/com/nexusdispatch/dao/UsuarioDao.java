package com.nexusdispatch.dao;

import com.nexusdispatch.enums.PerfilUsuario;
import com.nexusdispatch.model.UsuarioRegistro;
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
 * Persiste a tabela usuario do banco importado usando JDBC.
 * O chamador fornece e fecha a conexao, decide commit/rollback e autoriza a operacao.
 * Este DAO fecha apenas seus comandos e resultados; nao modifica o autoCommit.
 * Cadastrar um LOJISTA/ENTREGADOR aqui ainda nao cria seu registro complementar.
 */
public final class UsuarioDao {
    private static final String COLUNAS = "id_usuario, nome_razao_social, documento_fiscal, "
            + "email, tipo_perfil, data_cadastro, status_ativo";
    private final Connection conexao;

    public UsuarioDao(Connection conexao) {
        this.conexao = Objects.requireNonNull(conexao, "A conexao e obrigatoria.");
    }

    /** Retorna o ID gerado pelo MySQL. O hash deve ser produzido pelo servico. */
    public long inserir(String nome, String documentoFiscal, String email,
            String senhaHash, PerfilUsuario perfil) throws SQLException {
        nome = validarNome(nome);
        documentoFiscal = texto(documentoFiscal, "Documento fiscal", 18);
        email = validarEmail(email);
        // CHAR(60) e o contrato atual do banco. Comprimento nao comprova hash seguro.
        if (senhaHash == null || senhaHash.isBlank() || senhaHash.length() != 60
                || senhaHash.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Informe um hash sem espacos com 60 caracteres.");
        }
        if (perfil == null) throw new IllegalArgumentException("O perfil e obrigatorio.");
        String sql = "INSERT INTO usuario (nome_razao_social, documento_fiscal, email, "
                + "senha_hash, tipo_perfil) VALUES (?, ?, ?, ?, ?)";
        // Os valores sao vinculados aos ?, nunca concatenados ao SQL.
        try (PreparedStatement comando = conexao.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            comando.setString(1, nome);
            comando.setString(2, documentoFiscal);
            comando.setString(3, email);
            comando.setString(4, senhaHash);
            comando.setString(5, perfil.name());
            if (comando.executeUpdate() != 1) throw new SQLException("Cadastro nao inseriu uma linha.");
            try (ResultSet chaves = comando.getGeneratedKeys()) {
                if (!chaves.next()) throw new SQLException("O banco nao retornou o ID gerado.");
                return chaves.getLong(1);
            }
        }
    }

    /** Optional.empty() significa que o ID nao foi encontrado. */
    public Optional<UsuarioRegistro> buscarPorId(long id) throws SQLException {
        validarId(id);
        try (PreparedStatement comando = conexao.prepareStatement(
                "SELECT " + COLUNAS + " FROM usuario WHERE id_usuario = ?")) {
            comando.setLong(1, id);
            try (ResultSet resultado = comando.executeQuery()) {
                return resultado.next() ? Optional.of(mapear(resultado)) : Optional.empty();
            }
        }
    }

    /** A comparacao de email segue a collation do MySQL; nao retorna o hash. */
    public Optional<UsuarioRegistro> buscarPorEmail(String email) throws SQLException {
        email = validarEmail(email);
        try (PreparedStatement comando = conexao.prepareStatement(
                "SELECT " + COLUNAS + " FROM usuario WHERE email = ?")) {
            comando.setString(1, email);
            try (ResultSet resultado = comando.executeQuery()) {
                return resultado.next() ? Optional.of(mapear(resultado)) : Optional.empty();
            }
        }
    }

    /** Paginacao evita carregar todos os usuarios; inclui ativos e inativos. */
    public List<UsuarioRegistro> listar(int limite, int deslocamento) throws SQLException {
        if (limite < 1 || limite > 100 || deslocamento < 0) {
            throw new IllegalArgumentException("Limite deve ser de 1 a 100 e deslocamento nao negativo.");
        }
        try (PreparedStatement comando = conexao.prepareStatement(
                "SELECT " + COLUNAS + " FROM usuario ORDER BY id_usuario LIMIT ? OFFSET ?")) {
            comando.setInt(1, limite);
            comando.setInt(2, deslocamento);
            try (ResultSet resultado = comando.executeQuery()) {
                List<UsuarioRegistro> usuarios = new ArrayList<>();
                while (resultado.next()) usuarios.add(mapear(resultado));
                return List.copyOf(usuarios);
            }
        }
    }

    /** Atualiza dados cadastrais; perfil e senha exigirao fluxos proprios no servico. */
    public boolean atualizarCadastro(long id, String nome, String documentoFiscal, String email)
            throws SQLException {
        validarId(id);
        nome = validarNome(nome);
        documentoFiscal = texto(documentoFiscal, "Documento fiscal", 18);
        email = validarEmail(email);
        try (PreparedStatement comando = conexao.prepareStatement(
                "UPDATE usuario SET nome_razao_social = ?, documento_fiscal = ?, email = ? WHERE id_usuario = ?")) {
            comando.setString(1, nome);
            comando.setString(2, documentoFiscal);
            comando.setString(3, email);
            comando.setLong(4, id);
            return comando.executeUpdate() > 0;
        }
    }

    /** Desativar preserva o cadastro e suas referencias historicas. */
    public boolean definirAtivo(long id, boolean ativo) throws SQLException {
        validarId(id);
        try (PreparedStatement comando = conexao.prepareStatement(
                "UPDATE usuario SET status_ativo = ? WHERE id_usuario = ?")) {
            comando.setBoolean(1, ativo);
            comando.setLong(2, id);
            return comando.executeUpdate() > 0;
        }
    }

    /** Exclusao fisica: as chaves estrangeiras podem impedir a remocao. */
    public boolean excluir(long id) throws SQLException {
        validarId(id);
        try (PreparedStatement comando = conexao.prepareStatement("DELETE FROM usuario WHERE id_usuario = ?")) {
            comando.setLong(1, id);
            return comando.executeUpdate() > 0;
        }
    }

    private static UsuarioRegistro mapear(ResultSet resultado) throws SQLException {
        Timestamp data = resultado.getTimestamp("data_cadastro");
        boolean ativo = resultado.getBoolean("status_ativo");
        Boolean ativoOuNulo = resultado.wasNull() ? null : ativo;
        return new UsuarioRegistro(resultado.getLong("id_usuario"), resultado.getString("nome_razao_social"),
                resultado.getString("documento_fiscal"), resultado.getString("email"),
                PerfilUsuario.valueOf(resultado.getString("tipo_perfil")),
                data == null ? null : data.toLocalDateTime(), ativoOuNulo);
    }

    private static void validarId(long id) {
        // A coluna e INT assinado; Long e aceito pela API sem permitir overflow no banco.
        if (id < 1 || id > Integer.MAX_VALUE) throw new IllegalArgumentException("ID fora da faixa valida.");
    }

    private static String validarNome(String nome) {
        nome = texto(nome, "Nome", 120);
        if (nome.codePoints().noneMatch(Character::isLetter)) {
            throw new IllegalArgumentException("O nome deve conter pelo menos uma letra.");
        }
        return nome;
    }

    private static String validarEmail(String email) {
        email = texto(email, "Email", 100);
        if (!email.matches("(?U)^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("Informe um email com formato valido.");
        }
        return email;
    }

    private static String texto(String valor, String campo, int limite) {
        if (valor == null || valor.isBlank()) throw new IllegalArgumentException(campo + " e obrigatorio.");
        String tratado = valor.strip();
        if (tratado.codePointCount(0, tratado.length()) > limite) {
            throw new IllegalArgumentException(campo + " excede " + limite + " caracteres.");
        }
        return tratado;
    }
}
