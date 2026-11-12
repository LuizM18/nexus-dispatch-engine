package com.nexusdispatch.dao;

import java.sql.*;
import java.util.*;
import com.nexusdispatch.model.OrdemEntregaRegistro;
import com.nexusdispatch.enums.StatusOrdemEntrega;

/**
 * Consulta ordens ativas e persiste o aceite de uma ordem de entrega.
 *
 * Os metodos usam o schema atual (ordem_entrega ligada a comanda_lojista).
 * O chamador controla e fecha a Connection e decide commit/rollback. O aceite
 * precisa ser atomico com a reserva do entregador; por isso ambos os DAOs devem
 * receber a mesma Connection dentro da mesma transacao.
 */
public final class OrdemEntregaDao {
    private static final String COLUNAS = "id_entrega,id_comanda,id_entregador,modalidade_frete,distancia_km,"
            + "valor_repasse_entregador,taxa_frete_retida,status_entrega,data_hora_coleta,data_hora_entrega";
    private final Connection conexao;

    /** Recebe a conexao do service para compartilhar a transacao do aceite. */
    public OrdemEntregaDao(Connection conexao) {
        this.conexao = Objects.requireNonNull(conexao, "Conexao obrigatoria.");
    }

    /**
     * Verifica se o entregador possui uma ordem ativa, bloqueando as linhas lidas.
     * Deve ser chamada dentro da transacao e depois de bloquear o entregador.
     * Esse segundo bloqueio tambem protege contra dados historicos incoerentes.
     */
    public boolean possuiOrdemAtiva(long idEntregador) throws SQLException {
        validarId(idEntregador);
        exigirTransacao();
        // FOR UPDATE evita que outra transacao altere as linhas ativas ate o fim
        // desta transacao; ele nao substitui a verificacao inicial do service.
        try (PreparedStatement s = conexao.prepareStatement(
                "SELECT id_entrega FROM ordem_entrega WHERE id_entregador=? "
                + "AND status_entrega IN ('AGUARDANDO_COLETA','EM_TRANSITO') FOR UPDATE")) {
            s.setLong(1, idEntregador);
            try (ResultSet r = s.executeQuery()) { return r.next(); }
        }
    }

    /**
     * Atribui a ordem somente se ainda estiver sem entregador e aguardando coleta.
     * A ordem e a comanda precisam estar no estado esperado. O UPDATE condicional
     * faz uma disputa concorrente resultar em false; nao muda status nem datas,
     * pois aceitar a ordem nao significa que a coleta ja ocorreu.
     */
    public boolean vincularDisponivel(long idEntrega, long idEntregador) throws SQLException {
        validarId(idEntrega); validarId(idEntregador);
        exigirTransacao();
        String sql = "UPDATE ordem_entrega o JOIN comanda_lojista c ON c.id_comanda=o.id_comanda "
                + "SET o.id_entregador=? WHERE o.id_entrega=? AND o.id_entregador IS NULL "
                + "AND o.status_entrega='AGUARDANDO_COLETA' AND c.status_comanda='AGUARDANDO_COLETA'";
        try (PreparedStatement s = conexao.prepareStatement(sql)) {
            s.setLong(1, idEntregador); s.setLong(2, idEntrega);
            return s.executeUpdate() == 1;
        }
    }

    /** Bloqueios e aceite sem transacao poderiam liberar uma reserva cedo demais. */
    private void exigirTransacao() throws SQLException {
        if (conexao.getAutoCommit()) throw new SQLException("Aceite exige transacao explicita.");
    }

