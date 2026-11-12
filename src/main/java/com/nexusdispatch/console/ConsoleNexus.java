package com.nexusdispatch.console;

import java.io.BufferedReader;
import java.io.Console;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.nexusdispatch.enums.MetodoPagamento;
import com.nexusdispatch.enums.PerfilUsuario;
import com.nexusdispatch.enums.StatusPagamento;
import com.nexusdispatch.enums.TipoVeiculo;
import com.nexusdispatch.model.Administrador;
import com.nexusdispatch.model.Consumidor;
import com.nexusdispatch.model.Entregador;
import com.nexusdispatch.model.EntregadorRegistro;
import com.nexusdispatch.model.Lojista;
import com.nexusdispatch.model.LojistaRegistro;
import com.nexusdispatch.model.PedidoMestreRegistro;
import com.nexusdispatch.model.ProdutoRegistro;
import com.nexusdispatch.model.Usuario;
import com.nexusdispatch.model.UsuarioRegistro;
import com.nexusdispatch.service.CadastroService;
import com.nexusdispatch.service.CompraService;
import com.nexusdispatch.service.EntregadorService;
import com.nexusdispatch.service.LojistaService;
import com.nexusdispatch.service.ProdutoService;
import com.nexusdispatch.service.SenhaService;
import com.nexusdispatch.service.UsuarioService;

/** Terminal com login por perfil e persistencia delegada aos servicos JDBC. */
public final class ConsoleNexus {
    private static final String FAIXA_SENHA = " (" + SenhaService.MIN_CARACTERES + " a "
            + SenhaService.MAX_CARACTERES + " caracteres): ";
    private static final int TAMANHO_PAGINA_PERFIS = 20;
    private final ProdutoService produtos;
    private final BufferedReader entrada;
    private final PrintWriter saida;
    private final CadastroService cadastros;
    private final Console terminal;
    private final UsuarioService usuarios;
    private final LojistaService lojistas;
    private final EntregadorService entregadores;
    private final CompraService compras;
    // Loja selecionada para o CRUD. No menu autenticado, o lojista usa a propria loja.
    private Long idLojista;

    /** Recebe recursos para permitir execucao interativa e verificacoes com entrada controlada. */
    public ConsoleNexus(ProdutoService produtos, Reader entrada, PrintWriter saida) {
        this(produtos, null, null, null, null, null, entrada, saida, null);
    }

    /** Menu com cadastros injetados; testes nao dependem do banco de producao. */
    public ConsoleNexus(ProdutoService produtos, CadastroService cadastros, Reader entrada, PrintWriter saida) {
        this(produtos, cadastros, null, null, null, null, entrada, saida, null);
    }

    /** Injecao completa para testar gestao de perfis sem conectar na base original. */
    public ConsoleNexus(ProdutoService produtos, CadastroService cadastros, UsuarioService usuarios,
            LojistaService lojistas, EntregadorService entregadores, Reader entrada, PrintWriter saida) {
        this(produtos, cadastros, usuarios, lojistas, entregadores, null, entrada, saida, null);
    }

    /** Injeta os servicos do menu por perfil para exercitar o terminal com outro banco. */
    public ConsoleNexus(ProdutoService produtos, CadastroService cadastros, UsuarioService usuarios,
            LojistaService lojistas, EntregadorService entregadores, CompraService compras, Reader entrada, PrintWriter saida) {
        this(produtos, cadastros, usuarios, lojistas, entregadores, compras, entrada, saida, null);
    }

    /** Console nativo usa a mesma origem em toda leitura para nao perder senha em buffers. */
    private ConsoleNexus(ProdutoService produtos, CadastroService cadastros, UsuarioService usuarios,
            LojistaService lojistas, EntregadorService entregadores, CompraService compras, Reader entrada,
            PrintWriter saida, Console terminal) {
        this.produtos = Objects.requireNonNull(produtos);
        this.entrada = new BufferedReader(Objects.requireNonNull(entrada));
        this.saida = Objects.requireNonNull(saida);
        this.cadastros = cadastros;
        this.terminal = terminal;
        this.usuarios = usuarios;
        this.lojistas = lojistas;
        this.entregadores = entregadores;
        this.compras = compras;
    }

    /** Ponto de entrada do menu por perfil com taxa de compra opcional para a demonstracao. */
    public static void main(String[] args) throws IOException {
        PrintWriter saida = new PrintWriter(System.out, true, StandardCharsets.UTF_8);
        // Sem percentual informado, os menus existem, mas finalizar/consultar compras
        // exige configurar o service. Este main usa checkout isolado: nao injeta dados
        // logisticos em EntregaService e, portanto, nao cria ordens de entrega pelo terminal.
        CompraService compras = null;
        boolean criarAdmin = args.length == 1 && "--criar-admin-inicial".equals(args[0]);
        try {
            if (args.length > 1) throw new IllegalArgumentException("Informe apenas a taxa percentual da compra.");
            if (args.length == 1 && !criarAdmin)
                compras = new CompraService(new BigDecimal(args[0].replace(',', '.')), p -> true);
        } catch (IllegalArgumentException erro) {
            saida.println("Taxa da compra invalida. Informe um percentual entre 0 e 100, com ate duas casas decimais.");
            return;
        }
        var console = new ConsoleNexus(new ProdutoService(), new CadastroService(), new UsuarioService(), new LojistaService(),
                new EntregadorService(), compras, new InputStreamReader(System.in, StandardCharsets.UTF_8),
                saida, System.console());
        if (criarAdmin) console.executarCadastroAdministradorInicial();
        else console.executarPorPerfil();
    }

