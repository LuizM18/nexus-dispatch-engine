package com.nexusdispatch.service;

import com.nexusdispatch.dao.*;
import com.nexusdispatch.database.ConexaoBD;
import com.nexusdispatch.enums.PerfilUsuario;
import com.nexusdispatch.model.*;
import java.sql.*;
import java.util.*;

/**
 * CRUD de produto para o lojista informado pela camada de entrada. Confere perfil,
 * atividade e propriedade; selecionar um ID no console de demonstracao nao e login.
 * Cada chamada abre conexao dedicada e confirma/reverte sua propria transacao.
 */
public final class ProdutoService {
    @FunctionalInterface
    public interface FabricaConexao { Connection abrir() throws SQLException; }
    @FunctionalInterface
    private interface Operacao<T> { T executar(Connection c, ProdutoDao produtos) throws SQLException; }
    private final FabricaConexao fabrica;

    /** Conexao de producao usa a configuracao externa, sem credenciais no codigo. */
    public ProdutoService() { this(ConexaoBD::conectar); }

    /** Fabrica injetavel permite testes reais em catalogo descartavel. */
    public ProdutoService(FabricaConexao fabrica) {
        this.fabrica = Objects.requireNonNull(fabrica, "Fabrica obrigatoria.");
    }

    /** Consulta os dois cadastros e exercita a reconstituicao do perfil no fluxo real. */
    public Lojista consultarLojista(long idLojista) throws SQLException {
        return executar(idLojista, (c, produtos) -> carregarLojista(c, idLojista));
    }

    /** Recebe preco/peso/estoque validados pelo registro; nao aceita ID de produto novo. */
    public ProdutoRegistro cadastrar(ProdutoRegistro produto) throws SQLException {
        Objects.requireNonNull(produto, "Produto obrigatorio.");
        if (produto.id() != null) throw new IllegalArgumentException("Produto novo deve estar sem ID.");
        return executar(produto.idLojista(), (c, produtos) -> {
            long id = produtos.inserir(produto);
            return produtos.buscarPorId(id).orElseThrow(() -> new SQLException("Produto inserido nao encontrado."));
        });
    }

    /** Retorna somente produtos do lojista selecionado, com paginacao do DAO. */
    public List<ProdutoRegistro> listar(long idLojista, int limite, int deslocamento) throws SQLException {
        return executar(idLojista, (c, produtos) -> produtos.listarPorLojista(idLojista, limite, deslocamento));
    }

    /** Produto de outro lojista e tratado como indisponivel para este contexto. */
    public Optional<ProdutoRegistro> buscar(long idLojista, long idProduto) throws SQLException {
        validarId(idProduto);
        return executar(idLojista, (c, produtos) -> produtos.buscarPorId(idProduto)
                .filter(p -> p.idLojista() == idLojista));
    }

    /** Consulta o catalogo de venda por ID sem alterar estoque nem selecionar a loja como ator. */
    public Optional<ProdutoRegistro> buscarDisponivelParaCompra(long idProduto) throws SQLException {
        validarId(idProduto);
        return TransacaoJdbc.executar(fabrica::abrir, c -> {
            var produto = new ProdutoDao(c).buscarPorId(idProduto)
                    .filter(p -> Boolean.TRUE.equals(p.ativo()) && p.estoqueDisponivel() > 0);
            if (produto.isEmpty()) return Optional.empty();
            var loja = new UsuarioDao(c).buscarPorId(produto.orElseThrow().idLojista())
                    .filter(u -> u.perfil() == PerfilUsuario.LOJISTA && Boolean.TRUE.equals(u.ativo()));
            return loja.isPresent() ? produto : Optional.empty();
        });
    }

    /** Atualiza somente cadastro/preco/peso; saldo e atividade possuem fluxos separados. */
    public boolean atualizar(ProdutoRegistro produto) throws SQLException {
        Objects.requireNonNull(produto, "Produto obrigatorio.");
        if (produto.id() == null) throw new IllegalArgumentException("ID obrigatorio para atualizar.");
        return executar(produto.idLojista(), (c, produtos) -> produtos.atualizarCadastro(produto));
    }

    /** Reposicao positiva e atomica; nao altera campos cadastrais nem gera historico ficticio. */
    public boolean reporEstoque(long idLojista, long idProduto, int quantidade) throws SQLException {
        validarId(idProduto);
        if (quantidade <= 0) throw new IllegalArgumentException("Reposicao deve ser positiva.");
        return executar(idLojista, (c, produtos) -> produtos.reporEstoque(idProduto, idLojista, quantidade));
    }

    /** Desativar retira de venda e preserva itens/historico existentes. */
    public boolean definirAtivo(long idLojista, long idProduto, boolean ativo) throws SQLException {
        validarId(idProduto);
        return executar(idLojista, (c, produtos) -> produtos.definirAtivo(idProduto, idLojista, ativo));
    }

    /**
     * DELETE real apenas sem item de compra vinculado. Bloqueia produto antes de
     * conferir historico: insercao concorrente com FK tambem precisa dessa linha.
     * FK do banco continua como protecao final. Nao depende de ON DELETE CASCADE.
     */
    public boolean excluir(long idLojista, long idProduto) throws SQLException {
        validarId(idProduto);
        return executar(idLojista, (c, produtos) -> produtos.excluirSemHistorico(idProduto, idLojista));
    }

    /** Executa todas as verificacoes e escrita na conexao dedicada; erros preservam rollback. */
    private <T> T executar(long idLojista, Operacao<T> operacao) throws SQLException {
        validarId(idLojista);
        try (Connection c = fabrica.abrir()) {
            if (!c.getAutoCommit()) throw new SQLException("Use conexao nova sem transacao em andamento.");
            c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            c.setAutoCommit(false);
            try {
                carregarLojista(c, idLojista);
                // O callback executa a operacao especifica com o DAO desta conexao.
                // O tipo generico T conserva o retorno de cada operacao sem conversoes manuais.
                T resultado = operacao.executar(c, new ProdutoDao(c));
                c.commit();
                return resultado;
            } catch (SQLException | RuntimeException erro) {
                try { c.rollback(); } catch (SQLException falha) { erro.addSuppressed(falha); }
                throw erro;
            }
        }
    }

    /** Perfil/atividade sao conferidos a cada operacao, nao somente ao escolher o ID. */
    private static Lojista carregarLojista(Connection c, long id) throws SQLException {
        var usuario = new UsuarioDao(c).buscarPorId(id).orElseThrow(
                () -> new IllegalArgumentException("Lojista nao encontrado."));
        if (usuario.perfil() != PerfilUsuario.LOJISTA || !Boolean.TRUE.equals(usuario.ativo()))
            throw new IllegalArgumentException("Usuario deve ser LOJISTA ativo.");
        var complemento = new LojistaDao(c).buscarPorId(id).orElseThrow(
                () -> new IllegalArgumentException("Cadastro comercial do lojista ausente."));
        return new Lojista(usuario, complemento);
    }

    /** Java long deve caber na chave INT positiva do banco. */
    private static void validarId(long id) {
        if (id < 1 || id > Integer.MAX_VALUE) throw new IllegalArgumentException("ID fora da faixa INT positiva.");
    }
}
