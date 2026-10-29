package com.nexusdispatch.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;

/**
 * Transporta os dados de uma linha da tabela produto entre o DAO e o chamador.
 * Nao executa SQL: sua responsabilidade e representar e validar os valores.
 *
 * record cria os campos privados e finais, o construtor e os acessores, como
 * nome() e precoUnitario(), alem de equals(), hashCode() e toString().
 * Nao possui setters: para representar uma alteracao, criamos outro registro.
 *
 * Long permite que id seja null antes do INSERT; o MySQL gera essa chave.
 * idLojista usa long porque o produto precisa estar associado a um lojista.
 * BigDecimal representa preco e peso sem as aproximacoes de ponto flutuante.
 * Boolean permite true, false ou null, conforme a coluna status_ativo do banco.
 */
public record ProdutoRegistro(Long id, long idLojista, String nome, String categoria,
        String descricao, BigDecimal precoUnitario, BigDecimal pesoKg,
        int estoqueDisponivel, Boolean ativo) {

    // Construtor compacto: executado quando usamos new ProdutoRegistro(...).
    // O Java armazena os parametros nos campos depois destas validacoes.
    // Se uma validacao lancar excecao, a construcao do objeto nao e concluida.
    public ProdutoRegistro {
        // Produto novo nao tem ID. Se o ID foi informado, ele precisa ser valido.
        if (id != null) validarId(id);
        // Aqui conferimos a faixa numerica; a existencia do lojista depende da FK.
        validarId(idLojista);
        // Centraliza a verificacao de campos obrigatorios e limites do VARCHAR.
        nome = texto(nome, "Nome", 120);
        categoria = texto(categoria, "Categoria", 50);
        // codePoints percorre Unicode; isLetter reconhece letras com acento.
        // noneMatch retorna true se nenhuma letra existir: "123" e rejeitado,
        // enquanto "Arroz 1 kg" e aceito. Esta e uma regra da aplicacao Java.
        if (nome.codePoints().noneMatch(Character::isLetter)) {
            throw new IllegalArgumentException("O nome deve conter uma letra.");
        }
        // Operador ternario: condicao ? valorSeVerdadeiro : valorSeFalso.
        // A descricao e opcional. null ou texto em branco representam ausencia;
        // caso contrario, strip remove apenas os espacos das extremidades.
        // O || evita chamar isBlank() quando descricao ja e null.
        descricao = descricao == null || descricao.isBlank() ? null : descricao.strip();
        // TEXT limita bytes, nao caracteres; utf8mb4 pode usar quatro bytes por caractere.
        if (descricao != null && descricao.getBytes(StandardCharsets.UTF_8).length > 65535) {
            throw new IllegalArgumentException("Descricao excede os 65535 bytes de TEXT.");
        }
        // DECIMAL(10,2) reserva dez digitos no total, sendo dois apos a virgula.
        // DECIMAL(6,3) reserva seis digitos no total, sendo tres apos a virgula.
        // Os limites abaixo seguem essas colunas do banco, sem inventar valores padrao.
        precoUnitario = decimal(precoUnitario, 2, "99999999.99", "Preco");
        pesoKg = decimal(pesoKg, 3, "999.999", "Peso");
        // Zero e valido: o produto pode existir mesmo quando esta sem estoque.
        // int ja limita o maior valor a Integer.MAX_VALUE.
        if (estoqueDisponivel < 0) throw new IllegalArgumentException("Estoque nao pode ser negativo.");
        // Boolean preserva NULL permitido pelo SQL; nao inventamos status ao consultar.
    }

    // private restringe o uso a esta classe. static indica que o metodo nao
    // depende de um objeto ja construido: trabalha apenas com seus parametros.
    private static void validarId(long id) {
        // Embora Java receba long, a coluna MySQL e INT assinado. Evitamos enviar
        // um numero que cabe em long, mas nao cabe na coluna do banco.
        if (id <= 0 || id > Integer.MAX_VALUE) throw new IllegalArgumentException("ID fora da faixa INT.");
    }
    // Retorna o texto normalizado ou interrompe a criacao com uma mensagem clara.
    // campo e usado na mensagem; limite varia conforme a coluna validada.
    private static String texto(String valor, String campo, int limite) {
        if (valor == null || valor.isBlank()) throw new IllegalArgumentException(campo + " e obrigatorio.");
        valor = valor.strip();
        // Conta pontos de codigo, nao unidades UTF-16 de String.length().
        // Isso evita contar um caractere suplementar como se fossem dois.
        if (valor.codePointCount(0, valor.length()) > limite) throw new IllegalArgumentException(campo + " muito longo.");
        return valor;
    }
    // Compartilhado por preco e peso. escala informa as casas decimais permitidas.
    // maximo chega como String para criar o BigDecimal sem passar por double.
    private static BigDecimal decimal(BigDecimal valor, int escala, String maximo, String campo) {
        // signum retorna -1, 0 ou 1 conforme o sinal. compareTo retorna positivo
        // quando valor e maior que maximo. Preco e peso devem ser maiores que zero.
        if (valor == null || valor.signum() <= 0 || valor.compareTo(new BigDecimal(maximo)) > 0) {
            throw new IllegalArgumentException(campo + " fora da faixa permitida.");
        }
        try {
            // setScale padroniza as casas: preco 10 vira 10.00; 10.250 vira 10.25.
            // UNNECESSARY exige que isso seja exato: preco 10.251 gera excecao,
            // pois seria necessario arredondar ou descartar um digito diferente de zero.
            return valor.setScale(escala, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            // Traduz a falha numerica para uma mensagem de argumento invalido,
            // preservando a excecao original como causa para diagnostico.
            throw new IllegalArgumentException(campo + " tem casas decimais excedentes.", e);
        }
    }
}