    /** Comando de preparacao da bancada, separado dos cadastros acessiveis pelo menu. */
    public void executarCadastroAdministradorInicial() throws IOException {
        saida.println("Cadastro inicial de administrador");
        try {
            var dados = new CadastroService.DadosUsuario(ler("Nome: "), ler("Documento fiscal: "), ler("Email: "));
            char[] senha = lerSenha("Senha" + FAIXA_SENHA);
            char[] confirmacao = null;
            try {
                confirmacao = lerSenha("Confirme a senha: ");
                if (!Arrays.equals(senha, confirmacao)) throw new IllegalArgumentException("Senhas nao conferem.");
                var admin = cadastros.cadastrarAdministradorInicial(dados, senha);
                saida.println("Administrador cadastrado. ID " + admin.id());
                saida.println("Abra o terminal e escolha Entrar. Use o e-mail e a senha cadastrados.");
            } finally {
                // finally limpa os arrays mesmo se confirmacao ou cadastro falhar. O service
                // recebe a senha apenas para derivacao; nao a inclui no registro de resposta.
                Arrays.fill(senha, '\0');
                if (confirmacao != null) Arrays.fill(confirmacao, '\0');
            }
        } catch (EOFException fim) {
            saida.println("Cadastro interrompido.");
        } catch (IllegalArgumentException | IllegalStateException erro) {
            saida.println("Cadastro nao concluido: " + erro.getMessage());
        } catch (SQLException erro) {
            saida.println("Falha no banco durante o cadastro. SQLState: " + erro.getSQLState());
        }
    }

    /** Entrada do terminal. Cada login abre apenas a area do perfil autenticado. */
    public void executarPorPerfil() throws IOException {
        try {
            menu("NEXUS DISPATCH", "1 Entrar | 2 Criar perfil | 0 Sair", opcao -> {
                switch (opcao) {
                    case 1 -> entrar();
                    case 2 -> cadastrarPerfil(true);
                    default -> throw new IllegalArgumentException("Opcao invalida.");
                }
            });
        } catch (EOFException fim) {
            saida.println("Entrada encerrada.");
        }
        saida.println("Ate logo.");
        saida.flush();
    }

    private void entrar() throws IOException, SQLException {
        String email = ler("E-mail (Enter para voltar): ");
        if (email.isEmpty()) return;

        char[] senha = lerSenha("Senha: ");
        Optional<UsuarioRegistro> autenticado;
        try {
            autenticado = usuarios.autenticar(email, senha);
        } finally {
            Arrays.fill(senha, '\0');
        }
        if (autenticado.isEmpty()) {
            saida.println("Não foi possível entrar. Confira o e-mail e a senha e tente novamente.");
            return;
        }

        UsuarioRegistro usuario = autenticado.orElseThrow();
        try {
            switch (usuario.perfil()) {
                case CLIENTE -> menuCliente(usuario);
                case LOJISTA -> menuProdutos(usuario.id(), "Sair da conta");
                case ENTREGADOR -> menuEntregador(usuario);
                case ADMIN -> menuAdministrador(usuario);
            }
            saida.println("Você saiu da conta.");
        } finally {
            idLojista = null;
        }
    }

    @FunctionalInterface
    // Cada submenu fornece uma lambda que interpreta sua opcao. O laco comum
    // cuida de repetir a leitura e tratar falhas, sem duplicar esse controle em cada tela.
    private interface AcaoMenu { void executar(int opcao) throws IOException, SQLException; }

    /** Mantem o usuario no submenu se uma operacao falhar. O zero sempre volta um nivel. */
    private void menu(String titulo, String opcoes, AcaoMenu acao) throws IOException {
        while (true) {
            mostrarMenu(titulo, opcoes);
            try {
                int opcao = inteiro("Opcao: ");
                if (opcao == 0) return;
                // O callback pode abrir outro menu. Quando ele retorna, este laco continua.
                // EOFException nao e capturada aqui: sobe ate o ponto de encerramento da sessao.
                acao.executar(opcao);
            } catch (IllegalArgumentException | IllegalStateException erro) {
                saida.println("Operacao nao concluida: " + erro.getMessage());
            } catch (SQLException erro) {
                if (erro.getErrorCode() == 1062) saida.println("Dados duplicados. Confira documento, email ou CNH.");
                else saida.println("Falha no banco. Confira a conexao e a operacao solicitada. SQLState: " + erro.getSQLState());
            }
        }
    }

    private void mostrarMenu(String titulo, String opcoes) {
        saida.println("\n=== " + titulo + " ===\n");
        for (String opcao : opcoes.split(" \\| |\\n")) {
            int separador = opcao.indexOf(' ');
            saida.println(opcao.substring(0, separador) + ". " + opcao.substring(separador + 1));
        }
        saida.println();
    }

    /** Escolhe uma loja para consulta de catalogo ou gestao administrativa. */
    private long lerIdDeLoja(String rotulo) throws IOException, SQLException {
        long depoisDe = 0;
        String ajuda = "? para listar";
        while (true) {
            String resposta = ler(rotulo + " (" + ajuda + ", 0 para voltar): ");
            if (resposta.equals("?")) {
                var pagina = usuarios.listarLojasAtivasApos(depoisDe, TAMANHO_PAGINA_PERFIS + 1);
                if (pagina.isEmpty()) {
                    saida.println("Nenhuma loja ativa encontrada nesta lista.");
                } else {
                    saida.println("\n=== LOJAS | NOMES E IDs ===");
                    for (var item : pagina.subList(0, Math.min(pagina.size(), TAMANHO_PAGINA_PERFIS))) {
                        saida.println("ID " + item.id() + " - " + item.nome());
                    }
                }
                boolean mais = pagina.size() > TAMANHO_PAGINA_PERFIS;
                depoisDe = mais ? pagina.get(TAMANHO_PAGINA_PERFIS - 1).id() : 0;
                ajuda = mais ? "? para próxima página" : "? para listar";
            } else {
                try {
                    long id = Long.parseLong(resposta);
                    if (id >= 0 && id <= Integer.MAX_VALUE) return id;
                } catch (NumberFormatException erro) {
                    // A mensagem abaixo permite corrigir o ID sem voltar ao menu.
                }
                saida.println("Digite um ID válido, ? para listar ou 0 para voltar.");
            }
        }
    }

