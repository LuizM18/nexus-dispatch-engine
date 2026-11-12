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
 * Persiste e consulta os dados comuns dos perfis na tabela usuario usando JDBC.
 *
 * O DAO recebe uma Connection pronta. Quem a criou deve fecha-la e decidir
 * commit/rollback; esta classe fecha somente PreparedStatement e ResultSet.
 * A autorizacao de quem pode executar cada operacao pertence ao service.
 * Inserir um perfil LOJISTA/ENTREGADOR aqui nao cria seu registro complementar;
 * o service deve inserir ambas as partes na mesma transacao.
 */
public final class UsuarioDao {
    // Consultas de cadastro nao incluem senha_hash.
    private static final String COLUNAS = "id_usuario, nome_razao_social, documento_fiscal, "
            + "email, tipo_perfil, data_cadastro, status_ativo";
    // A conexao e compartilhada com outros DAOs durante a mesma operacao/transacao.
    private final Connection conexao;

    /** Recebe a conexao aberta pelo chamador; null e erro de configuracao imediato. */
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

    /** Leitura atual bloqueada para gerenciar perfil/atividade sem sobrescrever concorrencia. */
    public Optional<UsuarioRegistro> buscarPorIdParaAtualizacao(long id) throws SQLException {
        validarId(id);
        if (conexao.getAutoCommit()) throw new SQLException("Bloqueio exige transacao explicita.");
        try (PreparedStatement s = conexao.prepareStatement("SELECT " + COLUNAS + " FROM usuario WHERE id_usuario=? FOR UPDATE")) {
            s.setLong(1, id);
            try (ResultSet r = s.executeQuery()) { return r.next() ? Optional.of(mapear(r)) : Optional.empty(); }
        }
    }

    /** Verificacao exclusiva para o primeiro administrador, em transacao SERIALIZABLE. */
    public boolean existeAdministradorParaAtualizacao() throws SQLException {
        if (conexao.getAutoCommit() || conexao.getTransactionIsolation() != Connection.TRANSACTION_SERIALIZABLE)
            throw new SQLException("Cadastro administrativo inicial exige transacao serializavel.");
        try (PreparedStatement comando = conexao.prepareStatement(
                "SELECT id_usuario FROM usuario WHERE tipo_perfil='ADMIN' FOR UPDATE");
                ResultSet resultado = comando.executeQuery()) {
            return resultado.next();
        }
    }

