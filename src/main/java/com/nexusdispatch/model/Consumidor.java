package com.nexusdispatch.model;

/** Usuario que realiza compras, cada uma podendo reunir pedidos de varias lojas. */
public class Consumidor extends Usuario {
    // private impede substituir o atributo diretamente fora desta classe.
    // Nao e final: o consumidor pode trocar a referencia por outro endereco.
    private Endereco endereco;

    public Consumidor(String nome, String email, String telefone, String senhaHash,
            Endereco endereco) {
        // super chama o construtor de Usuario e suas validacoes.
        // A senha ja deve chegar como hash produzido pelo futuro servico.
        super(nome, email, telefone, senhaHash);
        validarEndereco(endereco);
        // Guarda a referencia ao objeto recebido, nao uma copia dos seus dados.
        this.endereco = endereco;
    }

    public Endereco getEndereco() {
        // O objeto retornado e mutavel: seus setters continuam disponiveis.
        return endereco;
    }

    public void setEndereco(Endereco endereco) {
        // Validar antes de atribuir preserva o endereco anterior em caso de erro.
        validarEndereco(endereco);
        this.endereco = endereco;
    }

    // private restringe este auxiliar a classe; static usa somente o parametro.
    // void nao retorna valor: termina normalmente ou lanca uma excecao.
    private static void validarEndereco(Endereco endereco) {
        if (endereco == null) {
            throw new IllegalArgumentException("O endereço do consumidor é obrigatório.");
        }
        // Rua, numero e CEP sao validados em Endereco, sem duplicar regras aqui.
        // Bairro cadastrado/atendido e permissao para editar dependem do servico.
    }

    // Compras confirmadas deverao guardar uma copia independente dos dados do
    // endereco. Reutilizar esta referencia nao congela o historico do pedido.
}
