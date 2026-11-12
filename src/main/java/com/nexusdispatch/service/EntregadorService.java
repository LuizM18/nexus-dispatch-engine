package com.nexusdispatch.service;

import com.nexusdispatch.dao.EntregadorDao;
import com.nexusdispatch.dao.OrdemEntregaDao;
import com.nexusdispatch.dao.UsuarioDao;
import com.nexusdispatch.database.ConexaoBD;
import com.nexusdispatch.enums.PerfilUsuario;
import com.nexusdispatch.enums.StatusOperacaoEntregador;
import com.nexusdispatch.model.Entregador;
import com.nexusdispatch.model.EntregadorRegistro;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Gestao cadastral e disponibilidade manual. Cadastro inicial pertence a
 * CadastroService; atividade do usuario a UsuarioService; aceite/coleta/conclusao
 * a EntregaService. Contexto por ID ainda nao representa autenticacao de sessao.
 */
public final class EntregadorService {
    private final TransacaoJdbc.FabricaConexao fabrica;
    /** Conexao configurada externamente. */
    public EntregadorService() { this(ConexaoBD::conectar); }
    /** Injeta conexao para testar bloqueios e rollback em banco descartavel. */
    public EntregadorService(TransacaoJdbc.FabricaConexao fabrica) {
        this.fabrica = Objects.requireNonNull(fabrica, "Fabrica de conexao obrigatoria.");
    }

    /**
     * Consulta integrada sob bloqueio: entregador antes de usuario, como no aceite.
     * Preserva dados desconhecidos e diferencia ausencia, perfil e complemento.
     */
    public Optional<Entregador> buscar(long id) throws SQLException {
        TransacaoJdbc.validarId(id);
        return TransacaoJdbc.executar(fabrica, c -> {
            var complemento = new EntregadorDao(c).buscarPorIdParaAtualizacao(id);
            var usuario = new UsuarioDao(c).buscarPorIdParaAtualizacao(id);
            if (usuario.isEmpty()) return Optional.empty();
            if (usuario.orElseThrow().perfil() != PerfilUsuario.ENTREGADOR)
                throw new IllegalArgumentException("ID nao pertence a um entregador.");
            return Optional.of(new Entregador(usuario.orElseThrow(), complemento.orElseThrow(
                    () -> new IllegalStateException("Complemento do entregador ausente."))));
        });
    }

    /** Lista somente usuarios ativos DISPONIVEIS no bairro exato, sem inferir proximidade. */
    public List<EntregadorRegistro> listarDisponiveisPorBairro(String bairro, int limite, int deslocamento) throws SQLException {
        if (bairro == null || bairro.isBlank()) throw new IllegalArgumentException("Bairro obrigatorio.");
        String normalizado = bairro.strip();
        if (normalizado.codePointCount(0, normalizado.length()) > 100 || limite < 1 || limite > 100 || deslocamento < 0)
            throw new IllegalArgumentException("Bairro ou paginacao invalida.");
        return TransacaoJdbc.executar(fabrica, c -> new EntregadorDao(c)
                .listarDisponiveisPorBairro(normalizado, limite, deslocamento));
    }

    /**
     * Troca apenas veiculo/bairro/CNH sem trabalho ativo, mesmo com estado inconsistente.
     * Status recebido nao e aplicado. Repetir mesmos campos e seguro sem novo UPDATE.
     */
    public boolean atualizar(EntregadorRegistro dados) throws SQLException {
        Objects.requireNonNull(dados, "Dados do entregador obrigatorios.");
        return TransacaoJdbc.executar(fabrica, c -> {
            var dao = new EntregadorDao(c);
            var atual = dao.buscarPorIdParaAtualizacao(dados.id());
            if (atual.isEmpty()) return false;
            validarPerfil(c, dados.id(), false);
            exigirSemTrabalho(c, atual.orElseThrow());
            // Valida bairro exigido para escrita mesmo se os campos nao mudarem.
            EntregadorRegistro.novoCadastro(dados.id(), dados.cnh(), dados.tipoVeiculo(), dados.bairroOperacao());
            // Preserva o estado operacional que acabou de ser lido, ignorando o status
            // recebido para edicao cadastral. Disponibilidade tem um fluxo separado.
            var cadastro = new EntregadorRegistro(dados.id(), dados.cnh(), dados.tipoVeiculo(),
                    atual.orElseThrow().statusOperacao(), dados.bairroOperacao());
            return atual.orElseThrow().equals(cadastro) || dao.atualizarCadastro(cadastro);
        });
    }

    /**
     * Troca manual apenas sem trabalho. NULL precisa de inativacao explicita antes
     * de disponibilizar. Usuario inativo nao pode receber entregas; repetir alvo e
     * idempotente, mas ainda confere ordens/perfil antes de declarar sucesso.
     */
    public boolean definirDisponivel(long id, boolean disponivel) throws SQLException {
        TransacaoJdbc.validarId(id);
        return TransacaoJdbc.executar(fabrica, c -> {
            var dao = new EntregadorDao(c);
            var atual = dao.buscarPorIdParaAtualizacao(id);
            if (atual.isEmpty()) return false;
            validarPerfil(c, id, disponivel);
            var registro = atual.orElseThrow();
            exigirSemTrabalho(c, registro);
            StatusOperacaoEntregador novo = disponivel ? StatusOperacaoEntregador.DISPONIVEL : StatusOperacaoEntregador.INATIVO;
            if (!disponivel) return registro.statusOperacao() == StatusOperacaoEntregador.INATIVO || dao.inativarSemOrdem(id);
            if (registro.statusOperacao() == null)
                throw new IllegalStateException("Estado desconhecido: coloque INATIVO antes de disponibilizar.");
            if (registro.bairroOperacao() == null || registro.bairroOperacao().isBlank())
                throw new IllegalStateException("Informe o bairro antes de disponibilizar.");
            return registro.statusOperacao() == novo || dao.alterarDisponibilidade(id, registro.statusOperacao(), novo);
        });
    }

    /** Bloqueio usuario vem depois do entregador, mantendo a ordem usada no aceite. */
    private static void validarPerfil(Connection c, long id, boolean exigirAtivo) throws SQLException {
        var usuario = new UsuarioDao(c).buscarPorIdParaAtualizacao(id).orElseThrow(
                () -> new IllegalStateException("Usuario do entregador ausente."));
        if (usuario.perfil() != PerfilUsuario.ENTREGADOR)
            throw new IllegalArgumentException("ID nao pertence a um entregador.");
        if (exigirAtivo && !Boolean.TRUE.equals(usuario.ativo()))
            throw new IllegalStateException("Usuario precisa estar ativo para receber entregas.");
    }

    /** Estado e ordens sao conferidos; um boolean de disponibilidade nao basta. */
    private static void exigirSemTrabalho(Connection c, EntregadorRegistro entregador) throws SQLException {
        if (entregador.statusOperacao() == StatusOperacaoEntregador.EM_ROTA
                || new OrdemEntregaDao(c).possuiOrdemAtiva(entregador.id()))
            throw new IllegalStateException("Entregador possui trabalho ativo; conclua antes desta alteracao.");
    }
}
