package com.nexusdispatch.model;

// Produto pertence a uma loja. Preco e estoque ficam em OfertaProduto.
public class Produto {
    // Constantes compartilhadas: static pertence a classe e final impede reatribuicao.
    private static final int LIMITE_NOME = 120;
    private static final int LIMITE_DESCRICAO = 1000;

    // Long aceita null antes da futura atribuicao pelo banco.
    private Long id;
    // Nao podemos trocar a loja do produto. Os dados da loja ainda sao mutaveis.
    private final Loja loja;
    // Acesso privado: alteracoes passam pelos setters e suas validacoes.
    private String nome;
    private String descricao;

    // Construtor chamado por new Produto(loja, nome, descricao).
    public Produto(Loja loja, String nome, String descricao) {
        if (loja == null) {
            throw new IllegalArgumentException("A loja do produto é obrigatória.");
        }
        // Primeiro verifica todos os textos; so depois guarda os dados.
        String nomeTratado = validarENormalizarNome(nome);
        String descricaoTratada = validarENormalizarDescricao(descricao);
        this.loja = loja;
        this.nome = nomeTratado;
        this.descricao = descricaoTratada;
    }

    // Getters consultam valores; return entrega o resultado ao chamador.
    public Long getId() {
        return id;
    }

    public Loja getLoja() {
        return loja;
    }

    public String getNome() {
        return nome;
    }

    public String getDescricao() {
        // Pode ser null: descricao e opcional.
        return descricao;
    }

    public void setNome(String nome) {
        // Java avalia o lado direito antes de atribuir ao atributo this.nome.
        // Uma excecao impede a atribuicao, preservando o nome anterior.
        this.nome = validarENormalizarNome(nome);
    }

    public void setDescricao(String descricao) {
        // null ou texto em branco remove a descricao.
        this.descricao = validarENormalizarDescricao(descricao);
    }

    // private restringe os auxiliares a esta classe.
    // static: usam parametros, sem depender de atributos ou de this.
    private static String validarENormalizarNome(String nome) {
        // || nao chama isBlank se o primeiro teste ja encontrou null.
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("O nome do produto é obrigatório.");
        }
        // Remove espacos externos, preservando os espacos entre palavras.
        String nomeTratado = nome.strip();
        // Conta pontos de codigo Unicode; length fornece o indice final UTF-16.
        // Um caractere suplementar nao e contado como dois.
        int quantidadeCaracteres = nomeTratado.codePointCount(0, nomeTratado.length());
        if (quantidadeCaracteres > LIMITE_NOME) {
            throw new IllegalArgumentException(
                    "O nome do produto deve ter no máximo " + LIMITE_NOME + " caracteres.");
        }
        // noneMatch e true quando nao existe letra, inclusive considerando acentos.
        // "123" e recusado; "Arroz 1 kg" e aceito.
        if (nomeTratado.codePoints().noneMatch(Character::isLetter)) {
            throw new IllegalArgumentException("O nome do produto deve conter pelo menos uma letra.");
        }
        return nomeTratado;
    }

    private static String validarENormalizarDescricao(String descricao) {
        // Padroniza ausencia de descricao como null, nao como texto vazio.
        if (descricao == null || descricao.isBlank()) {
            return null;
        }
        String descricaoTratada = descricao.strip();
        int quantidadeCaracteres = descricaoTratada.codePointCount(0, descricaoTratada.length());
        if (quantidadeCaracteres > LIMITE_DESCRICAO) {
            // Nao corta o texto automaticamente: pede que o chamador o corrija.
            throw new IllegalArgumentException(
                    "A descrição deve ter no máximo " + LIMITE_DESCRICAO + " caracteres.");
        }
        // Descricao pode conter somente numeros, como medidas.
        return descricaoTratada;
    }

    // Sem setLoja e setId. Persistencia ainda sera implementada.
    // Setters validam dados, nao permissoes. O servico devera conferir o lojista.
}
