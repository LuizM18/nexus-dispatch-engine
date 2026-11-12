package com.nexusdispatch.dao;

import com.nexusdispatch.enums.StatusPedidoMestre;
import com.nexusdispatch.model.PedidoMestreRegistro;
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
 * Acessa a tabela pedido_mestre: grava dados Java e converte linhas em registros.
 * O service verifica permissoes/perfil do cliente, calcula os valores reais e
 * coordena pagamento, estoque, itens e entregas na mesma transacao.
 * Quem fornece a Connection deve fecha-la e decidir commit/rollback.
 * Este DAO fecha somente os comandos e resultados que ele proprio cria.
 */
public final class PedidoMestreDao {
    // Nomes reais das colunas. Esta constante nao contem entrada do usuario.
    private static final String COLUNAS = "id_pedido, id_cliente, data_criacao, "
            + "valor_produtos, valor_total, status_pedido";

    // final impede substituir a referencia; a conexao continua podendo executar SQL.
    private final Connection conexao;

    /** Recebe a conexao compartilhada pelos DAOs que participam da compra. */
    public PedidoMestreDao(Connection conexao) {
        this.conexao = Objects.requireNonNull(conexao, "A conexao e obrigatoria.");
    }

    /**
     * Insere uma compra nova e retorna outro registro com ID e data do banco.
     * O objeto recebido permanece intacto. O service deve usar uma transacao
     * para poder reverter a compra caso alguma etapa, inclusive a leitura, falhe.
     */
    public PedidoMestreRegistro inserir(PedidoMestreRegistro pedido) throws SQLException {
        if (pedido == null) {
            throw new IllegalArgumentException("O pedido mestre e obrigatorio.");
        }
        // ID null identifica um registro ainda nao persistido.
        if (pedido.id() != null) {
            throw new IllegalArgumentException("Um pedido novo deve ter ID null.");
        }
        // Recusa uma data fornecida para nao ignora-la silenciosamente.
        if (pedido.dataCriacao() != null) {
            throw new IllegalArgumentException("A data de criacao sera gerada pelo banco.");
        }
        if (pedido.statusPedido() != StatusPedidoMestre.CRIADO) {
            throw new IllegalArgumentException("Um pedido novo deve comecar no estado CRIADO.");
        }

        // O record ja validou ID do cliente e valores. Omitimos ID e data para
        // aplicar AUTO_INCREMENT e CURRENT_TIMESTAMP definidos na tabela.
        exigirTransacao();
        String sql = "INSERT INTO pedido_mestre "
                + "(id_cliente, valor_produtos, valor_total, status_pedido) VALUES (?, ?, ?, ?)";
        long idGerado;
        // RETURN_GENERATED_KEYS solicita a chave gerada. try-with-resources
        // fecha o comando mesmo quando ocorre uma excecao.
        try (PreparedStatement comando = conexao.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            // Indices comecam em 1, na ordem dos ?. Valores ficam separados do SQL;
            // setBigDecimal preserva os decimais sem conversao para double.
            comando.setLong(1, pedido.idCliente());
            comando.setBigDecimal(2, pedido.valorProdutos());
            comando.setBigDecimal(3, pedido.valorTotal());
            comando.setString(4, pedido.statusPedido().name());
            if (comando.executeUpdate() != 1) {
                throw new SQLException("A insercao nao registrou um pedido mestre.");
            }
            try (ResultSet chaves = comando.getGeneratedKeys()) {
                // O cursor inicia antes da primeira linha; next() avanca ate a chave.
                if (!chaves.next()) throw new SQLException("O banco nao retornou o ID gerado.");
                idGerado = chaves.getLong(1);
            }
        }
        // Recupera a data efetivamente armazenada, em vez de usar o relogio Java.
        return buscarPorId(idGerado).orElseThrow(
                () -> new SQLException("O pedido foi inserido, mas nao encontrado na leitura."));
    }

    /** Optional vazio indica ausencia. Falha de conexao/SQL continua sendo excecao. */
    public Optional<PedidoMestreRegistro> buscarPorId(long idPedido) throws SQLException {
        return buscarPorId(idPedido, false);
    }

    /** Bloqueio compartilhado com os fluxos de preparo, entrega e nova tentativa. */
    public Optional<PedidoMestreRegistro> buscarPorIdParaAtualizacao(long idPedido) throws SQLException {
        exigirTransacao();
        return buscarPorId(idPedido, true);
    }

