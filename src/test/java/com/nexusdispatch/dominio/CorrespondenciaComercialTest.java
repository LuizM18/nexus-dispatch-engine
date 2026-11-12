package com.nexusdispatch.dominio;

import org.junit.jupiter.api.Test;

import com.nexusdispatch.enums.*;
import com.nexusdispatch.model.*;
import java.math.BigDecimal;

/** Contratos de leitura do SQL, vinculos e protecao contra saldo ficticio local. */
public class CorrespondenciaComercialTest {
    static int checks;
    // Constroi o decimal a partir de texto, sem aproximacao binaria de double.
    // Assim, o valor esperado pode ser comparado com o contrato monetario do modelo.
    static BigDecimal b(String valor) { return new BigDecimal(valor); }
    // Transforma uma condicao falsa em AssertionError para interromper o teste.
    // A mensagem/contador identifica a verificacao; imprimir sucesso nao substitui a assercao.
    static void ok(boolean valor) {
        if (!valor) throw new AssertionError("Correspondencia invalida");
        checks++;
    }
    // Executa a acao e confere a excecao esperada. Excecoes de outro tipo
    // nao devem ser confundidas com uma rejeicao correta da regra testada.
    static void rejeitar(Runnable acao, Class<? extends RuntimeException> tipo) {
        try { acao.run(); } catch (RuntimeException e) { if (!tipo.isInstance(e)) throw e; checks++; return; }
        throw new AssertionError("Operacao indevida aceita");
    }
    static UsuarioRegistro usuario(long id, PerfilUsuario perfil, Boolean ativo) {
        return new UsuarioRegistro(id, "Ana", "DOC", "ana@example.test", perfil, null, ativo);
    }
    // Compara objetos de dominio com registros sinteticos JDBC. Ausencia de campos
    // na tabela deve continuar desconhecida; oferta consulta o snapshot de produto
    // sem inventar estoque, CNPJ, categoria da CNH ou abertura da loja.
    @Test
    void verificar() {
        for (StatusOperacaoEntregador estado : new StatusOperacaoEntregador[]{null,
                StatusOperacaoEntregador.INATIVO, StatusOperacaoEntregador.DISPONIVEL, StatusOperacaoEntregador.EM_ROTA}) {
            var registro = new EntregadorRegistro(1, "CNH-123", TipoVeiculo.VAN, estado, "Centro");
            Entregador e = new Entregador(usuario(1, PerfilUsuario.ENTREGADOR, true), registro);
            ok(e.toEntregadorRegistro().equals(registro));
            ok(e.toRegistro().equals(usuario(1, PerfilUsuario.ENTREGADOR, true)));
            ok(e.getStatusOperacao() == estado);
            ok(e.isDisponivel() == (estado == StatusOperacaoEntregador.DISPONIVEL));
            ok(e.getCnh().equals("CNH-123") && e.getBairroOperacao().equals("Centro"));
            ok(e.getCategoriaCnh() == null && e.getPesoBrutoTotalKg() == null && e.getCapacidadePassageiros() == null);
            rejeitar(e::ficarDisponivel, IllegalStateException.class);
            rejeitar(e::ficarIndisponivel, IllegalStateException.class);
            rejeitar(() -> e.atualizarVeiculoECnh(TipoVeiculo.BICICLETA, null, null, null), IllegalStateException.class);
        }
        var livre = new EntregadorRegistro(1, null, TipoVeiculo.BICICLETA, StatusOperacaoEntregador.DISPONIVEL, null);
        for (Boolean ativo : new Boolean[]{false, null}) ok(!new Entregador(usuario(1, PerfilUsuario.ENTREGADOR, ativo), livre).isDisponivel());
        rejeitar(() -> new Entregador(usuario(2, PerfilUsuario.ENTREGADOR, true), livre), IllegalArgumentException.class);
        rejeitar(() -> new Entregador(usuario(1, PerfilUsuario.CLIENTE, true), livre), IllegalArgumentException.class);
        var lojista = new Lojista(usuario(2, PerfilUsuario.LOJISTA, true), new LojistaRegistro(2, "Loja Ana", b("5"), "Centro", "Rua A 10"));
        var loja = new Loja(lojista);
        ok(loja.getId().equals(2L) && loja.getNome().equals("Loja Ana"));
        ok(loja.getDocumentoFiscal().equals("DOC") && loja.getCnpj() == null);
        ok(loja.getEndereco() == null && loja.getEnderecoOrigem().equals("Rua A 10"));
        ok(loja.getBairroOperacao().equals("Centro") && loja.getStatusAberta() == null);
        rejeitar(loja::isAberta, IllegalStateException.class);
        rejeitar(loja::abrir, IllegalStateException.class);
        rejeitar(loja::fechar, IllegalStateException.class);
        rejeitar(() -> loja.setNome("Outra"), IllegalStateException.class);
        rejeitar(() -> loja.setEndereco(null), IllegalStateException.class);
        for (Boolean ativo : new Boolean[]{true, false, null}) {
            var registro = new ProdutoRegistro(10L, 2, "Produto", "OUTROS", "D".repeat(1200), b("12.50"), b("1.234"), 7, ativo);
            var produto = new Produto(loja, registro);
            var oferta = new OfertaProduto(produto);
            var outraVisao = new OfertaProduto(produto);
            ok(produto.toRegistro().equals(registro));
            ok(produto.getId().equals(10L) && produto.getLoja() == loja);
            ok(produto.getCategoria().equals("OUTROS") && produto.getPesoKg().equals(b("1.234")));
            ok(produto.getDescricao().length() == 1200 && produto.getAtivo() == ativo);
            ok(oferta.getPreco().equals(b("12.50")) && oferta.getEstoque() == 7);
            ok(outraVisao.getPreco().equals(oferta.getPreco()) && outraVisao.getEstoque() == oferta.getEstoque());
            ok(oferta.getQuantidadeDisponivelParaVenda() == (Boolean.TRUE.equals(ativo) ? 7 : 0));
            ok(oferta.getId() == null && oferta.getProduto() == produto);
            rejeitar(() -> oferta.setPreco(b("1")), IllegalStateException.class);
            rejeitar(() -> oferta.adicionarEstoque(3), IllegalStateException.class);
            rejeitar(oferta::getEntradasEstoque, IllegalStateException.class);
            rejeitar(() -> produto.setNome("Alterado"), IllegalStateException.class);
            rejeitar(() -> produto.setDescricao("Alterada"), IllegalStateException.class);
            rejeitar(() -> new OfertaProduto(produto, b("1"), 99), IllegalArgumentException.class);
            ok(produto.toRegistro().equals(registro));
        }
        rejeitar(() -> new Produto(loja, new ProdutoRegistro(10L, 3, "Produto", "OUTROS", null, b("1"), b("1"), 1, true)), IllegalArgumentException.class);
        rejeitar(() -> new Produto(loja, new ProdutoRegistro(null, 2, "Produto", "OUTROS", null, b("1"), b("1"), 1, true)), IllegalArgumentException.class);
        rejeitar(() -> new OfertaProduto(new Produto(loja, "Local", null)), IllegalArgumentException.class);
        System.out.println("Correspondencia comercial: " + checks + " verificacoes aprovadas.");
    }
}
