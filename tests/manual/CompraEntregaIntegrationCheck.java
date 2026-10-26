import com.nexusdispatch.model.*;
import com.nexusdispatch.enums.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/** Integracao em memoria; nao testa banco, pagamento ou concorrencia. */
public class CompraEntregaIntegrationCheck {
    private static int checks;
    private static void eq(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) throw new AssertionError(expected + " != " + actual);
        checks++;
    }
    private static void reject(Class<? extends RuntimeException> type, Runnable action) {
        try { action.run(); }
        catch (RuntimeException error) { if (!type.isInstance(error)) throw error; checks++; return; }
        throw new AssertionError("Operacao invalida aceita");
    }
    private static Endereco endereco() {
        return new Endereco(new Bairro("Centro", "Cidade", true), "01234567", "Casa", "Rua A", "1");
    }
    private static Pedido pedido() {
        Loja loja = new Loja("Loja", "01234567890123", endereco(),
                new Lojista("Ana", "ana@example.com", null, "hash-ficticio"));
        return new Pedido(loja, List.of(new ItemPedido(new OfertaProduto(
                new Produto(loja, "Arroz", "Pacote"), new BigDecimal("12.50"), 5), 5)));
    }
    private static Entregador entregador() {
        return new Entregador("Beto", "beto@example.com", null, "hash-ficticio",
                TipoVeiculo.BICICLETA, null, null, null);
    }
    // Apenas fixture de teste: simula objetos carregados com IDs do banco futuro.
    private static void id(Object objeto, long id) throws Exception {
        var campo = objeto.getClass().getDeclaredField("id");
        campo.setAccessible(true);
        campo.set(objeto, id);
    }
    public static void main(String[] args) throws Exception {
        Endereco endereco = endereco();
        Consumidor consumidor = new Consumidor("Cris", "cris@example.com", null, "hash-ficticio", endereco);
        Pedido a = pedido(), b = pedido();
        reject(IllegalArgumentException.class, () -> new Compra(null, List.of(a)));
        reject(IllegalArgumentException.class, () -> new Compra(consumidor, null));
        reject(IllegalArgumentException.class, () -> new Compra(consumidor, List.of()));
        reject(IllegalArgumentException.class, () -> new Compra(consumidor, Arrays.asList(a, null)));
        reject(IllegalArgumentException.class, () -> new Compra(consumidor, List.of(a, a)));
        Pedido mesmaLoja = new Pedido(a.getLoja(), a.getItens());
        reject(IllegalArgumentException.class, () -> new Compra(consumidor, List.of(a, mesmaLoja)));
        Pedido avancado = pedido();
        avancado.iniciarPreparacao();
        reject(IllegalArgumentException.class, () -> new Compra(consumidor, List.of(avancado)));
        List<Pedido> origem = new ArrayList<>(List.of(a, b));
        LocalDateTime antes = LocalDateTime.now();
        Compra compra = new Compra(consumidor, origem);
        eq(false, compra.getDataHoraCriacao().isBefore(antes));
        eq(false, compra.getDataHoraCriacao().isAfter(LocalDateTime.now()));
        eq(null, compra.getId()); eq(consumidor, compra.getConsumidor());
        origem.clear(); eq(2, compra.getPedidos().size());
        reject(UnsupportedOperationException.class, () -> compra.getPedidos().clear());
        eq(new BigDecimal("125.00"), compra.getSubtotal());
        endereco.setCep("99999999"); endereco.setLogradouro("Rua B"); endereco.setNumero("2");
        endereco.setComplemento("Apartamento"); endereco.getBairro().setNome("Outro bairro");
        endereco.getBairro().setCidade("Outra cidade"); consumidor.setEndereco(endereco());
        eq(new Compra.EnderecoEntrega("01234567", "Rua A", "1", "Casa", "Centro", "Cidade"), compra.getEnderecoEntrega());
        a.getItens().get(0).getOferta().setPreco(BigDecimal.ONE);
        a.getItens().get(0).getProduto().setNome("Outro produto");
        eq(new BigDecimal("125.00"), compra.getSubtotal());
        reject(IllegalArgumentException.class, () -> new OrdemEntrega(null, a));
        reject(IllegalArgumentException.class, () -> new OrdemEntrega(compra, null));
        reject(IllegalArgumentException.class, () -> new OrdemEntrega(compra, pedido()));
        OrdemEntrega entregaA = new OrdemEntrega(compra, a), entregaB = new OrdemEntrega(compra, b);
        eq(compra.getEnderecoEntrega(), entregaA.getEnderecoEntrega());
        eq(entregaA.getEnderecoEntrega(), entregaB.getEnderecoEntrega());
        eq(a, entregaA.getPedido()); eq(null, entregaA.getId()); eq(null, entregaA.getEntregador());
        eq(StatusEntrega.AGUARDANDO_ENTREGADOR, entregaA.getStatus()); eq(false, entregaA.isEmAndamento());
        eq(null, entregaA.getDataHoraAceite()); eq(null, entregaA.getDataHoraRetirada()); eq(null, entregaA.getDataHoraConclusao());
        reject(IllegalStateException.class, entregaA::registrarRetirada);
        reject(IllegalStateException.class, entregaA::concluirEntrega);
        reject(IllegalArgumentException.class, () -> entregaA.aceitar(null));
        Entregador entregador = entregador();
        reject(IllegalStateException.class, () -> entregaA.aceitar(entregador));
        eq(null, entregaA.getEntregador()); eq(null, entregaA.getDataHoraAceite());
        entregador.ficarDisponivel(); entregaA.aceitar(entregador);
        eq(entregador, entregaA.getEntregador()); eq(StatusEntrega.ACEITA, entregaA.getStatus());
        eq(true, entregaA.isEmAndamento());
        eq(false, entregaA.getDataHoraAceite().isBefore(entregaA.getDataHoraCriacao()));
        reject(IllegalStateException.class, () -> entregaA.aceitar(entregador));
        reject(IllegalStateException.class, entregaA::registrarRetirada);
        eq(StatusEntrega.ACEITA, entregaA.getStatus()); eq(null, entregaA.getDataHoraRetirada());
        eq(StatusPedido.CONFIRMADO, a.getStatus());
        reject(IllegalStateException.class, entregaA::concluirEntrega);
        a.iniciarPreparacao(); entregaA.registrarRetirada();
        eq(StatusEntrega.EM_ROTA, entregaA.getStatus()); eq(StatusPedido.EM_ROTA, a.getStatus());
        eq(false, entregaA.getDataHoraRetirada().isBefore(entregaA.getDataHoraAceite()));
        eq(StatusPedido.CONFIRMADO, b.getStatus());
        reject(IllegalStateException.class, entregaA::registrarRetirada);
        reject(IllegalStateException.class, () -> new OrdemEntrega(compra, a));
        entregador.ficarIndisponivel(); // Nao cancela nem impede concluir a entrega existente.
        entregaA.concluirEntrega(); eq(StatusPedido.ENTREGUE, a.getStatus());
        eq(StatusEntrega.ENTREGUE, entregaA.getStatus()); eq(false, entregaA.isEmAndamento());
        eq(false, entregaA.getDataHoraConclusao().isBefore(entregaA.getDataHoraRetirada()));
        reject(IllegalStateException.class, entregaA::concluirEntrega);
        reject(IllegalStateException.class, () -> entregaA.aceitar(entregador));
        reject(IllegalStateException.class, () -> new OrdemEntrega(compra, a));
        entregador.ficarDisponivel(); entregaB.aceitar(entregador);
        b.iniciarPreparacao(); entregaB.registrarRetirada(); entregaB.concluirEntrega();
        eq(entregador, entregaB.getEntregador()); eq(StatusPedido.ENTREGUE, b.getStatus());

        // Identidade persistente: ordem deve modificar o pedido da compra, nao uma copia.
        Pedido original = pedido(), copia = pedido(); id(original, 100); id(copia, 100);
        Compra outraCompra = new Compra(consumidor, List.of(original));
        OrdemEntrega canonica = new OrdemEntrega(outraCompra, copia);
        eq(original, canonica.getPedido()); canonica.aceitar(entregador);
        original.iniciarPreparacao(); canonica.registrarRetirada();
        eq(StatusPedido.EM_ROTA, original.getStatus()); eq(StatusPedido.CONFIRMADO, copia.getStatus());
        Pedido p1 = pedido(), p2 = pedido(); id(p1.getLoja(), 10); id(p2.getLoja(), 10);
        reject(IllegalArgumentException.class, () -> new Compra(consumidor, List.of(p1, p2)));
        System.out.println("Integracao Compra/Entrega: " + checks + " verificacoes passaram.");
        limitesConhecidos(consumidor);
    }

    /** Diagnosticos das lacunas, nao testes de seguranca aprovados. */
    private static void limitesConhecidos(Consumidor consumidor) {
        Pedido a = pedido(), b = pedido();
        Compra compra = new Compra(consumidor, List.of(a, b));
        OrdemEntrega x = new OrdemEntrega(compra, a), y = new OrdemEntrega(compra, b);
        Entregador e = entregador(); e.ficarDisponivel(); x.aceitar(e); y.aceitar(e);
        if (!x.isEmAndamento() || !y.isEmAndamento()) throw new AssertionError("Revisar diagnostico");
        System.out.println("PENDENTE confirmado: mesmo entregador aceita duas ordens ativas sem servico.");
        new OrdemEntrega(compra, a);
        new Compra(consumidor, List.of(a));
        System.out.println("PENDENTE confirmado: ordem duplicada e reutilizacao de pedido entre compras.");
        e.atualizarVeiculoECnh(TipoVeiculo.CARRO, CategoriaCnh.B, null, null);
        System.out.println("PENDENTE confirmado: troca de veiculo durante ordem ativa.");
        a.iniciarPreparacao(); a.iniciarEntrega();
        if (x.getStatus() != StatusEntrega.ACEITA) throw new AssertionError("Revisar diagnostico");
        System.out.println("PENDENTE confirmado: chamada direta em Pedido pode divergir da ordem.");
        if (a.getLoja().isAberta() || a.getItens().get(0).getOferta().getEstoque() != 5) {
            throw new AssertionError("Revisar diagnostico");
        }
        System.out.println("PENDENTE confirmado: construtores nao verificam loja aberta, pagamento ou baixa de estoque.");
    }
}