    /** Todos os perfis exibem a mesma identidade por meio da classe base Usuario. */
    private static String tituloPerfil(Usuario perfil) {
        return perfil.getPerfil() + " | " + perfil.getNome();
    }

    /** Reutiliza os comandos de produto no menu do lojista e no do administrador. */
    private void menuProdutos(long id, String textoVoltar) throws IOException, SQLException {
        Lojista lojista = produtos.consultarLojista(id);
        idLojista = id;
        saida.println("Loja: " + lojista.getDadosLojista().orElseThrow().nomeFantasia() + " (ID " + id + ")");
        menu("CADASTRO DE PRODUTOS", "1 Cadastrar produto | 2 Listar produtos | 3 Buscar produto | 4 Atualizar produto\n"
                + "5 Repor estoque | 6 Ativar/desativar produto | 7 Remover produto | 0 " + textoVoltar, opcao -> {
            switch (opcao) {
                case 1 -> cadastrarProduto();
                case 2 -> listar();
                case 3 -> buscar();
                case 4 -> atualizar();
                case 5 -> resultado(produtos.reporEstoque(id, inteiro("ID do produto: "), inteiro("Quantidade de entrada: ")));
                case 6 -> resultado(produtos.definirAtivo(id, inteiro("ID do produto: "), simNao("Ativo (s/n): ")));
                case 7 -> remover();
                default -> throw new IllegalArgumentException("Opcao invalida.");
            }
        });
    }

    /** Disponibilidade operacional preserva o cadastro do entregador. */
    private void menuEntregador(UsuarioRegistro perfil) throws IOException, SQLException {
        var entregador = consultarEntregador(perfil.id());
        mostrarEntregador(entregador);
        menu(tituloPerfil(entregador), "1 Consultar perfil | 2 Ficar disponivel | 3 Ficar inativo | 0 Sair da conta", opcao -> {
            switch (opcao) {
                case 1 -> mostrarEntregador(consultarEntregador(perfil.id()));
                case 2, 3 -> {
                    resultado(entregadores.definirDisponivel(perfil.id(), opcao == 2));
                    mostrarEntregador(consultarEntregador(perfil.id()));
                }
                default -> throw new IllegalArgumentException("Opcao invalida.");
            }
        });
    }

    // Converte ausencia em erro de estado para a tela: o submenu ja selecionou
    // um perfil, portanto precisa tambem encontrar seu complemento de entregador.
    private Entregador consultarEntregador(long id) throws SQLException {
        return entregadores.buscar(id).orElseThrow(() -> new IllegalStateException("Entregador nao encontrado."));
    }

    // Exibe o estado operacional do registro consultado, sem inferir disponibilidade
    // apenas pela atividade do usuario. A impressao nao altera esse estado.
    private void mostrarEntregador(Entregador entregador) {
        var dados = entregador.toEntregadorRegistro();
        saida.println("\nID: " + dados.id());
        saida.println("Nome: " + entregador.getNome());
        saida.println("Veiculo: " + dados.tipoVeiculo());
        saida.println("Bairro de operacao: " + dados.bairroOperacao());
        saida.println("Situacao: " + dados.statusOperacao());
    }

    /** O carrinho dura somente a sessao do cliente. */
    private void menuCliente(UsuarioRegistro cliente) throws IOException {
        Map<Long, CompraService.ItemCarrinho> carrinho = new LinkedHashMap<>();
        menu(tituloPerfil(new Consumidor(cliente)),
                "1 Catalogo por loja | 2 Carrinho | 3 Meus pedidos | 0 Sair da conta", opcao -> {
            switch (opcao) {
                case 1 -> catalogo();
                case 2 -> menuCarrinho(cliente.id(), carrinho);
                case 3 -> menuPedidos(cliente.id());
                default -> throw new IllegalArgumentException("Opcao invalida.");
            }
        });
    }

    /** A pagina de catalogo exibe somente itens ativos e com saldo. */
    private void catalogo() throws IOException, SQLException {
        long id = lerIdDeLoja("ID da loja");
        if (id == 0) return;
        var pagina = produtos.listar(id, 20, inteiro("Pular quantos registros (0 para inicio): "));
        var disponiveis = pagina.stream().filter(p -> Boolean.TRUE.equals(p.ativo()) && p.estoqueDisponivel() > 0).toList();
        if (disponiveis.isEmpty()) saida.println("Nenhum produto disponivel nesta pagina.");
        for (var produto : disponiveis) mostrar(produto);
    }

    /** Inclusao e remocao editam apenas a sessao. O checkout faz a baixa no MySQL. */
    private void menuCarrinho(long cliente, Map<Long, CompraService.ItemCarrinho> carrinho) throws IOException {
        menu("CARRINHO | Cliente " + cliente, "1 Ver itens | 2 Adicionar | 3 Alterar quantidade\n"
                + "4 Retirar produto | 5 Revisar e finalizar | 0 Voltar", opcao -> {
            switch (opcao) {
                case 1 -> mostrarCarrinho(carrinho);
                case 2 -> editarCarrinho(carrinho, true);
                case 3 -> editarCarrinho(carrinho, false);
                case 4 -> {
                    long id = inteiro("ID do produto: ");
                    saida.println(carrinho.remove(id) == null ? "Produto ausente do carrinho." : "Produto retirado do carrinho.");
                }
                case 5 -> finalizarCompra(cliente, carrinho);
                default -> throw new IllegalArgumentException("Opcao invalida.");
            }
        });
    }

