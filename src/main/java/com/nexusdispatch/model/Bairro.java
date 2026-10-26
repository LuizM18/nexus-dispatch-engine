package com.nexusdispatch.model;

/** Representa um bairro da base do Nexus; a regra de proximidade sera refinada. */
public class Bairro {
    // Identificador no banco; permanece null enquanto nao for atribuido.
    private Long id;
    private String nome;
    private String cidade;
    private boolean atendido;
    
    public Bairro(String nome, String cidade, boolean atendido) {
        // Verifica null primeiro para nao chamar isBlank() em um valor inexistente.
        // isBlank() identifica texto vazio ou composto apenas por espacos.
        // noneMatch(Character::isLetter) e true quando nao ha nenhuma letra, inclusive acentuada.
        if (nome == null || nome.isBlank() || nome.codePoints().noneMatch(Character::isLetter)) {
            throw new IllegalArgumentException("O nome do bairro é obrigatório e deve conter pelo menos uma letra.");
        }

        // throw interrompe a criacao do objeto quando o argumento e invalido.
        if (cidade == null || cidade.isBlank() || cidade.codePoints().noneMatch(Character::isLetter)) {
            throw new IllegalArgumentException("O nome da cidade é obrigatório e deve conter pelo menos uma letra.");
        }

        // Guarda os valores somente depois das validacoes.
        // strip() devolve o texto sem espacos no inicio e no fim.
        this.nome = nome.strip();
        this.cidade = cidade.strip();
        // Tanto true quanto false sao valores validos para atendido.
        this.atendido = atendido;
    }

    // public permite consultar o id a partir de outras classes.
    // Long e o tipo do valor devolvido; return entrega o atributo ao chamador.
    public Long getId() {
        return id;
    }

    // Consulta o nome sem modificar o atributo privado.
    public String getNome() {
        return nome;
    }

    // Devolve a cidade guardada neste objeto Bairro.
    public String getCidade() {
        return cidade;
    }

    // Para getters booleanos, usamos o prefixo is por convencao.
    // Retorna true se o bairro e atendido; false caso contrario.
    public boolean isAtendido() {
        return atendido;
    }

    // Setter: permite alterar o nome de um bairro que ja foi criado.
    // void significa que o metodo nao devolve um valor.
    // String nome e o parametro que recebe o novo nome.
    public void setNome(String nome) {
        // || significa "ou". Se nome for null, isBlank() nao sera executado.
        // A mesma regra do construtor deve valer para alteracoes posteriores.
        // codePoints() percorre o texto; isLetter verifica quais caracteres sao letras.
        if (nome == null || nome.isBlank() || nome.codePoints().noneMatch(Character::isLetter)) {
            // throw lanca a excecao e interrompe o metodo antes da atribuicao.
            // Assim, o nome anterior permanece intacto quando o novo e invalido.
            throw new IllegalArgumentException("O nome do bairro é obrigatório e deve conter pelo menos uma letra.");
        }

        // this.nome e o atributo do objeto; nome e o parametro recebido.
        // strip() remove os espacos das extremidades do novo texto.
        this.nome = nome.strip();
    }

    // Permite corrigir a cidade sem aceitar valores nulos ou em branco.
    public void setCidade(String cidade) {
        // Valida antes de modificar o atributo, preservando o valor anterior em caso de erro.
        if (cidade == null || cidade.isBlank() || cidade.codePoints().noneMatch(Character::isLetter)) {
            throw new IllegalArgumentException("O nome da cidade é obrigatório e deve conter pelo menos uma letra.");
        }

        // Guarda no objeto o novo valor recebido, sem espacos nas extremidades.
        this.cidade = cidade.strip();
    }

    // Altera se o Nexus atende este bairro.
    // boolean so admite true ou false; ambos sao validos, entao nao precisamos de um if.
    public void setAtendido(boolean atendido) {
        // Copia o valor do parametro para o atributo deste objeto.
        this.atendido = atendido;
    }

    // Nao ha setId: a atribuicao do identificador sera definida na integracao com o banco.
}