    private Optional<PedidoMestreRegistro> buscarPorId(long idPedido, boolean bloquear) throws SQLException {
        validarId(idPedido);
        String sql = "SELECT " + COLUNAS + " FROM pedido_mestre WHERE id_pedido = ?"
                + (bloquear ? " FOR UPDATE" : "");
        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setLong(1, idPedido);
            try (ResultSet resultado = comando.executeQuery()) {
                if (!resultado.next()) return Optional.empty();
                return Optional.of(mapear(resultado));
            }
        }
    }

    /**
     * Lista compras recentes primeiro. limite e o tamanho da pagina;
     * deslocamento e quantas linhas pular (10 e 20 buscam a terceira pagina).
     * O service deve autorizar o acesso ao historico do cliente informado.
     */
    public List<PedidoMestreRegistro> listarPorCliente(long idCliente, int limite, int deslocamento)
            throws SQLException {
        validarId(idCliente);
        // Evita carregar todo o historico de uma vez.
        if (limite < 1 || limite > 100 || deslocamento < 0) {
            throw new IllegalArgumentException("Limite deve ser de 1 a 100 e deslocamento nao negativo.");
        }
        // O ID desempata datas iguais. No MySQL, datas NULL ficam por ultimo em DESC.
        String sql = "SELECT " + COLUNAS + " FROM pedido_mestre WHERE id_cliente = ? "
                + "ORDER BY data_criacao DESC, id_pedido DESC LIMIT ? OFFSET ?";
        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setLong(1, idCliente);
            comando.setInt(2, limite);
            comando.setInt(3, deslocamento);
            try (ResultSet resultado = comando.executeQuery()) {
                List<PedidoMestreRegistro> pedidos = new ArrayList<>();
                while (resultado.next()) pedidos.add(mapear(resultado));
                // Lista vazia se nada foi encontrado; a copia impede adicionar/remover
                // elementos pelo chamador. Isso nao modifica os registros no banco.
                return List.copyOf(pedidos);
            }
        }
    }

    /**
     * Grava a mudanca somente se o estado ainda for o esperado. Por exemplo, uma
     * tentativa CRIADO -> PAGO falha se outra operacao ja gravou CANCELADO.
     * O service decide quais transicoes sao permitidas pelas regras de negocio.
     */
    public boolean atualizarStatus(long idPedido, StatusPedidoMestre statusEsperado,
            StatusPedidoMestre novoStatus) throws SQLException {
        validarId(idPedido);
        if (statusEsperado == null || novoStatus == null) {
            throw new IllegalArgumentException("Os estados esperado e novo sao obrigatorios.");
        }
        if (statusEsperado == novoStatus) {
            throw new IllegalArgumentException("O novo estado deve ser diferente do esperado.");
        }
        // Verifica e altera no mesmo UPDATE para nao sobrescrever uma mudanca
        // concorrente com base em uma leitura antiga.
        exigirTransacao();
        String sql = "UPDATE pedido_mestre SET status_pedido = ? "
                + "WHERE id_pedido = ? AND status_pedido = ?";
        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, novoStatus.name());
            comando.setLong(2, idPedido);
            comando.setString(3, statusEsperado.name());
            // false indica ID ausente ou estado diferente; erros SQL sao propagados.
            return comando.executeUpdate() == 1;
        }
    }

    /** Converte a linha atual do ResultSet sem avancar o cursor ou consultar novamente. */
    private static PedidoMestreRegistro mapear(ResultSet resultado) throws SQLException {
        // A coluna permite SQL NULL. LocalDateTime nao armazena fuso horario;
        // a aplicacao deve manter uma configuracao consistente nas conexoes.
        Timestamp data = resultado.getTimestamp("data_criacao");
        try {
            // O construtor do record reaplica as validacoes aos dados armazenados.
            return new PedidoMestreRegistro(resultado.getLong("id_pedido"),
                    resultado.getLong("id_cliente"), data == null ? null : data.toLocalDateTime(),
                    resultado.getBigDecimal("valor_produtos"), resultado.getBigDecimal("valor_total"),
                    StatusPedidoMestre.valueOf(resultado.getString("status_pedido")));
        } catch (IllegalArgumentException | NullPointerException erro) {
            // SQLState 22000 indica erro de dados. Preservamos a causa original.
            throw new SQLException("Pedido armazenado incompativel com o modelo Java.", "22000", erro);
        }
    }

    /** A API recebe long, mas a coluna MySQL usa INT: conferimos sua faixa positiva. */
    private static void validarId(long id) {
        if (id <= 0 || id > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("O ID deve estar na faixa INT positiva.");
        }
    }
    /** Escritas participam da transacao controlada pelo service. */
    private void exigirTransacao() throws SQLException {
        if (conexao.getAutoCommit()) {
            throw new SQLException("A escrita exige transacao explicita.");
        }
    }

}
