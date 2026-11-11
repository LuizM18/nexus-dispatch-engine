package com.nexusdispatch.service;

import com.nexusdispatch.dao.*;
import com.nexusdispatch.database.ConexaoBD;
import com.nexusdispatch.enums.*;
import com.nexusdispatch.model.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/** Cadastros de demonstracao e inicializacao administrativa explicita do banco. */
public final class CadastroService {
    /** Dados publicos comuns; nunca guarda senha em record, log ou toString. */
    public record DadosUsuario(String nome, String documentoFiscal, String email) { }
    /** Campos comerciais validados por LojistaRegistro antes de inserir complemento. */
    public record DadosLojista(String nomeFantasia, BigDecimal taxaComissaoPct,
            String bairroOperacao, String enderecoOrigem) { }
    /** Apenas dados presentes no SQL: CNH e numero, nao categoria. */
    public record DadosEntregador(String cnh, TipoVeiculo tipoVeiculo, String bairroOperacao) { }
    @FunctionalInterface
    public interface FabricaConexao { Connection abrir() throws SQLException; }
    @FunctionalInterface
    // Callback privado que insere a parte especifica do perfil usando o ID gerado
    // para usuario. CLIENTE e ADMIN passam uma lambda vazia, pois nao tem complemento.
    private interface Complemento { void inserir(Connection c, long id) throws SQLException; }
    private final FabricaConexao fabrica;
    private final SenhaService senhas;

    /** Usa conexao externa e PBKDF2 do JDK, sem dependencias novas. */
    public CadastroService() { this(ConexaoBD::conectar, new SenhaService()); }

    /** Injecao de conexao permite validar rollback em catalogo descartavel. */
    public CadastroService(FabricaConexao fabrica, SenhaService senhas) {
        this.fabrica = Objects.requireNonNull(fabrica);
        this.senhas = Objects.requireNonNull(senhas);
    }

    /** CLIENTE nao tem tabela complementar/endereco no SQL atual. */
    public UsuarioRegistro cadastrarCliente(DadosUsuario dados, char[] senha) throws SQLException {
        return cadastrar(dados, senha, PerfilUsuario.CLIENTE, (c, id) -> { });
    }

    /** Provisionamento local. Um ADMIN existente, mesmo inativo, impede a criacao inicial. */
    public UsuarioRegistro cadastrarAdministradorInicial(DadosUsuario dados, char[] senha) throws SQLException {
        return cadastrar(dados, senha, PerfilUsuario.ADMIN, (c, id) -> { });
    }

    /** Usuario LOJISTA e complemento recebem o mesmo ID e um unico commit. */
    public UsuarioRegistro cadastrarLojista(DadosUsuario dados, char[] senha, DadosLojista loja) throws SQLException {
        Objects.requireNonNull(loja, "Dados comerciais obrigatorios.");
        return cadastrar(dados, senha, PerfilUsuario.LOJISTA, (c, id) ->
                new LojistaDao(c).inserir(new LojistaRegistro(id, loja.nomeFantasia(), loja.taxaComissaoPct(),
                        loja.bairroOperacao(), loja.enderecoOrigem())));
    }

    /** ENTREGADOR nasce INATIVO explicitamente; nao inventa categoria/PBT/passageiros. */
    public UsuarioRegistro cadastrarEntregador(DadosUsuario dados, char[] senha, DadosEntregador entregador) throws SQLException {
        Objects.requireNonNull(entregador, "Dados do entregador obrigatorios.");
        return cadastrar(dados, senha, PerfilUsuario.ENTREGADOR, (c, id) -> new EntregadorDao(c).inserir(
                EntregadorRegistro.novoCadastro(id, entregador.cnh(), entregador.tipoVeiculo(), entregador.bairroOperacao())));
    }

    /** Lista dados sem credenciais para localizar IDs no menu de demonstracao. */
    public List<UsuarioRegistro> listarUsuarios(int limite, int deslocamento) throws SQLException {
        try (Connection c = fabrica.abrir()) {
            if (!c.getAutoCommit()) throw new SQLException("Use conexao nova sem transacao em andamento.");
            return new UsuarioDao(c).listar(limite, deslocamento);
        }
    }

    /** Hash antes de abrir transacao; qualquer falha do complemento reverte usuario. */
    private UsuarioRegistro cadastrar(DadosUsuario dados, char[] senha, PerfilUsuario perfil,
            Complemento complemento) throws SQLException {
        Objects.requireNonNull(dados, "Dados do usuario obrigatorios.");
        // Derivar a senha tem custo de CPU. Fazemos isso antes de abrir a conexao para
        // nao manter transacao/bloqueios durante esse calculo. O record de resposta nao leva hash.
        String hash = senhas.gerar(senha);
        try (Connection c = fabrica.abrir()) {
            if (!c.getAutoCommit()) throw new SQLException("Use conexao nova sem transacao em andamento.");
            // O primeiro ADMIN exige verificacao serializada da ausencia de outro ADMIN.
            // A consulta FOR UPDATE do DAO participa da mesma transacao que a insercao;
            // uma disputa pode falhar e deve chegar ao chamador, sem criar complemento parcial.
            if (perfil == PerfilUsuario.ADMIN) c.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            c.setAutoCommit(false);
            try {
                var usuarios = new UsuarioDao(c);
                if (perfil == PerfilUsuario.ADMIN && usuarios.existeAdministradorParaAtualizacao())
                    throw new IllegalStateException("Ja existe um administrador cadastrado.");
                long id = usuarios.inserir(dados.nome(), dados.documentoFiscal(), dados.email(), hash, perfil);
                // Usuario e complemento compartilham chave e conexao. Uma falha aqui desfaz
                // tambem o INSERT anterior; nenhum perfil incompleto deve ser confirmado.
                complemento.inserir(c, id);
                var salvo = usuarios.buscarPorId(id).orElseThrow(() -> new SQLException("Cadastro nao recuperado."));
                c.commit();
                return salvo;
            } catch (SQLException | RuntimeException erro) {
                try { c.rollback(); } catch (SQLException falha) { erro.addSuppressed(falha); }
                throw erro;
            }
        }
    }
}