    // Adicionar soma a quantidade anterior; editar substitui a quantidade.
    // Consulta saldo para orientar a entrada, mas a confirmacao volta a valida-lo
    // na transacao porque outro cliente pode comprar durante a navegacao.
    private void editarCarrinho(Map<Long, CompraService.ItemCarrinho> carrinho, boolean adicionar)
            throws IOException, SQLException {
        long id = inteiro("ID do produto: ");
        var atual = carrinho.get(id);
        if (!adicionar && atual == null) throw new IllegalArgumentException("Produto ausente do carrinho.");
        if (atual == null && carrinho.size() >= 100) throw new IllegalStateException("Limite de 100 produtos distintos.");
        var produto = produtos.buscarDisponivelParaCompra(id)
                .orElseThrow(() -> new IllegalArgumentException("Produto indisponivel para compra."));
        int entrada = inteiro(adicionar ? "Quantidade a adicionar: " : "Nova quantidade: ");
        // A soma usa long para nao estourar int antes de conferir o limite do estoque.
        // Somente a quantidade positiva que cabe no saldo e convertida para int no item.
        long quantidade = entrada + (adicionar && atual != null ? (long) atual.quantidade() : 0L);
        if (entrada <= 0 || quantidade > produto.estoqueDisponivel())
            throw new IllegalArgumentException("Informe uma quantidade positiva dentro do estoque disponivel.");
        carrinho.put(id, new CompraService.ItemCarrinho(id, (int) quantidade, null));
        saida.println(produto.nome() + " | Quantidade no carrinho: " + quantidade);
    }

    // Precos mostrados aqui sao os atuais do catalogo. Produto indisponivel continua
    // no carrinho para o cliente poder corrigi-lo; nenhuma compra e criada ao listar.
    private void mostrarCarrinho(Map<Long, CompraService.ItemCarrinho> carrinho) throws SQLException {
        if (carrinho.isEmpty()) saida.println("Carrinho vazio.");
        for (var item : carrinho.values()) {
            var produto = produtos.buscarDisponivelParaCompra(item.idProduto());
            String detalhe = produto.map(p -> p.nome() + " | Subtotal R$ "
                    + p.precoUnitario().multiply(BigDecimal.valueOf(item.quantidade())).toPlainString()).orElse("Indisponivel");
            saida.println("ID " + item.idProduto() + " | Quantidade " + item.quantidade() + " | " + detalhe);
        }
    }

    /** Mostra os valores finais e solicita confirmacao antes de chamar o simulador. */
    private void finalizarCompra(long cliente, Map<Long, CompraService.ItemCarrinho> carrinho)
            throws IOException, SQLException {
        if (carrinho.isEmpty()) throw new IllegalStateException("Adicione produtos antes de finalizar.");
        var service = comprasConfiguradas();
        // Captura os itens desta confirmacao. O service compara a cotacao com os totais
        // recalculados ao comprar; uma mudanca exige nova revisao pelo cliente.
        var itens = List.copyOf(carrinho.values());
        var cotacao = service.cotar(cliente, itens);
        for (var item : itens) saida.println("Produto " + item.idProduto() + " | Quantidade " + item.quantidade());
        saida.println("Produtos R$ " + cotacao.produtos() + " | Taxa R$ " + cotacao.taxa() + " | Total R$ " + cotacao.total());
        int opcao = inteiro("Pagamento simulado: 1 PIX | 2 Cartao de credito | 3 Boleto | 0 Voltar: ");
        if (opcao == 0) return;
        MetodoPagamento metodo = switch (opcao) {
            case 1 -> MetodoPagamento.PIX;
            case 2 -> MetodoPagamento.CARTAO_CREDITO;
            case 3 -> MetodoPagamento.BOLETO;
            default -> throw new IllegalArgumentException("Metodo de pagamento invalido.");
        };
        if (!simNao("Confirmar pedido (s/n): ")) {
            saida.println("Pedido não enviado. Os produtos continuam no carrinho.");
            return;
        }
        var resultado = service.comprarConfirmado(cliente, itens, metodo, cotacao);
        mostrarResumoPedido(resultado.pedido());
        saida.println("Pagamento: " + resultado.pagamento().statusPagamento());
        // Limpa apenas apos autorizacao confirmada. Em recusa ou excecao, conserva
        // os itens da sessao para permitir revisao, sem prometer estoque reservado.
        if (resultado.pagamento().statusPagamento() == StatusPagamento.AUTORIZADO) carrinho.clear();
    }

    /** Operacoes de pedido usam a mesma taxa configurada na abertura da demonstracao. */
    private CompraService comprasConfiguradas() {
        if (compras == null) throw new IllegalStateException("Abra o terminal com -TaxaCompraPct para habilitar pedidos.");
        return compras;
    }

