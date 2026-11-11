package com.nexusdispatch.service;

import com.nexusdispatch.dao.*;
import com.nexusdispatch.database.ConexaoBD;
import com.nexusdispatch.enums.*;
import com.nexusdispatch.model.UsuarioRegistro;
import java.sql.SQLException;
import java.util.*;

/** Gestao de demonstracao sem credenciais nas respostas; nao implementa autorizacao de sessao. */
public final class UsuarioService {
    private final TransacaoJdbc.FabricaConexao fabrica;
    /** Usa conexao externa; cadastro de usuario continua no CadastroService. */
    public UsuarioService() { this(ConexaoBD::conectar); }
    /** Injeta catalogo de testes sem alterar a configuracao real. */
    public UsuarioService(TransacaoJdbc.FabricaConexao fabrica) { this.fabrica = Objects.requireNonNull(fabrica); }

    /** Consulta publica do cadastro, sem carregar senha_hash. */
    public Optional<UsuarioRegistro> buscar(long id) throws SQLException {
        TransacaoJdbc.validarId(id);
        return TransacaoJdbc.executar(fabrica, c -> new UsuarioDao(c).buscarPorId(id));
    }

    /** Pagina limitada; nao expor listagem como se fosse autenticacao. */
    public List<UsuarioRegistro> listar(int limite, int deslocamento) throws SQLException {
        return TransacaoJdbc.executar(fabrica, c -> new UsuarioDao(c).listar(limite, deslocamento));
    }

    /** Atualiza dados comuns, nunca perfil/hash ou complementos comerciais. */
    public boolean atualizar(long id, CadastroService.DadosUsuario dados) throws SQLException {
        TransacaoJdbc.validarId(id); Objects.requireNonNull(dados);
        return TransacaoJdbc.executar(fabrica, c -> {
            var dao = new UsuarioDao(c);
            var atual = dao.buscarPorIdParaAtualizacao(id);
            if (atual.isEmpty()) return false;
            exigirPerfilGerenciavel(atual.orElseThrow());
            return dao.atualizarCadastro(id, dados.nome(), dados.documentoFiscal(), dados.email());
        });
    }

    /**
     * Desativar nao cancela compra. Entregador e bloqueado antes de usuario,
     * como no aceite; nao desativar EM_ROTA/ordem ativa. Lojista aguarda comandas.
     * Reativar usuario nunca o coloca automaticamente como entregador DISPONIVEL.
     */
    public boolean definirAtivo(long id, boolean ativo) throws SQLException {
        TransacaoJdbc.validarId(id);
        return TransacaoJdbc.executar(fabrica, c -> {
            var entregadores = new EntregadorDao(c);
            // Mesmo ID pode ter complemento de entregador. Bloqueia esse complemento
            // antes do usuario para manter a ordem compartilhada com o aceite e evitar ciclos.
            var entregador = entregadores.buscarPorIdParaAtualizacao(id);
            var usuarios = new UsuarioDao(c);
            var atual = usuarios.buscarPorIdParaAtualizacao(id);
            if (atual.isEmpty()) return false;
            UsuarioRegistro usuario = atual.orElseThrow();
            exigirPerfilGerenciavel(usuario);
            if (usuario.perfil() == PerfilUsuario.ENTREGADOR) {
                if (entregador.isEmpty()) throw new IllegalStateException("Complemento do entregador ausente.");
                if (!ativo) {
                    if (entregador.orElseThrow().statusOperacao() == StatusOperacaoEntregador.EM_ROTA
                            || new OrdemEntregaDao(c).possuiOrdemAtiva(id))
                        throw new IllegalStateException("Conclua a entrega antes de desativar o usuario.");
                    if (entregador.orElseThrow().statusOperacao() != StatusOperacaoEntregador.INATIVO
                            && !entregadores.inativarSemOrdem(id)) throw new IllegalStateException("Estado operacional mudou.");
                }
            } else if (usuario.perfil() == PerfilUsuario.LOJISTA) {
                var lojistas = new LojistaDao(c);
                if (lojistas.buscarPorIdParaAtualizacao(id).isEmpty())
                    throw new IllegalStateException("Complemento comercial ausente.");
                if (!ativo && lojistas.possuiComandaEmAndamento(id))
                    throw new IllegalStateException("Conclua ou cancele as comandas antes de desativar o lojista.");
            }
            // Repetir o estado desejado e sucesso sem novo UPDATE, mas so depois das
            // validacoes. equals distingue false de null: ausencia nao e inatividade explicita.
            return Boolean.valueOf(ativo).equals(usuario.ativo()) || usuarios.definirAtivo(id, ativo);
        });
    }

    /** DELETE apenas de CLIENTE inativo sem vinculos; preserva identidades com historico. */
    public boolean excluir(long id) throws SQLException {
        TransacaoJdbc.validarId(id);
        return TransacaoJdbc.executar(fabrica, c -> new UsuarioDao(c).excluirClienteSemVinculos(id));
    }

    /** Administrador precisa de politica propria; nao alterar por menu livre de demonstracao. */
    private static void exigirPerfilGerenciavel(UsuarioRegistro usuario) {
        if (usuario.perfil() == PerfilUsuario.ADMIN)
            throw new IllegalStateException("Gestao de ADMIN nao esta disponivel no modo de demonstracao.");
    }
}
