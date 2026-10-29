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
 * Persiste os dados complementares do lojista usando a conexao do chamador.
 * Nao cria usuario, nao autoriza operacoes e nao executa commit ou rollback.
 * A FK exige usuario existente; conferir perfil LOJISTA cabe ao futuro servico.
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

    private static void exigirRegistro(LojistaRegistro lojista) {
        if (lojista == null) throw new IllegalArgumentException("O registro de lojista e obrigatorio.");
    }

    private static void validarId(long id) {
        if (id <= 0 || id > Integer.MAX_VALUE) throw new IllegalArgumentException("ID fora da faixa valida.");
    }
    // try-with-resources fecha comandos e resultados. A Connection permanece aberta
    // para permitir que UsuarioDao e LojistaDao participem da mesma transacao.
}