    // Historico e detalhe sempre levam o ID do cliente selecionado. No cancelamento,
    // mostra o pedido e pede confirmacao; o service decide se o estado ainda permite cancelar.
    private void menuPedidos(long cliente) throws IOException {
        var service = comprasConfiguradas();
        menu("MEUS PEDIDOS | Cliente " + cliente, "1 Historico | 2 Consultar pedido | 3 Cancelar pedido | 0 Voltar", opcao -> {
            switch (opcao) {
                case 1 -> {
                    var pedidos = service.listarHistorico(cliente, 20, inteiro("Pular quantos registros: "));
                    if (pedidos.isEmpty()) saida.println("Nenhum pedido nesta pagina.");
                    pedidos.forEach(this::mostrarResumoPedido);
                }
                case 2 -> mostrarPedido(service.buscarCompra(cliente, inteiro("ID do pedido: ")));
                case 3 -> {
                    int id = inteiro("ID do pedido: ");
                    var pedido = service.buscarCompra(cliente, id);
                    mostrarPedido(pedido);
                    if (pedido.isPresent() && simNao("Cancelar este pedido (s/n): ")) {
                        saida.println(service.cancelarCompra(cliente, id) ? "Pedido cancelado."
                                : "Cancelamento indisponivel. Confira o status do pedido e das comandas.");
                    }
                }
                default -> throw new IllegalArgumentException("Opcao invalida.");
            }
        });
    }

    // Resumo do pedido mestre: representa a compra inteira, nao uma comanda da loja.
    private void mostrarResumoPedido(PedidoMestreRegistro pedido) {
        saida.println("Pedido " + pedido.id() + " | Cliente " + pedido.idCliente() + " | " + pedido.statusPedido()
                + " | Total R$ " + pedido.valorTotal());
    }

    // Percorre compra -> comandas -> itens/ordens. Optional vazio e ausencia de
    // resultado; pagamento tambem e opcional em registros antigos ou incompletos.
    private void mostrarPedido(Optional<CompraService.DetalheCompra> encontrado) {
        if (encontrado.isEmpty()) { saida.println("Pedido nao encontrado."); return; }
        var detalhe = encontrado.orElseThrow();
        mostrarResumoPedido(detalhe.pedido());
        detalhe.pagamento().ifPresent(p -> saida.println("Pagamento: " + p.statusPagamento() + " | " + p.metodoPagamento()));
        for (var parte : detalhe.comandas()) {
            saida.println("Comanda " + parte.comanda().id() + " | Loja " + parte.comanda().idLojista()
                    + " | " + parte.comanda().statusComanda());
            for (var item : parte.itens()) saida.println("Produto " + item.idProduto() + " | Quantidade "
                    + item.quantidade() + " | Subtotal R$ " + item.subtotal());
            for (var ordem : parte.entregas()) saida.println("Entrega " + ordem.id() + " | " + ordem.statusEntrega());
        }
    }

    /** Area acessivel somente depois de autenticar uma conta ADMIN. */
    private void menuAdministrador(UsuarioRegistro admin) throws IOException {
        menu(tituloPerfil(new Administrador(admin)),
                "1 Gerenciar usuarios | 2 Gerenciar produtos por loja | 3 Consultar pedido | 0 Sair da conta", opcao -> {
            switch (opcao) {
                case 1 -> menuUsuarios();
                case 2 -> {
                    long id = lerIdDeLoja("ID da loja");
                    if (id != 0) menuProdutos(id, "Voltar");
                }
                case 3 -> mostrarPedido(comprasConfiguradas().buscarCompraComoAdministrador(admin.id(), inteiro("ID do pedido: ")));
                default -> throw new IllegalArgumentException("Opcao invalida.");
            }
        });
    }

    // Traduz opcoes da area administrativa para as operacoes comuns de cadastro.
    // Os numeros repassados a operarUsuario pertencem ao menu interno daquele metodo.
    private void menuUsuarios() throws IOException {
        menu("CADASTRO DE USUARIOS", "1 Listar usuarios | 2 Buscar usuario | 3 Cadastrar usuario | 4 Atualizar usuario\n"
                + "5 Ativar/desativar usuario | 6 Remover usuario | 7 Dados de lojista | 8 Dados de entregador | 0 Voltar", opcao -> {
            switch (opcao) {
                case 1 -> listarUsuarios();
                case 2 -> operarUsuario(1);
                case 3 -> cadastrarPerfil(true);
                case 4 -> operarUsuario(2);
                case 5 -> operarUsuario(3);
                case 6 -> operarUsuario(4);
                case 7 -> gerenciarLojista();
                case 8 -> gerenciarEntregador();
                default -> throw new IllegalArgumentException("Opcao invalida.");
            }
        });
    }

    /** Mantem menu ativo apos entrada invalida/erro SQL; EOF encerra sem efetivar entrada incompleta. */
    // Entrada legada mantida para os testes e demonstracoes anteriores. O main
    // atual usa executarPorPerfil; ambos reaproveitam os mesmos comandos de produto.
    public void executar() throws IOException {
        saida.println("Nexus Dispatch - Cadastros e Produtos");
        saida.println("Modo de demonstracao: selecione o ID de um lojista cadastrado (nao e login).");
        try {
            while (true) {
                saida.println("\nLojista: " + (idLojista == null ? "nao selecionado" : idLojista));
                mostrarMenu("CADASTROS E PRODUTOS", "1 Selecionar lojista | 2 Cadastrar produto | 3 Listar produtos | 4 Buscar produto\n"
                        + "5 Atualizar produto | 6 Repor estoque | 7 Ativar/desativar produto | 8 Remover produto\n"
                        + "9 Cadastrar usuario/perfil | 10 Listar usuarios | 11 Gerenciar usuario\n"
                        + "12 Gerenciar lojista | 13 Gerenciar entregador | 0 Sair");
                try {
                    int opcao = inteiro("Opcao: ");
                    if (opcao == 0) break;
                    if (opcao == 1) { selecionarLojista(); continue; }
                    if (opcao == 9) { cadastrarPerfil(false); continue; }
                    if (opcao == 10) { listarUsuarios(); continue; }
                    if (opcao == 11) { gerenciarUsuario(); continue; }
                    if (opcao == 12) { gerenciarLojista(); continue; }
                    if (opcao == 13) { gerenciarEntregador(); continue; }
                    if (opcao < 2 || opcao > 8) { saida.println("Opcao invalida."); continue; }
                    if (idLojista == null) { saida.println("Selecione um lojista primeiro."); continue; }
                    switch (opcao) {
                        case 2 -> cadastrarProduto();
                        case 3 -> listar();
                        case 4 -> buscar();
                        case 5 -> atualizar();
                        case 6 -> resultado(produtos.reporEstoque(idLojista, inteiro("ID do produto: "), inteiro("Quantidade de entrada: ")));
                        case 7 -> resultado(produtos.definirAtivo(idLojista, inteiro("ID do produto: "), simNao("Ativo (s/n): ")));
                        case 8 -> remover();
                        default -> throw new IllegalStateException("Opcao inesperada.");
                    }
                } catch (IllegalArgumentException | IllegalStateException erro) {
                    saida.println("Operacao nao concluida: " + erro.getMessage());
                } catch (SQLException erro) {
                    if (erro.getErrorCode() == 1062)
                        saida.println("Dados duplicados. Confira email, documento fiscal ou CNH ja cadastrados.");
                    else saida.println("Falha no banco. Confira conexao e integridade dos dados. SQLState: " + erro.getSQLState());
                }
            }
        } catch (EOFException fim) { saida.println("Entrada encerrada."); }
        saida.println("Ate logo.");
        saida.flush();
    }

