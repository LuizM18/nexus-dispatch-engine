package com.nexusdispatch.model;

import com.nexusdispatch.enums.PerfilUsuario;
import java.time.LocalDateTime;
import java.util.Objects;

// abstract impede new Usuario(...); os perfis concretos herdam estes dados.
public abstract class Usuario {
    // private impede alteracao direta por outras classes, inclusive subclasses.
    // Long aceita null no modelo local; na reconstituicao, recebe o ID do registro SQL.
    private Long id;
    private String nome;
    // Definido no construtor; nao existe operacao de troca nesta versao.
    private final String email;
    // null representa telefone nao informado.
    private String telefone;
    // Guarda somente o hash recebido do servico, nunca a senha original.
    private final String senhaHash;
    // Campos persistentes comuns. Os construtores antigos continuam sendo apenas
    // modelos locais; nao inventamos documento ou atividade para esses objetos.
    private String documentoFiscal;
    private LocalDateTime dataCadastro;
    private Boolean ativo;

    /** Cada subtipo informa seu perfil; nao aceita troca arbitraria por setter. */
    public abstract PerfilUsuario getPerfil();

    /**
     * Reconstitui dados consultados sem senha. Perfil esperado e constante da
     * subclasse, evitando transformar um registro CLIENTE em objeto LOJISTA.
     * Nao chama metodos sobrescritos durante a construcao.
     */
    protected Usuario(UsuarioRegistro registro, PerfilUsuario perfilEsperado) {
        Objects.requireNonNull(registro, "Registro obrigatorio.");
        if (registro.id() < 1 || registro.id() > Integer.MAX_VALUE)
            throw new IllegalArgumentException("ID fora da faixa INT positiva.");
        if (registro.perfil() != perfilEsperado)
            throw new IllegalArgumentException("Registro nao corresponde ao perfil solicitado.");
        validarNome(registro.nome());
        validarEmail(registro.email());
        if (registro.documentoFiscal() == null || registro.documentoFiscal().isBlank()
                || registro.documentoFiscal().strip().codePointCount(0, registro.documentoFiscal().strip().length()) > 18)
            throw new IllegalArgumentException("Documento fiscal obrigatorio com ate 18 caracteres.");
        this.id = registro.id();
        this.nome = registro.nome().strip();
        this.email = registro.email().strip();
        this.documentoFiscal = registro.documentoFiscal().strip();
        this.dataCadastro = registro.dataCadastro();
        this.ativo = registro.ativo();
        // Consulta publica nao carrega credenciais; telefone nao existe no SQL.
        this.senhaHash = null;
        this.telefone = null;
    }

    /** Documento do SQL; null identifica modelo antigo sem dados persistidos. */
    public String getDocumentoFiscal() { return documentoFiscal; }

    /** Preserva a data do banco, inclusive NULL em registros antigos. */
    public LocalDateTime getDataCadastro() { return dataCadastro; }

    /** Preserva NULL: ausencia nao significa que o usuario esta ativo. */
    public Boolean getAtivo() { return ativo; }

    /** Uso em regras: somente TRUE explicito significa cadastro ativo. */
    public boolean isAtivo() { return Boolean.TRUE.equals(ativo); }

    /** Projecao sem credenciais; usa o perfil sobrescrito pelo subtipo real. */
    public UsuarioRegistro toRegistro() {
        if (id == null || documentoFiscal == null)
            throw new IllegalStateException("Modelo local ainda nao possui identidade persistida.");
        return new UsuarioRegistro(id, nome, documentoFiscal, email, getPerfil(), dataCadastro, ativo);
    }

    // protected permite que subclasses chamem super(...) e acesso no pacote.
    // CadastroService usa SenhaService para derivar a senha; este construtor recebe apenas o hash.
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

    // Credencial somente do construtor antigo; null em objetos reconstituidos.
    // Nao usar esta consulta publica para autenticar nem expor hash em telas/logs.
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
        if (nome.strip().codePointCount(0, nome.strip().length()) > 120)
            throw new IllegalArgumentException("Nome excede 120 caracteres.");
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
        if (emailTratado.codePointCount(0, emailTratado.length()) > 100)
            throw new IllegalArgumentException("Email excede 100 caracteres.");
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
