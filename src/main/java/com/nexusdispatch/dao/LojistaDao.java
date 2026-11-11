package com.nexusdispatch.dao;

import com.nexusdispatch.model.LojistaRegistro;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Persiste e consulta os dados complementares do lojista na tabela lojista.
 *
 * O ID de lojista e o mesmo ID previamente criado em usuario; por isso este DAO
 * nao gera outra chave nem cria o usuario. A FK confere que o usuario existe,
 * mas o service deve conferir perfil LOJISTA e autorizacao.
 * A Connection e a transacao pertencem ao chamador: esta classe fecha apenas
 * comandos/resultados e nunca executa commit ou rollback.
 */
public final class LojistaDao {
    private static final String COLUNAS = "id_lojista, nome_fantasia, taxa_comissao_pct, "
            + "bairro_operacao, endereco_origem";
    private final Connection conexao;

    public LojistaDao(Connection conexao) {
        this.conexao = Objects.requireNonNull(conexao, "A conexao e obrigatoria.");
    }

    /** Recebe o mesmo ID do usuario ja inserido; nao gera uma nova chave. */
    public void inserir(LojistaRegistro lojista) throws SQLException {
        exigirRegistro(lojista);
        try (PreparedStatement comando = conexao.prepareStatement(
                "INSERT INTO lojista (" + COLUNAS + ") VALUES (?, ?, ?, ?, ?)")) {
            comando.setLong(1, lojista.id());
            comando.setString(2, lojista.nomeFantasia());
            comando.setBigDecimal(3, lojista.taxaComissaoPct());
            comando.setString(4, lojista.bairroOperacao());
            comando.setString(5, lojista.enderecoOrigem());
            if (comando.executeUpdate() != 1) {
                throw new SQLException("Cadastro de lojista nao inseriu uma linha.");
            }
        }
    }

    /** Optional vazio indica ausencia; falha de banco continua sendo SQLException. */
    public Optional<LojistaRegistro> buscarPorId(long id) throws SQLException {
        validarId(id);
        // O ? recebe o valor separadamente do SQL, sem concatenar entrada do usuario.
        try (PreparedStatement comando = conexao.prepareStatement(
                "SELECT " + COLUNAS + " FROM lojista WHERE id_lojista = ?")) {
            comando.setLong(1, id);
            try (ResultSet resultado = comando.executeQuery()) {
                // O cursor comeca antes da primeira linha; next() tenta avancar.
                return resultado.next() ? Optional.of(mapear(resultado)) : Optional.empty();
            }
        }
    }

    /** Bloqueia o complemento antes de mudar atividade/cadastro; SQL nao muda aqui. */
    public Optional<LojistaRegistro> buscarPorIdParaAtualizacao(long id) throws SQLException {
        validarId(id);
        if (conexao.getAutoCommit()) throw new SQLException("Bloqueio exige transacao explicita.");
        try (PreparedStatement s = conexao.prepareStatement("SELECT " + COLUNAS + " FROM lojista WHERE id_lojista=? FOR UPDATE")) {
            s.setLong(1, id);
            try (ResultSet r = s.executeQuery()) { return r.next() ? Optional.of(mapear(r)) : Optional.empty(); }
        }
    }

    /** Impede desativacao durante preparo/coleta/transporte, preservando atendimento. */
    public boolean possuiComandaEmAndamento(long id) throws SQLException {
        validarId(id);
        if (conexao.getAutoCommit()) throw new SQLException("Verificacao exige transacao explicita.");
        // A verificacao considera todos os estados nao finais, inclusive PAGO.
        // FOR UPDATE conserva as linhas encontradas durante a decisao transacional de atividade.
        try (PreparedStatement s = conexao.prepareStatement("SELECT id_comanda FROM comanda_lojista WHERE id_lojista=? "
                + "AND status_comanda NOT IN ('CONCLUIDO','CANCELADO') FOR UPDATE")) {
            s.setLong(1, id);
            try (ResultSet r = s.executeQuery()) { return r.next(); }
        }
    }

    /** Retorna pagina ordenada por ID, incluindo usuarios ativos ou inativos. */
    public List<LojistaRegistro> listar(int limite, int deslocamento) throws SQLException {
        if (limite < 1 || limite > 100 || deslocamento < 0) {
            throw new IllegalArgumentException("Limite deve ser de 1 a 100 e deslocamento nao negativo.");
        }
        try (PreparedStatement comando = conexao.prepareStatement(
                "SELECT " + COLUNAS + " FROM lojista ORDER BY id_lojista LIMIT ? OFFSET ?")) {
            comando.setInt(1, limite);
            comando.setInt(2, deslocamento);
            try (ResultSet resultado = comando.executeQuery()) {
                List<LojistaRegistro> lojistas = new ArrayList<>();
                while (resultado.next()) lojistas.add(mapear(resultado));
                return List.copyOf(lojistas);
            }
        }
    }

    /** Atualiza os campos comerciais mantendo o vinculo com o mesmo usuario. */
    public boolean atualizar(LojistaRegistro lojista) throws SQLException {
        exigirRegistro(lojista);
        try (PreparedStatement comando = conexao.prepareStatement(
                "UPDATE lojista SET nome_fantasia = ?, taxa_comissao_pct = ?, "
                        + "bairro_operacao = ?, endereco_origem = ? WHERE id_lojista = ?")) {
            comando.setString(1, lojista.nomeFantasia());
            comando.setBigDecimal(2, lojista.taxaComissaoPct());
            comando.setString(3, lojista.bairroOperacao());
            comando.setString(4, lojista.enderecoOrigem());
            comando.setLong(5, lojista.id());
            // Segue a contagem de linhas reportada pelo driver, como UsuarioDao.
            return comando.executeUpdate() > 0;
        }
    }

    /**
     * Remove apenas o registro complementar, sem excluir o usuario.
     * Produtos e comandas vinculados podem impedir a exclusao por FK.
     * Desativacao do usuario e uma operacao separada em UsuarioDao.
     */
    public boolean excluir(long id) throws SQLException {
        validarId(id);
        try (PreparedStatement comando = conexao.prepareStatement("DELETE FROM lojista WHERE id_lojista = ?")) {
            comando.setLong(1, id);
            return comando.executeUpdate() > 0;
        }
    }

    /** Converte colunas selecionadas para o record e traduz dados invalidos em erro JDBC. */
    private static LojistaRegistro mapear(ResultSet resultado) throws SQLException {
        // getBigDecimal preserva o valor decimal recebido do banco.
        // Dados antigos que violem o contrato Java sao reportados como erro de leitura.
        try {
            return new LojistaRegistro(resultado.getLong("id_lojista"),
                    resultado.getString("nome_fantasia"), resultado.getBigDecimal("taxa_comissao_pct"),
                    resultado.getString("bairro_operacao"), resultado.getString("endereco_origem"));
        } catch (IllegalArgumentException e) {
            throw new SQLException("Registro de lojista incompativel com as validacoes Java.", "22000", e);
        }
    }

    /** Evita acessar os componentes do record quando a referencia recebida e null. */
    private static void exigirRegistro(LojistaRegistro lojista) {
        if (lojista == null) throw new IllegalArgumentException("O registro de lojista e obrigatorio.");
    }

    /** O Java recebe long, mas a chave correspondente no MySQL e INT positivo. */
    private static void validarId(long id) {
        if (id <= 0 || id > Integer.MAX_VALUE) throw new IllegalArgumentException("ID fora da faixa valida.");
    }
    // try-with-resources fecha comandos e resultados. A Connection permanece aberta
    // para permitir que UsuarioDao e LojistaDao participem da mesma transacao.
}
