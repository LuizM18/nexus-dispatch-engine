package com.nexusdispatch.service;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * PBKDF2 do JDK, nao algoritmo criptografico proprio. Formato P2 versionado
 * cabe no CHAR(60) existente: prefixo 10 + salt 22 + separador 1 + derivado 27.
 * Nao e bcrypt e nao deve ser verificado como bcrypt por outro componente.
 */
public final class SenhaService {
    public static final int MIN_CARACTERES = 10;
    public static final int MAX_CARACTERES = 128;

    // P2 identifica o formato e 600000 registra o numero de iteracoes aceito.
    // P2$ tem 3 caracteres; 600000 tem 6; o ultimo $ tem 1: prefixo de 10.
    // Com salt, separador e derivado, o texto ocupa os 60 caracteres exigidos pelo DAO.
    private static final String PREFIXO = "P2$600000$";
    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    /** Valida o comprimento em unidades UTF-16, preservando espacos e Unicode. */
    public String gerar(char[] senha) {
        if (senha == null || senha.length < MIN_CARACTERES || senha.length > MAX_CARACTERES)
            throw new IllegalArgumentException("Senha deve conter de " + MIN_CARACTERES + " a " + MAX_CARACTERES + " caracteres.");
        boolean preenchida = false;
        for (char caractere : senha) if (!Character.isWhitespace(caractere)) preenchida = true;
        if (!preenchida) throw new IllegalArgumentException("Senha nao pode conter apenas espacos.");
        // Salt e um valor aleatorio por senha: duas senhas iguais devem gerar textos
        // diferentes. Ele e armazenado com o derivado e nao precisa permanecer secreto.
        byte[] salt = new byte[16];
        ALEATORIO.nextBytes(salt);
        byte[] derivado = derivar(senha, salt);
        // Base64 URL sem padding converte bytes em texto usando o alfabeto do formato.
        // finally apaga o derivado temporario mesmo se a montagem do texto falhar.
        // O char[] recebido pertence ao chamador, que continua responsavel por limpa-lo.
        try { return PREFIXO + ENCODER.encodeToString(salt) + "$" + ENCODER.encodeToString(derivado); }
        finally { Arrays.fill(derivado, (byte)0); }
    }

    /** Verifica somente P2 conhecido, com parametros limitados e comparacao de bytes. */
    public boolean verificar(char[] senha, String hash) {
        // So processa o formato conhecido. A contagem de iteracoes nao vem livremente
        // do texto recebido: a expressao exige o valor fixo suportado por esta versao.
        if (senha == null || senha.length > MAX_CARACTERES || hash == null
                || !hash.matches("P2\\$600000\\$[A-Za-z0-9_-]{22}\\$[A-Za-z0-9_-]{27}")) return false;
        String[] partes = hash.split("\\$");
        byte[] salt, esperado;
        try {
            salt = Base64.getUrlDecoder().decode(partes[2]);
            esperado = Base64.getUrlDecoder().decode(partes[3]);
        } catch (IllegalArgumentException erro) { return false; }
        // Decodificar nao basta: conferir tamanhos e recodificar exige a representacao
        // canonica sem variantes de Base64 para os mesmos bytes.
        if (salt.length != 16 || esperado.length != 20
                || !ENCODER.encodeToString(salt).equals(partes[2])
                || !ENCODER.encodeToString(esperado).equals(partes[3])) return false;
        byte[] atual = derivar(senha, salt);
        // Compara os bytes derivados com a rotina do JDK destinada a digests.
        // Nao usa equals de String para comparar o segredo; os buffers sao limpos depois.
        try { return MessageDigest.isEqual(esperado, atual); }
        finally { Arrays.fill(atual, (byte)0); Arrays.fill(esperado, (byte)0); }
    }

    /** 600 mil iteracoes SHA-256, salt de 128 bits e derivado de 160 bits. */
    private static byte[] derivar(char[] senha, byte[] salt) {
        // A especificacao recebe senha, salt, iteracoes e tamanho em BITS (160 = 20 bytes).
        // clearPassword limpa a copia interna da especificacao, nao o array do chamador.
        PBEKeySpec spec = new PBEKeySpec(senha, salt, 600_000, 160);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (GeneralSecurityException erro) { throw new IllegalStateException("PBKDF2 indisponivel neste JDK.", erro); }
        finally { spec.clearPassword(); }
    }
}
