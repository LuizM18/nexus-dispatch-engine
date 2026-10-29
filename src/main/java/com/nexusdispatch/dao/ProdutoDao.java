package com.nexusdispatch.dao;

import com.nexusdispatch.model.ProdutoRegistro;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * DAO significa Data Access Object: concentra o acesso a tabela produto.
 * Recebe objetos Java, executa SQL e converte resultados em ProdutoRegistro.
 *
 * O chamador abre e fecha a Connection e decide quando confirmar ou reverter
 * a transacao. Assim, varios DAOs podem participar de uma mesma compra.
 * Este DAO nao verifica quem esta autenticado: autorizacao pertence ao service.
 * final impede criar subclasses de ProdutoDao; nao torna a Connection imutavel.
 */
public final class ProdutoDao {
    // Lista fixa reutilizada pelos SELECTs. Evita SELECT * e deixa explicito
    // quais dados serao lidos. A concatenacao desta constante nao usa entrada externa.
    private static final String COLUNAS = "id_produto, id_lojista, nome, categoria, descricao, "
            + "preco_unitario, peso_kg, estoque_disponivel, status_ativo";
    // final impede substituir a referencia. A conexao continua sendo um recurso
    // aberto cujo ciclo de vida pertence a quem criou o DAO.
    private final Connection conexao;

    public ProdutoDao(Connection conexao) {
        // this.conexao e o campo do objeto; conexao e o parametro recebido.
        // requireNonNull falha imediatamente se tentarem criar um DAO sem conexao.
        this.conexao = Objects.requireNonNull(conexao, "Conexao obrigatoria.");
    }