    /** So troca o contexto depois de validar o perfil e o complemento no banco. */
    private void selecionarLojista() throws IOException, SQLException {
        long id = inteiro("ID do lojista: ");
        Lojista lojista = produtos.consultarLojista(id);
        idLojista = id;
        saida.println("Selecionado: " + lojista.getDadosLojista().orElseThrow().nomeFantasia()
                + " | perfil " + lojista.getPerfil());
    }

    /** Cadastros funcionam sem lojista selecionado e sem inserts manuais no SQL. */
    private void cadastrarPerfil(boolean menuPorPerfil) throws IOException, SQLException {
        if (cadastros == null) throw new IllegalStateException("Cadastros indisponiveis nesta instancia.");
        mostrarMenu("CRIAR PERFIL", "1 Cliente | 2 Lojista | 3 Entregador | 0 Voltar");
        int perfil = inteiro("Perfil: ");
        if (perfil == 0) return;
        if (perfil < 1 || perfil > 3) throw new IllegalArgumentException("Perfil invalido.");
        var dados = new CadastroService.DadosUsuario(ler("Nome/razao social: "), ler("Documento fiscal: "), ler("Email: "));
        CadastroService.DadosLojista loja = null;
        CadastroService.DadosEntregador entregador = null;
        if (perfil == 2) {
            loja = new CadastroService.DadosLojista(ler("Nome fantasia: "), decimal("Comissao em %: "),
                    ler("Bairro de operacao: "), ler("Endereco de origem: "));
        } else if (perfil == 3) {
            String veiculo = ler("Veiculo (BICICLETA/MOTO/CARRO/VAN): ").toUpperCase(java.util.Locale.ROOT);
            TipoVeiculo tipo;
            try { tipo = TipoVeiculo.valueOf(veiculo); }
            catch (IllegalArgumentException erro) { throw new IllegalArgumentException("Veiculo invalido."); }
            entregador = new CadastroService.DadosEntregador(ler("Numero da CNH (vazio para bicicleta): "), tipo, ler("Bairro de operacao: "));
        }
        char[] senha = lerLinha("Senha: " + FAIXA_SENHA).toCharArray();
        char[] confirmacao = null;
        try {
            confirmacao = lerLinha("Confirme a senha: ").toCharArray();
            if (!Arrays.equals(senha, confirmacao)) throw new IllegalArgumentException("Senhas nao conferem.");
            UsuarioRegistro salvo = switch (perfil) {
                case 1 -> cadastros.cadastrarCliente(dados, senha);
                case 2 -> cadastros.cadastrarLojista(dados, senha, loja);
                case 3 -> cadastros.cadastrarEntregador(dados, senha, entregador);
                default -> throw new IllegalStateException("Perfil inesperado.");
            };
            saida.println("\nPerfil cadastrado com sucesso!");
            mostrarUsuario(salvo);
            if (menuPorPerfil) {
                saida.println("No menu inicial, escolha Entrar e use o e-mail e a senha cadastrados.");
            } else if (salvo.perfil() == PerfilUsuario.LOJISTA) {
                idLojista = salvo.id();
                saida.println("Lojista selecionado para cadastrar produtos.");
            }
        } finally {
            Arrays.fill(senha, '\0');
            if (confirmacao != null) Arrays.fill(confirmacao, '\0');
        }
    }

    /** IDs/nome/perfil/atividade, sem documento, email ou credenciais na listagem. */
    private void listarUsuarios() throws IOException, SQLException {
        if (cadastros == null) throw new IllegalStateException("Cadastros indisponiveis nesta instancia.");
        var usuarios = cadastros.listarUsuarios(20, inteiro("Pular quantos registros (0 para inicio): "));
        if (usuarios.isEmpty()) saida.println("Nenhum usuario nesta pagina.");
        for (UsuarioRegistro usuario : usuarios) mostrarUsuario(usuario);
    }

    private void mostrarUsuario(UsuarioRegistro usuario) {
        saida.println("\nID: " + usuario.id());
        saida.println("Nome: " + usuario.nome());
        saida.println("Perfil: " + usuario.perfil());
        saida.println("Ativo: " + (Boolean.TRUE.equals(usuario.ativo()) ? "Sim" : "Nao"));
    }

