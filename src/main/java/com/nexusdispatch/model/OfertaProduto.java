package com.nexusdispatch.model;

// BigDecimal representa numeros decimais, como precos, sem usar double.
// Ao criar um preco, prefira new BigDecimal("12.50") para preservar o decimal.
import java.math.BigDecimal;
// LocalDateTime guarda data e hora, mas nao inclui fuso horario.
import java.time.LocalDateTime;
// ArrayList e a implementacao da lista usada para guardar as entradas.
import java.util.ArrayList;
// List define as operacoes de uma lista; <EntradaEstoque> restringe seu conteudo.
import java.util.List;

/** Preco e estoque de um produto em uma loja. Historico ainda somente em memoria. */
public class OfertaProduto {
    // private: acessivel diretamente apenas nesta classe.
    // static: a constante pertence a classe, compartilhada por todas as ofertas.
    // final: seu valor nao pode ser reatribuido. A reserva e sempre tres.
    private static final int RESERVA_MINIMA = 3;

    // Long permite null: o ID sera atribuido na futura integracao com o banco.
    private Long id;
    // Uma oferta nao muda de produto nem de loja depois de criada.
    // final fixa as referencias, mas nao torna os objetos Produto e Loja imutaveis.
    private final Produto produto;
    // A loja vem do produto: nao guardamos outra referencia que possa divergir.
    // Cada loja cadastra seus proprios produtos, precos e estoques.
    private BigDecimal preco;
    // Estoque fisico total, incluindo as tres unidades que nao podem ser vendidas.
    // Atributos int comecam em zero; variaveis locais nao recebem esse padrao.
    private int estoque;
    // Cada oferta possui sua propria lista, inicialmente vazia.
    // final impede substituir a lista; ainda podemos adicionar elementos nela.
    // <> infere o tipo EntradaEstoque a partir da declaracao a esquerda.
    private final List<EntradaEstoque> entradas = new ArrayList<>();

    // record agrupa dados imutaveis: cada entrada preserva os saldos daquele momento.
    // Este tipo e aninhado e implicitamente static, mantido neste mesmo arquivo.
    // Java gera construtor e metodos de consulta: quantidade(), estoqueAnterior(),
    // estoqueApos() e dataHora(). Nao sao chamados getQuantidade(), por exemplo.
    // Os componentes sao finais; aqui int e LocalDateTime permitem um registro
    // imutavel. Mudar o estoque da oferta nao muda uma entrada ja registrada.
    public record EntradaEstoque(
            int quantidade,
            int estoqueAnterior,
            int estoqueApos,
            LocalDateTime dataHora) {
    }

    // Construtor: executado por new OfertaProduto(...).
    // Os parametros sao dados recebidos; this identifica atributos deste objeto.
    public OfertaProduto(Produto produto, BigDecimal preco, int estoqueInicial) {
        // Produto ja exige loja em seu construtor; nao repetimos esse parametro.
        // throw interrompe a criacao; o chamador nao recebe uma oferta pronta.
        if (produto == null) {
            throw new IllegalArgumentException("O produto é obrigatório.");
        }
        // Reaproveitamos a mesma regra usada depois na alteracao do preco.
        validarPreco(preco);
        if (estoqueInicial < 0) {
            throw new IllegalArgumentException("O estoque inicial não pode ser negativo.");
        }
        // As atribuicoes acontecem depois de conferir os dados obrigatorios.
        this.produto = produto;
        this.preco = preco;
        // int inicia em zero. Estoque inicial positivo tambem gera uma entrada.
        // O auxiliar privado evita chamar metodo sobrescrevivel no construtor.
        if (estoqueInicial > 0) {
            registrarEntrada(estoqueInicial);
        }
    }

    // GETTERS: consultam os atributos sem alterar o estado da oferta.
    // return entrega o valor ou referencia a quem chamou o metodo.
    public Long getId() {
        return id;
    }

    public Produto getProduto() {
        return produto;
    }

    public Loja getLoja() {
        // A oferta pertence obrigatoriamente a mesma loja do produto.
        return produto.getLoja();
    }

    public BigDecimal getPreco() {
        return preco;
    }

    public int getEstoque() {
        return estoque;
    }