    /**
     * Cria uma ordem por comanda. O bloqueio do pai serializa insercoes por este DAO,
     * pois o SQL atual nao possui UNIQUE em id_comanda. SQL externo pode ignorar isso.
     */
    public OrdemEntregaRegistro inserir(OrdemEntregaRegistro ordem) throws SQLException {
        Objects.requireNonNull(ordem);
        if(ordem.id()!=null || ordem.idEntregador()!=null || ordem.dataHoraColeta()!=null
                || ordem.dataHoraEntrega()!=null || ordem.statusEntrega()!=StatusOrdemEntrega.AGUARDANDO_COLETA)
            throw new IllegalArgumentException("Nova ordem exige dados iniciais sem ID, entregador ou datas.");
        exigirTransacao();
        try(PreparedStatement s=conexao.prepareStatement("SELECT status_comanda FROM comanda_lojista WHERE id_comanda=? FOR UPDATE")){
            s.setLong(1,ordem.idComanda());
            try(ResultSet r=s.executeQuery()){
                if(!r.next() || !"PAGO".equals(r.getString(1)))throw new SQLException("Comanda deve existir e estar PAGO.");
            }
        }
        // Leitura corrente apos bloquear o pai evita duplicacao entre duas conexoes.
        try(PreparedStatement s=conexao.prepareStatement("SELECT id_entrega FROM ordem_entrega WHERE id_comanda=? FOR UPDATE")){
            s.setLong(1,ordem.idComanda());
            try(ResultSet r=s.executeQuery()){if(r.next())throw new SQLException("Comanda ja possui ordem.");}
        }
        String sql="INSERT INTO ordem_entrega (id_comanda,modalidade_frete,distancia_km,"
                +"valor_repasse_entregador,taxa_frete_retida,status_entrega) VALUES (?,?,?,?,?,'AGUARDANDO_COLETA')";
        try(PreparedStatement s=conexao.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){
            s.setLong(1,ordem.idComanda());s.setString(2,ordem.modalidadeFrete());s.setBigDecimal(3,ordem.distanciaKm());
            s.setBigDecimal(4,ordem.valorRepasseEntregador());s.setBigDecimal(5,ordem.taxaFreteRetida());
            if(s.executeUpdate()!=1)throw new SQLException("Ordem nao inserida.");
            try(ResultSet r=s.getGeneratedKeys()){
                if(!r.next())throw new SQLException("Chave nao retornada.");long id=r.getLong(1);
                if(r.wasNull() || id<1 || id>Integer.MAX_VALUE)throw new SQLException("Chave invalida.");
                return buscarPorId(id).orElseThrow(()->new SQLException("Ordem nao encontrada apos inserir."));
            }
        }
    }
    // Leitura comum sem reserva: o registro retornado e um snapshot. Para decidir
    // uma transicao concorrente, o service usa os bloqueios e UPDATEs condicionais.
    public Optional<OrdemEntregaRegistro> buscarPorId(long id)throws SQLException{
        validarId(id);
        try(PreparedStatement s=conexao.prepareStatement("SELECT "+COLUNAS+" FROM ordem_entrega WHERE id_entrega=?")){
            s.setLong(1,id);try(ResultSet r=s.executeQuery()){return r.next()?Optional.of(mapear(r)):Optional.empty();}
        }
    }
    /** Leitura corrente da ordem; o service bloqueia antes entregador e pedido. */
    public Optional<OrdemEntregaRegistro> buscarPorIdParaAtualizacao(long id) throws SQLException {
        validarId(id); exigirTransacao();
        try (PreparedStatement s = conexao.prepareStatement("SELECT " + COLUNAS
                + " FROM ordem_entrega WHERE id_entrega=? FOR UPDATE")) {
            s.setLong(1, id);
            try (ResultSet r = s.executeQuery()) { return r.next() ? Optional.of(mapear(r)) : Optional.empty(); }
        }
    }

    /** Manifesto ativo completo, ordenado para coleta e bloqueado na transacao. */
    public List<OrdemEntregaRegistro> listarAtivasParaAtualizacao(long idEntregador) throws SQLException {
        validarId(idEntregador); exigirTransacao();
        try (PreparedStatement s = conexao.prepareStatement("SELECT " + COLUNAS
                + " FROM ordem_entrega WHERE id_entregador=? AND status_entrega IN "
                + "('AGUARDANDO_COLETA','EM_TRANSITO') ORDER BY id_entrega FOR UPDATE")) {
            s.setLong(1, idEntregador);
            try (ResultSet r = s.executeQuery()) {
                List<OrdemEntregaRegistro> ordens = new ArrayList<>();
                while (r.next()) ordens.add(mapear(r));
                return List.copyOf(ordens);
            }
        }
    }