    /** Gestao de dados comuns; confirma alvo antes de desativar/remover, sem trocar perfil. */
    private void gerenciarUsuario() throws IOException, SQLException {
        if (usuarios == null) throw new IllegalStateException("Gestao de usuarios indisponivel nesta instancia.");
        mostrarMenu("GERENCIAR USUARIO", "1 Buscar usuario | 2 Atualizar dados | 3 Ativar/desativar | 4 Remover usuario | 0 Voltar");
        int opcao = inteiro("Opcao: ");
        if (opcao == 0) return;
        if (opcao < 1 || opcao > 4) throw new IllegalArgumentException("Opcao invalida.");
        operarUsuario(opcao);
    }

    /** Executa uma acao administrativa sobre o usuario selecionado. */
    private void operarUsuario(int opcao) throws IOException, SQLException {
        long id = inteiro("ID do usuario: ");
        var atual = usuarios.buscar(id);
        if (atual.isEmpty()) { saida.println("Usuario nao encontrado."); return; }
        var usuario = atual.orElseThrow();
        mostrarUsuario(usuario);
        switch (opcao) {
            case 1 -> { }
            case 2 -> resultado(usuarios.atualizar(id, new CadastroService.DadosUsuario(
                    ler("Nome/razao social: "), ler("Documento fiscal: "), ler("Email: "))));
            case 3 -> {
                boolean ativo = simNao("Usuario ativo (s/n): ");
                boolean alterado = usuarios.definirAtivo(id, ativo);
                resultado(alterado);
                if (alterado && !ativo && idLojista != null && idLojista == id) idLojista = null;
            }
            case 4 -> {
                if (usuario.perfil() != PerfilUsuario.CLIENTE) {
                    saida.println("A remoção está disponível apenas para clientes inativos e sem histórico.");
                } else if (!Boolean.FALSE.equals(usuario.ativo())) {
                    saida.println("Antes de remover, escolha Ativar/desativar usuário e responda n para desativar o cliente.");
                } else if (simNao("Remover definitivamente este cliente (s/n): ")) {
                    resultado(usuarios.excluir(id));
                } else {
                    saida.println("Remocao cancelada.");
                }
            }
            default -> throw new IllegalStateException("Opcao inesperada.");
        }
    }

    /** Cadastro comercial nao modifica dados comuns, produtos ou comissoes historicas. */
    private void gerenciarLojista() throws IOException, SQLException {
        if (lojistas == null) throw new IllegalStateException("Gestao de lojistas indisponivel nesta instancia.");
        mostrarMenu("CADASTRO DE LOJISTAS", "1 Buscar lojista | 2 Listar lojistas | 3 Atualizar dados comerciais | 0 Voltar");
        int opcao = inteiro("Opcao: ");
        if (opcao == 0) return;
        if (opcao == 2) {
            for (var loja : lojistas.listar(20, inteiro("Pular quantos registros: "))) {
                saida.println("\nID: " + loja.id());
                saida.println("Nome fantasia: " + loja.nomeFantasia());
            }
            return;
        }
        if (opcao != 1 && opcao != 3) throw new IllegalArgumentException("Opcao invalida.");
        long id = inteiro("ID do lojista: ");
        var atual = lojistas.buscar(id);
        if (atual.isEmpty()) { saida.println("Lojista nao encontrado."); return; }
        var dados = atual.orElseThrow().getDadosLojista().orElseThrow();
        saida.println("\nID: " + dados.id());
        saida.println("Nome fantasia: " + dados.nomeFantasia());
        saida.println("Comissao: " + dados.taxaComissaoPct() + "%");
        saida.println("Bairro de operacao: " + dados.bairroOperacao());
        saida.println("Endereco de origem: " + dados.enderecoOrigem());
        if (opcao == 3) resultado(lojistas.atualizar(new LojistaRegistro(id, ler("Nome fantasia: "),
                decimal("Comissao em %: "), ler("Bairro de operacao: "), ler("Endereco de origem: "))));
    }

    /** Disponibilidade e alteracoes de veiculo passam pelo service, sem liberar EM_ROTA. */
    private void gerenciarEntregador() throws IOException, SQLException {
        if (entregadores == null) throw new IllegalStateException("Gestao de entregadores indisponivel nesta instancia.");
        mostrarMenu("CADASTRO DE ENTREGADORES", "1 Buscar entregador | 2 Atualizar cadastro | 3 Disponivel | 4 Inativo | 0 Voltar");
        int opcao = inteiro("Opcao: ");
        if (opcao == 0) return;
        if (opcao < 1 || opcao > 4) throw new IllegalArgumentException("Opcao invalida.");
        long id = inteiro("ID do entregador: ");
        var atual = entregadores.buscar(id);
        if (atual.isEmpty()) { saida.println("Entregador nao encontrado."); return; }
        var dados = atual.orElseThrow().toEntregadorRegistro();
        mostrarEntregador(atual.orElseThrow());
        if (opcao == 2) {
            TipoVeiculo tipo;
            try { tipo = TipoVeiculo.valueOf(ler("Veiculo (BICICLETA/MOTO/CARRO/VAN): ").toUpperCase(java.util.Locale.ROOT)); }
            catch (IllegalArgumentException erro) { throw new IllegalArgumentException("Veiculo invalido."); }
            resultado(entregadores.atualizar(new EntregadorRegistro(id, ler("Numero da CNH (vazio para bicicleta): "),
                    tipo, dados.statusOperacao(), ler("Bairro de operacao: "))));
        } else if (opcao == 3 || opcao == 4) resultado(entregadores.definirDisponivel(id, opcao == 3));
    }

