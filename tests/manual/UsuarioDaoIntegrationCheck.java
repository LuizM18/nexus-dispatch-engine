import com.nexusdispatch.dao.UsuarioDao;
import com.nexusdispatch.database.ConexaoBD;
import com.nexusdispatch.enums.PerfilUsuario;
import com.nexusdispatch.model.UsuarioRegistro;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;

/**
 * Teste opt-in com MySQL real. A tabela TEMPORARY usuario oculta a permanente
 * somente nesta conexao. Nenhum cadastro real e lido, alterado ou excluido.
 * Copia o DDL de usuario (sem FKs no esquema atual), mas nao copia triggers.
 */
public class UsuarioDaoIntegrationCheck {
    private static int verificacoes;
    // Dado sintetico para testar armazenamento; nao e uma senha nem um hash de autenticacao.
    private static final String HASH = "x".repeat(60);

    public static void main(String[] args) throws Exception {
        try (Connection conexao = ConexaoBD.conectar()) {
            String ddl;
            try (Statement comando = conexao.createStatement();
                 ResultSet resultado = comando.executeQuery("SHOW CREATE TABLE nexus_db.usuario")) {
                if (!resultado.next()) throw new AssertionError("DDL de usuario nao encontrado");
                ddl = resultado.getString(2);
            }
            if (!ddl.startsWith("CREATE TABLE `usuario` (") || ddl.contains("FOREIGN KEY")) {
                throw new AssertionError("Esquema de usuario mudou; revise o isolamento do teste");
            }
            try (Statement comando = conexao.createStatement()) {
                // Se a criacao falhar, o teste termina antes de chamar o DAO.
                // CREATE ... LIKE com o mesmo nome e rejeitado; usamos o DDL inspecionado.
                comando.executeUpdate(ddl.replaceFirst("CREATE TABLE", "CREATE TEMPORARY TABLE"));
            }
            conexao.setAutoCommit(false);
            try {
                UsuarioDao dao = new UsuarioDao(conexao);
                check(dao.listar(10, 0).isEmpty(), "Tabela temporaria inicia vazia");
                long id = dao.inserir("  Ana D'Avila  ", " DOC-001 ", " ana@example.test ", HASH, PerfilUsuario.CLIENTE);
                UsuarioRegistro usuario = dao.buscarPorId(id).orElseThrow();
                check(id > 0 && usuario.id() == id, "ID gerado e recuperado");
                check(usuario.nome().equals("Ana D'Avila") && usuario.documentoFiscal().equals("DOC-001"), "Normalizacao e apostrofo");
                check(usuario.email().equals("ana@example.test"), "Email normalizado");
                check(usuario.perfil() == PerfilUsuario.CLIENTE && Boolean.TRUE.equals(usuario.ativo()), "Perfil e ativo padrao");
                check(usuario.dataCadastro() != null, "Data gerada pelo banco");
                check(!usuario.toString().contains(HASH), "Consulta nao expoe hash");
                check(dao.buscarPorEmail("ana@example.test").orElseThrow().id() == id, "Busca por email");
                check(dao.buscarPorEmail("ausente@example.test").isEmpty(), "Email inexistente");
                duplicado(() -> dao.inserir("Outro", "DOC-002", "ana@example.test", HASH, PerfilUsuario.ADMIN));
                duplicado(() -> dao.inserir("Outro", "DOC-001", "outro@example.test", HASH, PerfilUsuario.ADMIN));
                check(dao.atualizarCadastro(id, "Ana Atualizada", "DOC-003", "nova@example.test"), "Atualizacao");
                check(dao.buscarPorEmail("ana@example.test").isEmpty(), "Email anterior removido");
                check(dao.buscarPorEmail("nova@example.test").orElseThrow().nome().equals("Ana Atualizada"), "Dados atualizados");
                check(dao.definirAtivo(id, false) && Boolean.FALSE.equals(dao.buscarPorId(id).orElseThrow().ativo()), "Desativacao");
                check(dao.definirAtivo(id, true) && Boolean.TRUE.equals(dao.buscarPorId(id).orElseThrow().ativo()), "Reativacao");
                for (PerfilUsuario perfil : PerfilUsuario.values()) {
                    long outro = dao.inserir("Perfil " + perfil, "DOC-" + perfil, perfil + "@example.test", HASH, perfil);
                    check(dao.buscarPorId(outro).orElseThrow().perfil() == perfil, "Mapeamento de " + perfil);
                }
                check(dao.listar(2, 0).size() == 2 && dao.listar(2, 2).size() == 2, "Paginacao");
                check(dao.listar(1, 0).getFirst().id() == id, "Ordenacao por ID");
                check(dao.listar(10, 100).isEmpty(), "Pagina vazia");
                // NULL e permitido no DDL atual e nao deve virar false ou uma data inventada.
                try (Statement comando = conexao.createStatement()) {
                    comando.executeUpdate("UPDATE usuario SET status_ativo = NULL, data_cadastro = NULL");
                }
                check(dao.buscarPorId(id).orElseThrow().ativo() == null, "Status nulo preservado");
                check(dao.buscarPorId(id).orElseThrow().dataCadastro() == null, "Data nula preservada");
                check(dao.excluir(id) && dao.buscarPorId(id).isEmpty(), "Exclusao e busca ausente");
                check(!dao.excluir(id), "Exclusao inexistente");
                check(!dao.atualizarCadastro(id, "Ausente", "AUSENTE", "ausente@example.test"), "Atualizacao inexistente");
                check(!dao.definirAtivo(id, true), "Ativacao inexistente");
                for (long invalido : new long[]{0, -1, (long) Integer.MAX_VALUE + 1}) {
                    invalido(() -> dao.buscarPorId(invalido));
                    invalido(() -> dao.excluir(invalido));
                    invalido(() -> dao.definirAtivo(invalido, true));
                }
                for (String nome : new String[]{null, " ", "123", "a".repeat(121)}) {
                    invalido(() -> dao.inserir(nome, "DOC", "teste@example.test", HASH, PerfilUsuario.CLIENTE));
                }
                for (String doc : new String[]{null, " ", "d".repeat(19)}) {
                    invalido(() -> dao.inserir("Nome", doc, "teste@example.test", HASH, PerfilUsuario.CLIENTE));
                }
                for (String email : new String[]{null, "sem-arroba", "a b@example.test", "a".repeat(100) + "@b.test"}) {
                    invalido(() -> dao.buscarPorEmail(email));
                }
                for (String hash : new String[]{null, " ", "x".repeat(59), "x".repeat(61), " " + "x".repeat(59)}) {
                    invalido(() -> dao.inserir("Nome", "DOC", "teste@example.test", hash, PerfilUsuario.CLIENTE));
                }
                invalido(() -> dao.inserir("Nome", "DOC", "teste@example.test", HASH, null));
                invalido(() -> dao.listar(0, 0));
                invalido(() -> dao.listar(101, 0));
                invalido(() -> dao.listar(10, -1));
                long fronteira = dao.inserir("a".repeat(120), "d".repeat(18), "a".repeat(93) + "@b.test", HASH, PerfilUsuario.CLIENTE);
                check(dao.buscarPorId(fronteira).orElseThrow().email().length() == 100, "Limites 120/18/100 aceitos");
                check(!conexao.isClosed() && !conexao.getAutoCommit(), "DAO preserva conexao e transacao");
                conexao.rollback();
                check(dao.listar(10, 0).isEmpty(), "Rollback desfaz todas as gravacoes");
            } finally {
                conexao.rollback();
            }
        } catch (SQLException e) {
            // Evita mensagens do servidor que possam conter valores dos cadastros.
            System.err.println("Falha JDBC no teste: SQLState=" + e.getSQLState() + "; codigo=" + e.getErrorCode());
            System.exit(1);
        }
        System.out.println("UsuarioDao: " + verificacoes + " verificacoes passaram em tabela temporaria MySQL.");
    }

    private static void check(boolean condicao, String mensagem) {
        if (!condicao) throw new AssertionError(mensagem);
        verificacoes++;
    }

    private static void invalido(Acao acao) throws Exception {
        try { acao.executar(); } catch (IllegalArgumentException esperado) { verificacoes++; return; }
        throw new AssertionError("Entrada invalida aceita");
    }

    private static void duplicado(Acao acao) throws Exception {
        try { acao.executar(); } catch (SQLException esperado) {
            check(esperado.getErrorCode() == 1062, "Unicidade garantida pelo banco");
            return;
        }
        throw new AssertionError("Cadastro duplicado aceito");
    }

    @FunctionalInterface
    private interface Acao { void executar() throws Exception; }
}