    /** Devolve somente a atribuicao anterior a coleta, preservando a ordem e o frete. */
    public boolean desvincularAntesDaColeta(long id, long idEntregador) throws SQLException {
        validarId(id); validarId(idEntregador); exigirTransacao();
        try (PreparedStatement s = conexao.prepareStatement("UPDATE ordem_entrega SET id_entregador=NULL "
                + "WHERE id_entrega=? AND id_entregador=? AND status_entrega='AGUARDANDO_COLETA' "
                + "AND data_hora_coleta IS NULL AND data_hora_entrega IS NULL")) {
            s.setLong(1, id); s.setLong(2, idEntregador); return s.executeUpdate() == 1;
        }
    }
    /** Lista paginada tambem permite inspecionar duplicatas preexistentes no banco. */
    public List<OrdemEntregaRegistro> listarPorComanda(long idComanda,int limite,int deslocamento)throws SQLException{
        validarId(idComanda);
        if(limite<1||limite>100||deslocamento<0)throw new IllegalArgumentException("Paginacao invalida.");
        try(PreparedStatement s=conexao.prepareStatement("SELECT "+COLUNAS+" FROM ordem_entrega WHERE id_comanda=? ORDER BY id_entrega LIMIT ? OFFSET ?")){
            s.setLong(1,idComanda);s.setInt(2,limite);s.setInt(3,deslocamento);
            try(ResultSet r=s.executeQuery()){
                List<OrdemEntregaRegistro> ordens=new ArrayList<>();while(r.next())ordens.add(mapear(r));return List.copyOf(ordens);
            }
        }
    }
    /** Service deve validar ator e coordenar comanda/pedido na mesma transacao. */
    public boolean registrarColeta(long id,long idEntregador)throws SQLException{
        validarId(id);validarId(idEntregador);exigirTransacao();
        // Coleta grava estado e instante juntos, apenas para o entregador vinculado
        // e a comanda pronta. false significa que as condicoes nao casaram; o service
        // ainda precisa atualizar a comanda na mesma transacao.
        String sql="UPDATE ordem_entrega o JOIN comanda_lojista c ON c.id_comanda=o.id_comanda "
                +"SET o.status_entrega='EM_TRANSITO',o.data_hora_coleta=CURRENT_TIMESTAMP "
                +"WHERE o.id_entrega=? AND o.id_entregador=? AND o.status_entrega='AGUARDANDO_COLETA' "
                +"AND o.data_hora_coleta IS NULL AND o.data_hora_entrega IS NULL AND c.status_comanda='AGUARDANDO_COLETA'";
        try(PreparedStatement s=conexao.prepareStatement(sql)){s.setLong(1,id);s.setLong(2,idEntregador);return s.executeUpdate()==1;}
    }
    /** Nao libera entregador sozinho: o service deve atualizar sua disponibilidade. */
    public boolean concluirEntrega(long id,long idEntregador)throws SQLException{
        validarId(id);validarId(idEntregador);exigirTransacao();
        String sql="UPDATE ordem_entrega o JOIN comanda_lojista c ON c.id_comanda=o.id_comanda "
                +"SET o.status_entrega='ENTREGUE',o.data_hora_entrega=CURRENT_TIMESTAMP "
                +"WHERE o.id_entrega=? AND o.id_entregador=? AND o.status_entrega='EM_TRANSITO' "
                +"AND o.data_hora_coleta IS NOT NULL AND o.data_hora_entrega IS NULL "
                +"AND o.data_hora_coleta<=CURRENT_TIMESTAMP AND c.status_comanda='EM_TRANSITO'";
        try(PreparedStatement s=conexao.prepareStatement(sql)){s.setLong(1,id);s.setLong(2,idEntregador);return s.executeUpdate()==1;}
    }
    /**
     * Cancela somente esta ordem antes da coleta. O service deve verificar TODAS as
     * ordens da compra, bloquear coleta concorrente e liberar entregador reservado.
     * Nao representa cancelamento integral nem estorno financeiro.
     */
    public boolean cancelarAntesDaColeta(long id)throws SQLException{
        validarId(id);exigirTransacao();
        try(PreparedStatement s=conexao.prepareStatement("UPDATE ordem_entrega SET status_entrega='CANCELADO' "
                +"WHERE id_entrega=? AND status_entrega='AGUARDANDO_COLETA' AND data_hora_coleta IS NULL AND data_hora_entrega IS NULL")){
            s.setLong(1,id);return s.executeUpdate()==1;
        }
    }
    // getLong retorna zero para SQL NULL; wasNull recupera essa distincao para
    // idEntregador. Datas ausentes permanecem null; nenhuma data e inventada na leitura.
    // Construtor/enums invalidos viram SQLException com a causa para diagnostico.
    private static OrdemEntregaRegistro mapear(ResultSet r)throws SQLException{
        try{
            long entregador=r.getLong("id_entregador");Long idEntregador=r.wasNull()?null:entregador;
            Timestamp coleta=r.getTimestamp("data_hora_coleta"),entrega=r.getTimestamp("data_hora_entrega");
            return new OrdemEntregaRegistro(r.getLong("id_entrega"),r.getLong("id_comanda"),idEntregador,
                    r.getString("modalidade_frete"),r.getBigDecimal("distancia_km"),r.getBigDecimal("valor_repasse_entregador"),
                    r.getBigDecimal("taxa_frete_retida"),StatusOrdemEntrega.valueOf(r.getString("status_entrega")),
                    coleta==null?null:coleta.toLocalDateTime(),entrega==null?null:entrega.toLocalDateTime());
        }catch(IllegalArgumentException|NullPointerException e){throw new SQLException("Ordem armazenada invalida.","22000",e);}
    }
    // Confere apenas a faixa numerica INT positiva; existencia e vinculos sao SQL.
    private static void validarId(long id){if(id<1||id>Integer.MAX_VALUE)throw new IllegalArgumentException("ID invalido.");}
}
