package com.nexusdispatch.model;

// abstract impede new Usuario(...); os perfis concretos herdam estes dados.
public abstract class Usuario {
    // private impede alteracao direta por outras classes, inclusive subclasses.
    // Long aceita null; o banco atribuira o ID na futura persistencia.
    private Long id;
    private String nome;
    private String email;
    // null representa telefone nao informado.
    private String telefone;
    // Guarda somente o hash recebido do servico, nunca a senha original.
    private String senhaHash;

    // protected permite que subclasses chamem super(...) e acesso no pacote.
    // O servico devera gerar o hash usando uma biblioteca propria para senhas.
    protected Usuario(String nome, String email, String telefone, String senhaHash) {
        // Se uma validacao lancar excecao, a criacao nao termina normalmente.
        validarNome(nome);
        validarEmail(email);
        validarSenhaHash(senhaHash);

        // this.nome e o atributo; nome e o parametro recebido.
        // strip remove espacos externos, preservando os espacos entre palavras.
        this.nome = nome.strip();
        this.email = email.strip();
        this.telefone = normalizarTelefone(telefone);
        // Nao modificar o hash: preservar exatamente o resultado da biblioteca.
        this.senhaHash = senhaHash;
    }

    // Getters consultam o estado sem modifica-lo.
    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public String getTelefone() {
        return telefone;
    }

    // Apenas para autenticacao. Nao expor em telas, respostas da API ou logs.
    // O futuro controller devera usar respostas que excluam este campo.
    public String getSenhaHash() {
        return senhaHash;
    }

    public void setNome(String nome) {
        // Valida antes de atribuir: se houver erro, o nome anterior permanece.
        validarNome(nome);
        this.nome = nome.strip();
    }

    public void setTelefone(String telefone) {
        // null ou texto em branco remove o telefone, pois ele e opcional.
        this.telefone = normalizarTelefone(telefone);
    }

    // private restringe estes auxiliares a esta classe.
    // static indica que usam parametros, sem consultar atributos ou this.
    private static void validarNome(String nome) {
        // || interrompe a avaliacao ao encontrar true: nao chama isBlank em null.
        // isBlank identifica texto vazio ou composto somente de espacos.
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("O nome é obrigatório.");
        }
        // codePoints percorre Unicode; isLetter reconhece inclusive acentos.
        // noneMatch retorna true se NENHUM caractere for letra.
        // Exige pelo menos uma letra, nao exclusivamente letras.
        if (nome.codePoints().noneMatch(Character::isLetter)) {
            throw new IllegalArgumentException("O nome deve conter pelo menos uma letra.");
        }
    }

    private static void validarEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("O email é obrigatório.");
        }
        String emailTratado = email.strip();
        // matches confere uma expressao regular; ! inverte o resultado.
        // Exige partes antes/depois de @ e ponto no dominio, sem espacos.
        // (?U) faz a classe de espacos considerar Unicode.
        // Cada barra invertida da regex precisa ser duplicada na String Java.
        if (!emailTratado.matches("(?U)^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("Informe um email com formato válido.");
        }
        // Regra basica: nao comprova existencia, propriedade ou unicidade.
        // A unicidade sera garantida pelo banco e tratada no servico.
    }

    private static void validarSenhaHash(String senhaHash) {
        // Exige texto preenchido, mas nao comprova algoritmo nem seguranca.
        // O formulario nao deve fornecer um hash escolhido pelo usuario.
        if (senhaHash == null || senhaHash.isBlank()) {
            throw new IllegalArgumentException("O hash da senha é obrigatório.");
        }
    }

    private static String normalizarTelefone(String telefone) {
        // Normalizar padroniza o dado; nao e verificar se o numero existe.
        if (telefone == null || telefone.isBlank()) {
            return null;
        }
        // Formato, DDD e limites de tamanho ainda serao definidos.
        return telefone.strip();
    }

    // Sem setters para ID, email e hash: persistencia e alteracoes sensiveis
    // precisam de fluxos proprios, que ainda serao implementados.
}