    public int getQuantidadeDisponivelParaVenda() {
        // Estoque fisico pode ser 0, 1, 2 ou 3, mas nada fica disponivel para venda.
        // Math.max escolhe o maior dos dois valores: nunca devolvemos negativo.
        // Exemplo: estoque 12 -> max(0, 9) -> nove unidades para venda.
        // Este metodo so calcula; nao retira, bloqueia ou reserva unidades.
        return Math.max(0, estoque - RESERVA_MINIMA);
    }

    public List<EntradaEstoque> getEntradasEstoque() {
        // Devolve uma copia nao modificavel. O chamador nao apaga nem insere entradas.
        // Uma copia ja obtida tambem nao muda quando chegam novas entradas.
        // Tentar add/remove/clear nessa copia causa UnsupportedOperationException.
        // Copia a lista, nao cada registro; os registros ja sao imutaveis aqui.
        return List.copyOf(entradas);
    }

    public void setPreco(BigDecimal preco) {
        // Se validarPreco lancar excecao, a atribuicao abaixo nao e executada.
        // O preco anterior permanece no objeto, sem precisar restaura-lo.
        validarPreco(preco);
        this.preco = preco;
        // ItemPedido devera guardar seu proprio preco historico na confirmacao.
    }

    public void adicionarEstoque(int quantidade) {
        // Operacao publica para reposicao: passa a quantidade ao auxiliar privado.
        // Exemplo: estoque 10, entrada 5 -> novo estoque 15, e nao 5.
        // void indica que nao ha valor de retorno; o estado do objeto e alterado.
        registrarEntrada(quantidade);
    }

    private void registrarEntrada(int quantidade) {
        // Uma entrada precisa aumentar o saldo; zero e negativos sao recusados.
        // Retiradas de estoque precisarao de outra operacao, ainda nao implementada.
        if (quantidade <= 0) {
            throw new IllegalArgumentException("A entrada de estoque deve ser maior que zero.");
        }
        // Variavel local para calcular o resultado sem alterar o atributo estoque.
        int novoEstoque;
        try {
            // try executa uma operacao que pode lancar uma excecao.
            // Soma comum poderia ultrapassar o limite de int e produzir valor negativo.
            // addExact detecta esse estouro. int vai ate 2.147.483.647.
            novoEstoque = Math.addExact(estoque, quantidade);
        } catch (ArithmeticException erro) {
            // catch trata especificamente o estouro da soma, traduzindo a mensagem.
            // O segundo argumento preserva a causa original para diagnostico.
            // throw impede continuar para a gravacao do historico e do saldo.
            throw new IllegalArgumentException("A entrada ultrapassa o limite de estoque.", erro);
        }
        // Calcula e valida antes de alterar. Entradas recusadas nao mudam o historico.
        // Estoque ainda e o saldo anterior; novoEstoque e o resultado da soma.
        // now() usa o relogio e o fuso padrao do ambiente onde Java esta rodando.
        EntradaEstoque entrada = new EntradaEstoque(
                quantidade, estoque, novoEstoque, LocalDateTime.now());
        // Guarda o evento na lista e depois atualiza o saldo atual.
        // Essa sequencia nao substitui uma transacao de banco nem protege threads.
        entradas.add(entrada);
        estoque = novoEstoque;
    }

    private static void validarPreco(BigDecimal preco) {
        // Este auxiliar so usa seu parametro; por isso pode ser static.
        // Conferir null primeiro evita chamar signum() em uma referencia ausente.
        if (preco == null) {
            throw new IllegalArgumentException("O preço é obrigatório.");
        }
        // signum retorna -1 para negativo, 0 para zero e 1 para positivo.
        // Zero e permitido pelo requisito atual. Nao arredondamos silenciosamente.
        // Esta versao ainda nao restringe casas decimais: 10.555 tambem passa.
        if (preco.signum() < 0) {
            throw new IllegalArgumentException("O preço não pode ser negativo.");
        }
    }

    // Sem setEstoque: reposicoes passam por adicionarEstoque e geram historico.
    // Baixas, concorrencia e persistencia serao integradas ao servico de compra.
    // O historico vive apenas na memoria e e perdido ao encerrar a aplicacao.
    // O servico tambem devera verificar quem pode repor estoque ou mudar o preco.
}
