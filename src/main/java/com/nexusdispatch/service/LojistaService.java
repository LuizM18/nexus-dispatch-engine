package com.nexusdispatch.service;

import com.nexusdispatch.dao.LojistaDao;
import com.nexusdispatch.dao.UsuarioDao;
import com.nexusdispatch.database.ConexaoBD;
import com.nexusdispatch.enums.PerfilUsuario;
import com.nexusdispatch.model.Lojista;
import com.nexusdispatch.model.LojistaRegistro;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Consulta e edita dados comerciais da tabela lojista. Cadastro inicial pertence
 * a CadastroService; nome/documento/email e atividade pertencem a UsuarioService.
 * Receber um ID nao autentica o chamador: a interface atual e de demonstracao.
 */
public final class LojistaService {
    private final TransacaoJdbc.FabricaConexao fabrica;
    /** Conexao configurada externamente. */
    public LojistaService() { this(ConexaoBD::conectar); }
    /** Permite testar sem escrever na base real. */
    public LojistaService(TransacaoJdbc.FabricaConexao fabrica) {
        this.fabrica = Objects.requireNonNull(fabrica, "Fabrica de conexao obrigatoria.");
    }

    /**
     * Reune usuario/complemento sob bloqueio, nessa ordem, para nao misturar
     * alteracoes concorrentes das duas fichas em READ_COMMITTED. Leitura curta
     * pode aguardar outra transacao. Ausencia nao e erro de conexao/perfil.
     */
    public Optional<Lojista> buscar(long id) throws SQLException {
        TransacaoJdbc.validarId(id);
        return TransacaoJdbc.executar(fabrica, c -> {
            var usuario = new UsuarioDao(c).buscarPorIdParaAtualizacao(id);
            if (usuario.isEmpty()) return Optional.empty();
            if (usuario.orElseThrow().perfil() != PerfilUsuario.LOJISTA)
                throw new IllegalArgumentException("ID nao pertence a um lojista.");
            var complemento = new LojistaDao(c).buscarPorIdParaAtualizacao(id).orElseThrow(
                    () -> new IllegalStateException("Complemento comercial ausente."));
            return Optional.of(new Lojista(usuario.orElseThrow(), complemento));
        });
    }

    /**
     * Pagina de complementos comerciais, incluindo inativos para gestao.
     * Nao e uma lista de lojistas autorizados a vender; venda valida perfil/atividade.
     */
    public List<LojistaRegistro> listar(int limite, int deslocamento) throws SQLException {
        if (limite < 1 || limite > 100 || deslocamento < 0)
            throw new IllegalArgumentException("Limite deve ser de 1 a 100 e deslocamento nao negativo.");
        return TransacaoJdbc.executar(fabrica, c -> new LojistaDao(c).listar(limite, deslocamento));
    }

    /**
     * Altera apenas campos comerciais, mesmo em cadastro inativo para regularizacao.
     * Comissao afeta compras futuras, nunca recalcula valores de comandas salvas.
     * Sem ID existente retorna false; repetir os mesmos dados e sucesso sem UPDATE.
     */
    public boolean atualizar(LojistaRegistro dados) throws SQLException {
        Objects.requireNonNull(dados, "Dados comerciais obrigatorios.");
        return TransacaoJdbc.executar(fabrica, c -> {
            var usuario = new UsuarioDao(c).buscarPorIdParaAtualizacao(dados.id());
            if (usuario.isEmpty()) return false;
            if (usuario.orElseThrow().perfil() != PerfilUsuario.LOJISTA)
                throw new IllegalArgumentException("ID nao pertence a um lojista.");
            var dao = new LojistaDao(c);
            var atual = dao.buscarPorIdParaAtualizacao(dados.id());
            if (atual.isEmpty())
                throw new IllegalStateException("Complemento comercial ausente.");
            // Records comparam seus componentes por equals. Se os dados ja sao iguais,
            // retorna sucesso sem depender da contagem do driver para UPDATE sem mudanca.
            return atual.orElseThrow().equals(dados) || dao.atualizar(dados);
        });
    }
}