    /**
     * Remove apenas cliente inativo sem compras, complementos ou autoria de auditoria.
     * Nao apagar identidade de quem ja participou do sistema nem depender de CASCADE.
     */
    public boolean excluirClienteSemVinculos(long id) throws SQLException {
        validarId(id);
        if (conexao.getAutoCommit()) throw new SQLException("Exclusao protegida exige transacao explicita.");
        var usuario = buscarPorIdParaAtualizacao(id);
        if (usuario.isEmpty()) return false;
        if (usuario.orElseThrow().perfil() != PerfilUsuario.CLIENTE || !Boolean.FALSE.equals(usuario.orElseThrow().ativo()))
            throw new IllegalStateException("Remocao fisica exige CLIENTE explicitamente inativo. Para outros perfis use desativacao.");
        String sql = """
                SELECT EXISTS(SELECT 1 FROM pedido_mestre WHERE id_cliente=?)
                    OR EXISTS(SELECT 1 FROM lojista WHERE id_lojista=?)
                    OR EXISTS(SELECT 1 FROM entregador WHERE id_entregador=?)
                    OR EXISTS(SELECT 1 FROM auditoria_sistema WHERE id_usuario_ator=?)
                """;
        try (PreparedStatement s = conexao.prepareStatement(sql)) {
            for (int i = 1; i <= 4; i++) s.setLong(i, id);
            try (ResultSet r = s.executeQuery()) {
                r.next();
                if (r.getBoolean(1)) throw new IllegalStateException(
                        "Este cliente tem histórico ou vínculos. Mantenha o cadastro desativado.");
            }
        }
        return excluir(id);
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

    /** Resultado exclusivo da consulta de login. */
    public record Credencial(UsuarioRegistro usuario, String hash) {
        @Override public String toString() { return "Credencial[id=" + usuario.id() + "]"; }
    }

    /** Busca o cadastro ativo e seu hash juntos para validar a senha no service. */
    public Optional<Credencial> buscarCredencialAtiva(String email) throws SQLException {
        email = validarEmail(email);
        try (PreparedStatement comando = conexao.prepareStatement(
                "SELECT " + COLUNAS + ", senha_hash FROM usuario WHERE email = ? AND status_ativo = TRUE")) {
            comando.setString(1, email);
            try (ResultSet resultado = comando.executeQuery()) {
                if (!resultado.next()) return Optional.empty();
                return Optional.of(new Credencial(mapear(resultado), resultado.getString("senha_hash")));
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

    /** Identidade comercial exibida na selecao de loja. */
    public record Identificacao(long id, String nome) { }

    /** Lista somente nomes e IDs de lojas ativas, em ordem de ID. */
    public List<Identificacao> listarLojasAtivasApos(long depoisDe, int limite) throws SQLException {
        String sql = """
                SELECT u.id_usuario, l.nome_fantasia AS nome
                FROM usuario u
                JOIN lojista l ON l.id_lojista = u.id_usuario
                WHERE u.tipo_perfil = 'LOJISTA' AND u.status_ativo = TRUE AND u.id_usuario > ?
                ORDER BY u.id_usuario
                LIMIT ?
                """;
        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setLong(1, depoisDe);
            comando.setInt(2, limite);
            try (ResultSet resultado = comando.executeQuery()) {
                List<Identificacao> perfis = new ArrayList<>();
                while (resultado.next()) {
                    perfis.add(new Identificacao(resultado.getLong("id_usuario"), resultado.getString("nome")));
                }
                return List.copyOf(perfis);
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

    /** Converte uma linha do SELECT em registro, preservando SQL NULL em campos opcionais. */
    private static UsuarioRegistro mapear(ResultSet resultado) throws SQLException {
        Timestamp data = resultado.getTimestamp("data_cadastro");
        // getBoolean devolve false tanto para FALSE quanto para NULL. wasNull deve
        // ser consultado imediatamente depois para nao perder a ausencia do dado.
        boolean ativo = resultado.getBoolean("status_ativo");
        Boolean ativoOuNulo = resultado.wasNull() ? null : ativo;
        return new UsuarioRegistro(resultado.getLong("id_usuario"), resultado.getString("nome_razao_social"),
                resultado.getString("documento_fiscal"), resultado.getString("email"),
                PerfilUsuario.valueOf(resultado.getString("tipo_perfil")),
                data == null ? null : data.toLocalDateTime(), ativoOuNulo);
    }

    /** Confere a faixa da coluna MySQL INT antes de enviar o identificador ao banco. */
    private static void validarId(long id) {
        // A coluna e INT assinado; Long e aceito pela API sem permitir overflow no banco.
        if (id < 1 || id > Integer.MAX_VALUE) throw new IllegalArgumentException("ID fora da faixa valida.");
    }

    /** Remove espacos externos e aplica o limite da coluna de nome. */
    private static String validarNome(String nome) {
        nome = texto(nome, "Nome", 120);
        if (nome.codePoints().noneMatch(Character::isLetter)) {
            throw new IllegalArgumentException("O nome deve conter pelo menos uma letra.");
        }
        return nome;
    }

    /** Valida presenca, tamanho e formato basico; nao comprova que a caixa existe. */
    private static String validarEmail(String email) {
        email = texto(email, "Email", 100);
        if (!email.matches("(?U)^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("Informe um email com formato valido.");
        }
        return email;
    }

    /** Regra comum para texto obrigatorio: rejeita vazio e conta caracteres Unicode. */
    private static String texto(String valor, String campo, int limite) {
        if (valor == null || valor.isBlank()) throw new IllegalArgumentException(campo + " e obrigatorio.");
        String tratado = valor.strip();
        if (tratado.codePointCount(0, tratado.length()) > limite) {
            throw new IllegalArgumentException(campo + " excede " + limite + " caracteres.");
        }
        return tratado;
    }
}
