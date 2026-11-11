package com.nexusdispatch.dao;

import com.nexusdispatch.model.PagamentoRegistro;
import com.nexusdispatch.enums.*;
import java.sql.*;
import java.util.*;

/** Persistencia do pagamento simulado. Transacao e autorizacao pertencem ao service. */
public final class PagamentoDao {
    private static final String COLUNAS = "id_pagamento, id_pedido, metodo_pagamento, valor_processado, "
            + "codigo_autorizacao, data_processamento, status_pagamento";
    // Conexao emprestada pelo service: este DAO fecha apenas comandos/resultados.
    // A mesma Connection permite que suas escritas sejam revertidas junto das demais tabelas.
    private final Connection conexao;
    public PagamentoDao(Connection conexao) { this.conexao = Objects.requireNonNull(conexao); }

    /** Um registro por pedido, protegido pela UNIQUE existente no SQL. */
    public PagamentoRegistro inserir(PagamentoRegistro pagamento) throws SQLException {
        Objects.requireNonNull(pagamento, "Pagamento obrigatorio.");
        if (pagamento.id() != null || pagamento.dataProcessamento() != null
                || pagamento.statusPagamento() != StatusPagamento.PENDENTE)
            throw new IllegalArgumentException("Novo pagamento exige ID/data nulos e estado PENDENTE.");
        exigirTransacao();
        // INSERT SELECT vincula o pagamento apenas a pedido CRIADO com o mesmo total.
        // A UNIQUE por pedido impede uma segunda linha; retry deve reutilizar a existente.
        String sql = """
                INSERT INTO pagamento
                (id_pedido, metodo_pagamento, valor_processado, codigo_autorizacao, status_pagamento)
                SELECT id_pedido, ?, ?, ?, 'PENDENTE' FROM pedido_mestre
                WHERE id_pedido=? AND status_pedido='CRIADO' AND valor_total=?
                """;
        try (PreparedStatement s=conexao.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            s.setString(1,pagamento.metodoPagamento().name()); s.setBigDecimal(2,pagamento.valorProcessado());
            s.setString(3,pagamento.codigoAutorizacao()); s.setLong(4,pagamento.idPedido());
            s.setBigDecimal(5,pagamento.valorProcessado());
            if(s.executeUpdate()!=1) throw new SQLException("Pedido ausente, fora de CRIADO ou valor divergente.");
            try(ResultSet r=s.getGeneratedKeys()) {
                if(!r.next()) throw new SQLException("Chave de pagamento nao retornada.");
                long id=r.getLong(1);
                if(r.wasNull() || id<1 || id>Integer.MAX_VALUE) throw new SQLException("Chave invalida.");
                return buscarPorId(id).orElseThrow(()->new SQLException("Pagamento inserido nao encontrado."));
            }
        }
    }
    // Consulta pela chave primaria do pagamento. Optional vazio indica ausencia;
    // falhas da consulta continuam sendo SQLException para o chamador.
    public Optional<PagamentoRegistro> buscarPorId(long id) throws SQLException {
        validarId(id); return consultar("id_pagamento",id);
    }
    // Consulta pela chave da compra. A restricao UNIQUE garante no maximo uma
    // linha por pedido, mesmo quando o pagamento possui varias tentativas ao longo do tempo.
    public Optional<PagamentoRegistro> buscarPorPedido(long idPedido) throws SQLException {
        validarId(idPedido); return consultar("id_pedido",idPedido);
    }
    // A coluna vem exclusivamente das duas chamadas internas acima, nunca do usuario.
    private Optional<PagamentoRegistro> consultar(String coluna,long id) throws SQLException {
        try(PreparedStatement s=conexao.prepareStatement("SELECT "+COLUNAS+" FROM pagamento WHERE "+coluna+"=?")) {
            s.setLong(1,id);
            try(ResultSet r=s.executeQuery()){return r.next()?Optional.of(mapear(r)):Optional.empty();}
        }
    }

    /**
     * Compara tambem o codigo da tentativa: resposta antiga nao conclui tentativa nova.
     * Nao confirma pedido/comandas: o service deve fazer isso na MESMA transacao.
     * false indica estado/codigo/pedido divergente, inclusive repeticao ja processada.
     */
    public boolean registrarResultado(long id,String codigoTentativa,StatusPagamento resultado,
            String codigoResultado) throws SQLException {
        validarId(id); codigoTentativa=validarCodigo(codigoTentativa); codigoResultado=validarCodigo(codigoResultado);
        if(resultado!=StatusPagamento.AUTORIZADO && resultado!=StatusPagamento.RECUSADO)
            throw new IllegalArgumentException("Resultado deve ser AUTORIZADO ou RECUSADO.");
        exigirTransacao();
        // BINARY compara o codigo exatamente, sem ignorar maiusculas pela collation.
        // Estado, codigo e total sao conferidos no mesmo UPDATE para recusar uma resposta
        // que ja ficou desatualizada. Uma linha alterada significa que esta tentativa venceu.
        String sql="""
                UPDATE pagamento p JOIN pedido_mestre m ON m.id_pedido=p.id_pedido
                SET p.status_pagamento=?, p.codigo_autorizacao=?, p.data_processamento=CURRENT_TIMESTAMP
                WHERE p.id_pagamento=? AND p.status_pagamento='PENDENTE'
                AND BINARY p.codigo_autorizacao=BINARY ?
                AND m.status_pedido='CRIADO' AND m.valor_total=p.valor_processado
                """;
        try(PreparedStatement s=conexao.prepareStatement(sql)) {
            s.setString(1,resultado.name());s.setString(2,codigoResultado);s.setLong(3,id);s.setString(4,codigoTentativa);
            return s.executeUpdate()==1;
        }
    }

