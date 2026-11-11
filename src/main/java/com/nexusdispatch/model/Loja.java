package com.nexusdispatch.model;

/** Loja local ou visao de consulta do cadastro comercial do lojista; sem tabela loja. */
public class Loja {
    private boolean visaoPersistida;
    private String enderecoOrigem;
    private String bairroOperacao;

    /**
     * Visao comercial JDBC. O ID e o do lojista, nao uma nova chave de loja.
     * SQL nao informa CNPJ separado, endereco estruturado ou horario de abertura.
     */
    public Loja(Lojista lojista) {
        validarLojista(lojista);
        LojistaRegistro dados = lojista.getDadosLojista().orElseThrow(
                () -> new IllegalArgumentException("Lojista precisa do complemento persistido."));
        this.lojista = lojista;
        this.id = dados.id();
        this.nome = dados.nomeFantasia();
        this.enderecoOrigem = dados.enderecoOrigem();
        this.bairroOperacao = dados.bairroOperacao();
        this.cnpj = null;
        this.visaoPersistida = true;
    }

    /** Origem textual preservada do lojista; nao e um Endereco estruturado. */
    public String getEnderecoOrigem() { return enderecoOrigem; }

    /** Bairro textual informado pelo banco; null no modelo local antigo. */
    public String getBairroOperacao() { return bairroOperacao; }

    /** Documento do usuario; nao afirma que todo documento e um CNPJ. */
    public String getDocumentoFiscal() { return lojista.getDocumentoFiscal(); }

    /** NULL significa abertura desconhecida: nao ha coluna para esse estado. */
    public Boolean getStatusAberta() { return visaoPersistida ? null : aberta; }

    /** Visao JDBC nao altera copias de campos cujo dono e LojistaDao. */
    private void exigirModeloLocal() {
        if (visaoPersistida)
            throw new IllegalStateException("Visao JDBC: altere o lojista pelo DAO/service e consulte novamente.");
    }

    // O construtor local deixa o ID null.
    // A visao JDBC recebe o ID do lojista; nao existe uma tabela loja separada.
    private Long id;

    private String nome;

    // final impede trocar estas referências após o construtor.
    // Não haverá setters para CNPJ e lojista responsável.
    private final String cnpj;
    private final Lojista lojista;

    // Reaproveitamos a classe Endereco, sem duplicar seus atributos.
    private Endereco endereco;

    // true: recebe novas compras.
    // false: está fechada para novas compras.
    private boolean aberta;

    public Loja(
            String nome,
            String cnpj,
            Endereco endereco,
            Lojista lojista) {

        // Verificamos os dados antes de atribuí-los aos atributos.
        validarNome(nome);
        validarCnpj(cnpj);
        validarEndereco(endereco);
        validarLojista(lojista);

        // this.nome é o atributo; nome é o parâmetro recebido.
        // strip() remove espaços no início e no fim do texto.
        this.nome = nome.strip();
        this.cnpj = cnpj.strip();

        this.endereco = endereco;
        this.lojista = lojista;

        // Toda loja começa fechada.
        // O lojista poderá abri-la posteriormente.
        this.aberta = false;
    }

    // GETTERS: permitem consultar os dados da loja.

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getCnpj() {
        // null na visao JDBC: documento fiscal do usuario nao e CNPJ separado.
        return cnpj;
    }

    public Endereco getEndereco() {
        // null na visao JDBC; use getEnderecoOrigem para o texto realmente salvo.
        return endereco;
    }

    public Lojista getLojista() {
        return lojista;
    }

    // Para um atributo boolean, usamos o prefixo "is".
    public boolean isAberta() {
        exigirModeloLocal();
        return aberta;
    }

    // ALTERAÇÕES PERMITIDAS

    public void setNome(String nome) {
        exigirModeloLocal();
        // Se o nome for inválido, a exceção interrompe o método.
        // Nesse caso, o nome anterior permanece no atributo.
        validarNome(nome);

        this.nome = nome.strip();
    }

    public void setEndereco(Endereco endereco) {
        exigirModeloLocal();
        // A loja não pode ficar sem endereço de coleta.
        validarEndereco(endereco);

        this.endereco = endereco;
    }

    // Estas operações alteram apenas o estado da loja.
    // O serviço deverá verificar se quem chamou tem permissão.
    public void abrir() {
        exigirModeloLocal();
        this.aberta = true;
    }

    public void fechar() {
        exigirModeloLocal();
        this.aberta = false;
    }

    // VALIDAÇÕES INTERNAS
    //
    // private: acessíveis diretamente apenas dentro desta classe.
    // static: trabalham com os parâmetros, sem depender de this.

    private static void validarNome(String nome) {

        // || significa "ou".
        // Se nome for null, isBlank() não será chamado.
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException(
                    "O nome da loja é obrigatório.");
        }

        // Nesta versão, exigimos somente um nome preenchido.
        // Outras restrições de nome poderão ser definidas pelo grupo.
    }

    private static void validarCnpj(String cnpj) {

        // CNPJ é obrigatório.
        // Se for null, o operador || impede a chamada de isBlank().
        if (cnpj == null || cnpj.isBlank()) {
            throw new IllegalArgumentException(
                    "O CNPJ da loja é obrigatório.");
        }

        // Remove somente espaços no início e no fim.
        // Não remove letras, pontuação nem espaços internos.
        String cnpjTratado = cnpj.strip();

        // O formato numérico adotado pelo projeto exige 14 caracteres.
        // Como String, os zeros iniciais são preservados.
        if (cnpjTratado.length() != 14) {
            throw new IllegalArgumentException(
                    "O CNPJ deve conter exatamente 14 dígitos.");
        }

        // [0-9] permite somente os algarismos de zero a nove.
        // + exige uma ou mais ocorrências.
        // matches verifica o texto inteiro.
        // ! significa "não": rejeitamos se não corresponder ao padrão.
        if (!cnpjTratado.matches("[0-9]+")) {
            throw new IllegalArgumentException(
                    "O CNPJ deve conter somente números, sem pontos, "
                            + "barra, hífen ou espaços internos.");
        }

        // Ainda não calculamos os dígitos verificadores.
        // Passar aqui significa apenas que o formato foi aceito.
        // CNPJ duplicado sera impedido pelo servico e por UNIQUE no banco.
        // Esta entidade sozinha nao conhece as outras lojas cadastradas.
    }

    private static void validarEndereco(Endereco endereco) {

        // Exige uma referência para um objeto Endereco.
        // Os campos internos são validados pela própria classe Endereco.
        if (endereco == null) {
            throw new IllegalArgumentException(
                    "O endereço de coleta da loja é obrigatório.");
        }

        // Conferir se o bairro é atendido depende do serviço.
    }

    private static void validarLojista(Lojista lojista) {

        // Toda loja precisa de um responsável.
        if (lojista == null) {
            throw new IllegalArgumentException(
                    "O lojista responsável é obrigatório.");
        }

        // Este método não consegue saber se o lojista já possui outra loja.
        // Essa regra será garantida pelo serviço e pelo banco.
    }
}
