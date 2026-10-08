package com.nexusdispatch.model;

/** Representa o endereco de usuarios e lojas, vinculado a um bairro cadastrado. */
public class Endereco {

    private Long id;  // Identificador no banco; permanece null enquanto nao for atribuido.

    private String logradouro;   // Nome da rua, avenida ou outro logradouro do endereco.

    private String numero;  // String permite numeros com letras e valores como "s/n".

    private String complemento;   // Informacao opcional, como apartamento, bloco ou ponto de referencia.

    private String cep;   // String preserva zeros a esquerda; a regra aceita de 8 a 10 digitos, sem hifen.

    private Bairro bairro;   // Referencia ao objeto Bairro, que guarda o nome do bairro e a cidade.



    public Endereco(Bairro bairro, String cep, String complemento, String logradouro, String numero) {
        // Valida antes de guardar: o logradouro deve conter pelo menos uma letra.
        if( logradouro == null || logradouro.isBlank() || logradouro.codePoints().noneMatch(Character::isLetter)){
             throw new IllegalArgumentException("O nome do logradouro é obrigatório e não pode ser nulo nem ter somente espaços e deve conter pelo menos uma letra.");
        }

        // Primeiro verifica null para poder chamar os metodos de String com seguranca.
        if (numero == null || numero.isBlank()) {
            throw new IllegalArgumentException("O número é obrigatório.");
        }

        // strip() remove espacos externos; guardamos o resultado para validar e armazenar.
        String numeroTratado = numero.strip();
        // isDigit procura digitos; equalsIgnoreCase aceita "s/n" ou "S/N".
        // && exige as duas falhas: sem digito E diferente de "s/n".
        // Assim, "123", "12A" e "s/n" sao aceitos; "abc" é rejeitado.
        if (numeroTratado.codePoints().noneMatch(Character::isDigit)
                && !numeroTratado.equalsIgnoreCase("s/n")) {
            throw new IllegalArgumentException("O número deve conter pelo menos um dígito ou ser s/n.");
        }

        if ( cep == null || cep.isBlank()){
            throw new IllegalArgumentException("O CEP é obrigatório.");
        }

        // Remove espacos externos antes de contar os caracteres e conferir os digitos.
        String cepTratado = cep.strip();
        // || rejeita se qualquer regra falhar; !matches rejeita letras, hifens e espacos internos.
        // Esta validacao verifica o formato definido, nao se o CEP existe.
        if (cepTratado.length() < 8 || cepTratado.length() > 10 || !cepTratado.matches("[0-9]+")) {
            throw new IllegalArgumentException("O CEP deve conter somente números, entre 8 e 10 dígitos.");
        }

        // Bairro e um objeto: basta impedir uma referencia nula neste construtor.
        // Isso exige uma associacao, mas nao confirma cadastro no banco nem atendimento.
        if (bairro == null) {
            throw new IllegalArgumentException("O bairro é obrigatório.");
        }

        // Complemento e opcional. O if/else evita chamar strip() em null.
        if (complemento == null) {
            this.complemento = "";
        } else {
            this.complemento = complemento.strip();
        }

        // So guarda os dados depois que todas as validacoes passaram.
        // bairro guarda a referencia ao objeto recebido; os textos ficam sem espacos externos.
        this.bairro = bairro;
        this.cep = cepTratado;
        this.logradouro = logradouro.strip();
        this.numero = numeroTratado;
    }

    // Consulta o identificador; pode retornar null enquanto ele nao for atribuido.
    public Long getId(){
        return id;
    }

    // Consulta o CEP que foi validado e guardado pelo construtor.
    public String getCep(){
        return cep;
    }


    // Consulta o nome do logradouro.
    public String getLogradouro(){
        return logradouro;
    }

    // Consulta o numero do imovel, incluindo valores como "12A" ou "s/n".
    public String getNumero(){
        return numero;
    }

    // Consulta o complemento; retorna texto vazio quando nao foi informado.
    public String getComplemento(){
        return complemento;
    }

    // Consulta a referencia ao objeto Bairro associado a este endereco.
    public Bairro getBairro(){
        return bairro;
    }

        // Altera o logradouro somente se houver pelo menos uma letra.
    // A validacao ocorre antes da atribuicao, preservando o valor anterior em caso de erro.
    public void setLogradouro(String logradouro) {
        if (logradouro == null || logradouro.isBlank()
                || logradouro.codePoints().noneMatch(Character::isLetter)) {
            throw new IllegalArgumentException(
                    "O logradouro é obrigatório e deve conter pelo menos uma letra.");
        }

        this.logradouro = logradouro.strip();
    }

    // Altera o numero, aceitando pelo menos um digito ou a indicacao "s/n".
    public void setNumero(String numero) {
        if (numero == null || numero.isBlank()) {
            throw new IllegalArgumentException("O número é obrigatório.");
        }

        String numeroTratado = numero.strip();

        // Rejeita quando nao ha digito E o texto nao corresponde a "s/n".
        if (numeroTratado.codePoints().noneMatch(Character::isDigit)
                && !numeroTratado.equalsIgnoreCase("s/n")) {
            throw new IllegalArgumentException(
                    "O número deve conter pelo menos um dígito ou ser s/n.");
        }

        this.numero = numeroTratado;
    }

    // Altera o CEP, mantendo a regra de 8 a 10 digitos numericos.
    public void setCep(String cep) {
        if (cep == null || cep.isBlank()) {
            throw new IllegalArgumentException("O CEP é obrigatório.");
        }

        String cepTratado = cep.strip();

        // Verifica o tamanho e rejeita qualquer caractere que nao seja de 0 a 9.
        if (cepTratado.length() < 8 || cepTratado.length() > 10
                || !cepTratado.matches("[0-9]+")) {
            throw new IllegalArgumentException(
                    "O CEP deve conter somente números, entre 8 e 10 dígitos.");
        }

        this.cep = cepTratado;
    }

    // Altera ou remove o complemento, que e opcional.
    // Quando recebe null, guarda uma String vazia.
    public void setComplemento(String complemento) {
        if (complemento == null) {
            this.complemento = "";
        } else {
            this.complemento = complemento.strip();
        }
    }

    // Altera a referencia ao bairro, impedindo que o endereco fique sem bairro.
    // Esta verificacao nao confirma cadastro no banco nem area de atendimento.
    public void setBairro(Bairro bairro) {
        if (bairro == null) {
            throw new IllegalArgumentException("O bairro é obrigatório.");
        }

        this.bairro = bairro;
    }


}