    /** Reutiliza a linha recusada sem historico de tentativas, conforme o SQL atual. */
    public Optional<PagamentoRegistro> prepararNovaTentativa(long id,String codigoRecusado,
            MetodoPagamento metodo) throws SQLException {
        validarId(id); codigoRecusado=validarCodigo(codigoRecusado);
        Objects.requireNonNull(metodo,"Metodo obrigatorio.");exigirTransacao();
        // Nova referencia distingue esta tentativa da recusa anterior. O UUID identifica
        // a simulacao local; nao representa comprovante ou autorizacao de uma operadora.
        String codigoNovo="SIM-PENDENTE-"+UUID.randomUUID();
        String sql="""
                UPDATE pagamento p JOIN pedido_mestre m ON m.id_pedido=p.id_pedido
                SET p.status_pagamento='PENDENTE', p.codigo_autorizacao=?, p.metodo_pagamento=?,
                    p.data_processamento=CURRENT_TIMESTAMP
                WHERE p.id_pagamento=? AND p.status_pagamento='RECUSADO'
                AND BINARY p.codigo_autorizacao=BINARY ?
                AND m.status_pedido='CRIADO' AND m.valor_total=p.valor_processado
                """;
        try(PreparedStatement s=conexao.prepareStatement(sql)) {
            s.setString(1,codigoNovo);s.setString(2,metodo.name());s.setLong(3,id);s.setString(4,codigoRecusado);
            if(s.executeUpdate()!=1)return Optional.empty();
        }
        return Optional.of(buscarPorId(id).orElseThrow(()->new SQLException("Pagamento nao encontrado.")));
    }
    /** Marca estorno integral simulado, preservando valor, referencia e data da autorizacao. */
    public boolean registrarEstornoIntegral(long idPedido) throws SQLException {
        validarId(idPedido);
        exigirTransacao();
        String sql = """
                UPDATE pagamento p JOIN pedido_mestre m ON m.id_pedido=p.id_pedido
                SET p.status_pagamento='ESTORNADO'
                WHERE p.id_pedido=? AND p.status_pagamento='AUTORIZADO'
                AND m.status_pedido='PAGO' AND m.valor_total=p.valor_processado
                """;
        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setLong(1, idPedido);
            if (comando.executeUpdate() != 1) return false;
        }
        // Tambem protege bancos sem modo estrito se o ENUM ainda nao foi migrado.
        if (buscarPorPedido(idPedido).orElseThrow().statusPagamento() != StatusPagamento.ESTORNADO) {
            throw new SQLException("Estorno nao foi persistido.");
        }
        return true;
    }

    // Converte nomes SQL de enums para constantes Java. Data e status nulos sao
    // preservados conforme o registro; valor desconhecido de enum e erro de dados,
    // nao pagamento recusado nem consulta ausente.
    private static PagamentoRegistro mapear(ResultSet r)throws SQLException {
        try {
            Timestamp data=r.getTimestamp("data_processamento");String status=r.getString("status_pagamento");
            return new PagamentoRegistro(r.getLong("id_pagamento"),r.getLong("id_pedido"),
                    MetodoPagamento.valueOf(r.getString("metodo_pagamento")),r.getBigDecimal("valor_processado"),
                    r.getString("codigo_autorizacao"),data==null?null:data.toLocalDateTime(),
                    status==null?null:StatusPagamento.valueOf(status));
        }catch(IllegalArgumentException|NullPointerException e){throw new SQLException("Pagamento armazenado invalido.","22000",e);}
    }
    // Normaliza somente os espacos externos e confere o VARCHAR(100) em pontos
    // de codigo Unicode. Nao valida uma autorizacao financeira real.
    private static String validarCodigo(String codigo){
        if(codigo==null || codigo.isBlank())throw new IllegalArgumentException("Codigo obrigatorio.");
        codigo=codigo.strip();
        if(codigo.codePointCount(0,codigo.length())>100)throw new IllegalArgumentException("Codigo excede 100 caracteres.");
        return codigo;
    }
    // Restringe long a faixa positiva do INT usado nas chaves MySQL.
    private static void validarId(long id){if(id<1||id>Integer.MAX_VALUE)throw new IllegalArgumentException("ID invalido.");}
    // Pagamento e pedido devem mudar juntos. Recusa escrita isolada em autoCommit
    // para que o service possa desfazer o conjunto caso uma etapa posterior falhe.
    private void exigirTransacao()throws SQLException{if(conexao.getAutoCommit())throw new SQLException("Pagamento exige transacao explicita.");}
}
