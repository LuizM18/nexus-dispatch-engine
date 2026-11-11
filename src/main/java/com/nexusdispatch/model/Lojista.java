package com.nexusdispatch.model;

import com.nexusdispatch.enums.PerfilUsuario;
import java.util.Objects;
import java.util.Optional;

/** Perfil LOJISTA: dados comuns de usuario e complemento da tabela lojista. */
public class Lojista extends Usuario {
    private LojistaRegistro dadosLojista;

    /**
     * Une os resultados dos dois DAOs sem perder identidade. O chamador deve
     * consultar ambos na mesma transacao quando precisar de leitura consistente.
     * Documento fiscal pertence a usuario; nao exigimos uma tabela loja.
     */
    public Lojista(UsuarioRegistro usuario, LojistaRegistro complemento) {
        super(usuario, PerfilUsuario.LOJISTA);
        Objects.requireNonNull(complemento, "Complemento do lojista obrigatorio.");
        if (usuario.id() != complemento.id())
            throw new IllegalArgumentException("Usuario e lojista devem ter o mesmo ID.");
        this.dadosLojista = complemento;
    }

    /** Disponibiliza o complemento imutavel; vazio no construtor local antigo. */
    public Optional<LojistaRegistro> getDadosLojista() { return Optional.ofNullable(dadosLojista); }

    /** O subtipo define o perfil usado pelas operacoes comuns de Usuario. */
    @Override
    public PerfilUsuario getPerfil() { return PerfilUsuario.LOJISTA; }

    /** Compatibilidade com modelos locais; nao representa cadastro completo no SQL. */
    public Lojista(String nome, String email, String telefone, String senhaHash) {
        // Os dados comuns sao inicializados e validados na superclasse.
        super(nome, email, telefone, senhaHash);
    }
}