    /** Insere um produto novo e devolve o ID gerado; nao modifica o record recebido. */
    public long inserir(ProdutoRegistro produto) throws SQLException {
        // O record ja validou seus campos. Aqui conferimos se o objeto foi fornecido
        // e se representa um cadastro novo, ainda sem chave gerada pelo banco.
        exigir(produto);
        if (produto.id() != null) throw new IllegalArgumentException("Produto novo deve ter ID null.");
        // PreparedStatement vincula valores aos ?; apostrofos no nome sao dados,
        // nao instrucoes SQL. RETURN_GENERATED_KEYS solicita o ID do AUTO_INCREMENT.
        // try-with-resources fecha o comando s mesmo se ocorrer uma excecao.
        try (PreparedStatement s = conexao.prepareStatement(
                "INSERT INTO produto (id_lojista, nome, categoria, descricao, preco_unitario, peso_kg, "
                        + "estoque_disponivel, status_ativo) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            // Os indices comecam em 1 e seguem exatamente a ordem dos ? no INSERT.
            // Cada setter informa o tipo do valor; setBigDecimal preserva os decimais.
            s.setLong(1, produto.idLojista());
            s.setString(2, produto.nome());
            s.setString(3, produto.categoria());
            s.setString(4, produto.descricao());
            s.setBigDecimal(5, produto.precoUnitario());
            s.setBigDecimal(6, produto.pesoKg());
            s.setInt(7, produto.estoqueDisponivel());
            // NULL e diferente de false. setNull envia ausencia explicitamente,
            // portanto o valor padrao TRUE da coluna nao e aplicado nesse caso.
            if (produto.ativo() == null) s.setNull(8, Types.BOOLEAN);
            else s.setBoolean(8, produto.ativo());
            // executeUpdate e usado para INSERT/UPDATE/DELETE e retorna a quantidade
            // de linhas reportada pelo driver. Este INSERT deve inserir uma linha.
            if (s.executeUpdate() != 1) throw new SQLException("Produto nao inserido.");
            try (ResultSet r = s.getGeneratedKeys()) {
                // O cursor inicia antes da primeira linha. next() avanca para a chave;
                // getLong(1) le a primeira coluna desse resultado, nao a tabela inteira.
                if (!r.next()) throw new SQLException("ID gerado nao retornado.");
                return r.getLong(1);
            }
        }
    }

    /** Retorna o produto encontrado ou Optional.empty(); erros JDBC sao propagados. */
    public Optional<ProdutoRegistro> buscarPorId(long id) throws SQLException {
        validarId(id);
        try (PreparedStatement s = conexao.prepareStatement("SELECT " + COLUNAS + " FROM produto WHERE id_produto = ?")) {
            // Vincula o identificador sem monta-lo dentro do texto SQL.
            s.setLong(1, id);
            try (ResultSet r = s.executeQuery()) {
                // executeQuery e usado para SELECT. A chave primaria identifica no
                // maximo uma linha. mapear converte essa linha para um record Java.
                // Optional vazio significa "nao encontrado", nao "erro de conexao".
                return r.next() ? Optional.of(mapear(r)) : Optional.empty();
            }
        }
    }

    /**
     * Lista uma pagina de produtos de um lojista, incluindo ativos e inativos.
     * limite e o tamanho da pagina; deslocamento e quantas linhas pular.
     * Exemplo: limite 10 e deslocamento 20 busca a terceira pagina de dez itens.
     */
    public List<ProdutoRegistro> listarPorLojista(long idLojista, int limite, int deslocamento) throws SQLException {
        validarId(idLojista);
        // Evita consultas sem limite e valores de paginacao sem sentido.
        if (limite < 1 || limite > 100 || deslocamento < 0) throw new IllegalArgumentException("Paginacao invalida.");
        try (PreparedStatement s = conexao.prepareStatement("SELECT " + COLUNAS
                + " FROM produto WHERE id_lojista = ? ORDER BY id_produto LIMIT ? OFFSET ?")) {
            s.setLong(1, idLojista);
            s.setInt(2, limite);
            s.setInt(3, deslocamento);
            try (ResultSet r = s.executeQuery()) {
                // A lista temporaria pode crescer enquanto percorremos as linhas.
                // ORDER BY id_produto define a ordem usada para montar a pagina.
                List<ProdutoRegistro> produtos = new ArrayList<>();
                while (r.next()) produtos.add(mapear(r));
                // Entrega uma copia imutavel: o chamador nao pode adicionar ou remover
                // elementos desta lista. Sem resultados, devolve uma lista vazia.
                return List.copyOf(produtos);
            }
        }
    }

    /**
     * Altera nome, categoria, descricao, preco e peso de um cadastro existente.
     * Nao troca o lojista nem grava estoque/status do record: esses dados podem
     * estar desatualizados e possuem operacoes proprias para alteracao.
     */
    public boolean atualizarCadastro(ProdutoRegistro produto) throws SQLException {
        exigir(produto);
        if (produto.id() == null) throw new IllegalArgumentException("ID obrigatorio para atualizar.");
        try (PreparedStatement s = conexao.prepareStatement("UPDATE produto SET nome = ?, categoria = ?, descricao = ?, "
                + "preco_unitario = ?, peso_kg = ? WHERE id_produto = ? AND id_lojista = ?")) {
            s.setString(1, produto.nome());
            s.setString(2, produto.categoria());
            s.setString(3, produto.descricao());
            s.setBigDecimal(4, produto.precoUnitario());
            s.setBigDecimal(5, produto.pesoKg());
            // Os dois ultimos parametros restringem a alteracao ao produto E ao
            // lojista informados. Isso nao substitui autorizacao pelo service.
            s.setLong(6, produto.id());
            s.setLong(7, produto.idLojista());
            // true significa que o driver reportou linha afetada/encontrada.
            // A contagem de UPDATE sem mudanca depende da configuracao do driver.
            return s.executeUpdate() > 0;
        }
    }

    /** Ativa ou desativa sem apagar o produto e sem modificar seu estoque. */
    public boolean definirAtivo(long id, long idLojista, boolean ativo) throws SQLException {
        validarId(id); validarId(idLojista);
        try (PreparedStatement s = conexao.prepareStatement(
                "UPDATE produto SET status_ativo = ? WHERE id_produto = ? AND id_lojista = ?")) {
            s.setBoolean(1, ativo); s.setLong(2, id); s.setLong(3, idLojista);
            return s.executeUpdate() > 0;
        }
    }

    /**
     * Subtrai estoque somente de produto ativo com saldo suficiente.
     * A condicao e a baixa ficam no mesmo UPDATE, evitando decidir com um saldo
     * lido anteriormente. O futuro checkout ainda precisa de transacao e rollback.
     */
    public boolean baixarEstoque(long id, long idLojista, int quantidade) throws SQLException {
        validarMovimento(id, idLojista, quantidade);
        try (PreparedStatement s = conexao.prepareStatement("UPDATE produto SET estoque_disponivel = estoque_disponivel - ? "
                + "WHERE id_produto = ? AND id_lojista = ? AND status_ativo = TRUE AND estoque_disponivel >= ?")) {
            // A quantidade aparece duas vezes: primeiro na subtracao, depois na
            // verificacao do saldo. >= permite vender exatamente todo o estoque.
            // status_ativo = TRUE tambem exclui produtos cujo status seja NULL.
            s.setInt(1, quantidade); s.setLong(2, id); s.setLong(3, idLojista); s.setInt(4, quantidade);
            // false pode indicar saldo insuficiente, inatividade, ID inexistente
            // ou lojista divergente; o retorno nao identifica sozinho qual motivo.
            return s.executeUpdate() > 0;
        }
    }

    /** Soma estoque, inclusive de produto inativo, sem exceder a capacidade de INT. */
    public boolean reporEstoque(long id, long idLojista, int quantidade) throws SQLException {
        validarMovimento(id, idLojista, quantidade);
        try (PreparedStatement s = conexao.prepareStatement("UPDATE produto SET estoque_disponivel = estoque_disponivel + ? "
                + "WHERE id_produto = ? AND id_lojista = ? AND estoque_disponivel <= ?")) {
            s.setInt(1, quantidade); s.setLong(2, id); s.setLong(3, idLojista);
            // Antes de somar, exige saldo <= maximo - quantidade. Essa forma evita
            // overflow e mantem a verificacao junto da alteracao no banco.
            s.setInt(4, Integer.MAX_VALUE - quantidade);
            return s.executeUpdate() > 0;
        }
    }

    /**
     * Apaga fisicamente o produto identificado pelo par produto/lojista.
     * Se um item de pedido o referenciar com FK restritiva, o MySQL recusa e
     * SQLException chega ao chamador. Para retirar de venda, existe definirAtivo.
     */
    public boolean excluir(long id, long idLojista) throws SQLException {
        validarId(id); validarId(idLojista);
        try (PreparedStatement s = conexao.prepareStatement("DELETE FROM produto WHERE id_produto = ? AND id_lojista = ?")) {
            s.setLong(1, id); s.setLong(2, idLojista);
            return s.executeUpdate() > 0;
        }
    }

    /** Le a linha atual do ResultSet; nao avanca o cursor nem executa outra consulta. */
    private static ProdutoRegistro mapear(ResultSet r) throws SQLException {
        // getBoolean devolve false tanto para FALSE quanto para SQL NULL.
        // wasNull precisa ser chamado logo depois para distinguir os dois casos.
        boolean ativo = r.getBoolean("status_ativo");
        Boolean status = r.wasNull() ? null : ativo;
        try {
            // Os nomes correspondem as colunas do SELECT. O construtor do record
            // reaplica as validacoes, inclusive em dados antigos existentes no banco.
            return new ProdutoRegistro(r.getLong("id_produto"), r.getLong("id_lojista"), r.getString("nome"),
                    r.getString("categoria"), r.getString("descricao"), r.getBigDecimal("preco_unitario"),
                    r.getBigDecimal("peso_kg"), r.getInt("estoque_disponivel"), status);
        } catch (IllegalArgumentException e) {
            // Um dado armazenado invalido e erro de leitura, nao cadastro ausente.
            // SQLState 22000 indica erro de dados; a causa original e preservada.
            throw new SQLException("Produto armazenado incompativel com as validacoes Java.", "22000", e);
        }
    }
    // Evita chamar acessores, como produto.id(), sobre uma referencia inexistente.
    private static void exigir(ProdutoRegistro produto) {
        if (produto == null) throw new IllegalArgumentException("Produto obrigatorio.");
    }
    // Os parametros sao long no Java, mas os identificadores no MySQL sao INT.
    private static void validarId(long id) {
        if (id <= 0 || id > Integer.MAX_VALUE) throw new IllegalArgumentException("ID fora da faixa INT.");
    }
    // Reutiliza as mesmas regras na baixa e reposicao. Quantidade negativa poderia
    // inverter o sentido da operacao; zero nao representa uma movimentacao valida.
    private static void validarMovimento(long id, long idLojista, int quantidade) {
        validarId(id); validarId(idLojista);
        if (quantidade <= 0) throw new IllegalArgumentException("Quantidade deve ser positiva.");
    }
}