    /** Nao corta espacos da senha; console nativo oculta, entrada redirecionada nao. */
    private char[] lerSenha(String mensagem) throws IOException {
        if (terminal != null) {
            char[] valor = terminal.readPassword("%s", mensagem);
            if (valor == null) throw new EOFException();
            return valor;
        }
        saida.println("Entrada sem ocultacao de senha neste ambiente.");
        return lerLinha(mensagem).toCharArray();
    }

    private void cadastrarProduto() throws IOException, SQLException {
        var produto = produtos.cadastrar(lerCadastro(null));
        saida.println("\nProduto cadastrado com sucesso!");
        mostrar(produto);
    }

    /** Le cadastro completo; estoque/atividade so sao pedidos na criacao. */
    private ProdutoRegistro lerCadastro(ProdutoRegistro atual) throws IOException {
        String nome = ler("Nome: ");
        String categoria = ler("Categoria: ");
        String descricao = ler("Descricao (vazia para nenhuma): ");
        BigDecimal preco = decimal("Preco (ex.: 12,50): ");
        BigDecimal peso = decimal("Peso em kg (ex.: 1,250): ");
        int estoque = atual == null ? inteiro("Estoque inicial: ") : atual.estoqueDisponivel();
        Boolean ativo = atual == null ? Boolean.valueOf(simNao("Ativo (s/n): ")) : atual.ativo();
        return new ProdutoRegistro(atual == null ? null : atual.id(), idLojista,
                nome, categoria, descricao, preco, peso, estoque, ativo);
    }

    /** Mostra pagina de ate 20 produtos; deslocamento permite percorrer o catalogo. */
    private void listar() throws IOException, SQLException {
        var pagina = produtos.listar(idLojista, 20, inteiro("Pular quantos registros (0 para inicio): "));
        if (pagina.isEmpty()) saida.println("Nenhum produto nesta pagina.");
        for (ProdutoRegistro produto : pagina) mostrar(produto);
    }

    /** Busca sempre limitada ao lojista selecionado. */
    private void buscar() throws IOException, SQLException {
        var produto = produtos.buscar(idLojista, inteiro("ID do produto: "));
        if (produto.isPresent()) mostrar(produto.orElseThrow());
        else saida.println("Produto nao encontrado para este lojista.");
    }

    /** Consulta primeiro e preserva saldo/atividade, que nao pertencem a edicao cadastral. */
    private void atualizar() throws IOException, SQLException {
        var atual = produtos.buscar(idLojista, inteiro("ID do produto: "));
        if (atual.isEmpty()) { saida.println("Produto nao encontrado para este lojista."); return; }
        mostrar(atual.orElseThrow());
        resultado(produtos.atualizar(lerCadastro(atual.orElseThrow())));
    }

    /** Confirma o produto concreto antes do DELETE; historico e protegido no service. */
    private void remover() throws IOException, SQLException {
        var atual = produtos.buscar(idLojista, inteiro("ID do produto: "));
        if (atual.isEmpty()) { saida.println("Produto nao encontrado para este lojista."); return; }
        mostrar(atual.orElseThrow());
        if (simNao("Remover definitivamente este produto (s/n): "))
            resultado(produtos.excluir(idLojista, atual.orElseThrow().id()));
        else saida.println("Remocao cancelada.");
    }

    /** Exibe somente dados do produto e nunca credenciais do usuario. */
    private void mostrar(ProdutoRegistro p) {
        saida.println("\nID: " + p.id());
        saida.println("Nome: " + p.nome());
        if (p.descricao() != null) saida.println("Descricao: " + p.descricao());
        saida.println("Categoria: " + p.categoria());
        saida.println("Preco: R$ " + p.precoUnitario().toPlainString());
        saida.println("Peso: " + p.pesoKg().toPlainString() + " kg");
        saida.println("Estoque: " + p.estoqueDisponivel());
        String atividade = p.ativo() == null ? "Não informado" : p.ativo() ? "Sim" : "Nao";
        saida.println("Ativo: " + atividade);
    }

    /** false tambem pode indicar estado/saldo indisponivel, nao sucesso silencioso. */
    private void resultado(boolean alterado) {
        saida.println(alterado ? "Operacao concluida." : "Nenhuma alteracao: confira ID, saldo e limites.");
    }

    /** Le uma linha inteira para evitar mistura de nextInt/nextLine e trata EOF. */
    private String ler(String mensagem) throws IOException {
        return lerLinha(mensagem).strip();
    }

    private String lerLinha(String mensagem) throws IOException {
        saida.print(mensagem); saida.flush();
        String linha = terminal == null ? entrada.readLine() : terminal.readLine();
        if (linha == null) throw new EOFException();
        return linha;
    }

    /** Inteiros invalidos interrompem a operacao e voltam ao menu. */
    private int inteiro(String mensagem) throws IOException {
        String valor = ler(mensagem);
        try { return Integer.parseInt(valor); }
        catch (NumberFormatException erro) { throw new IllegalArgumentException("Informe um numero inteiro valido.", erro); }
    }

    /** Aceita virgula ou ponto decimal, sem separador de milhar e sem double. */
    private BigDecimal decimal(String mensagem) throws IOException {
        String valor = ler(mensagem).replace(',', '.');
        try { return new BigDecimal(valor); }
        catch (NumberFormatException erro) { throw new IllegalArgumentException("Informe um decimal valido, sem separador de milhar.", erro); }
    }

    /** Pergunta explicita sem interpretar texto desconhecido como confirmacao. */
    private boolean simNao(String mensagem) throws IOException {
        String valor = ler(mensagem);
        if (valor.equalsIgnoreCase("s")) return true;
        if (valor.equalsIgnoreCase("n")) return false;
        throw new IllegalArgumentException("Responda s ou n.");
    }
}
