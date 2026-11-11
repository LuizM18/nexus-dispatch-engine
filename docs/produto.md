# Produto por loja

Decisao do grupo em 12/10/2026: cada produto pertence a uma unica loja.
Substitui o catalogo generico compartilhado descrito no PDF de 30/09/2026.
Loja 1:N Produto; OfertaProduto referencia Produto e obtem a loja por getLoja().
Nao recebe mais uma loja independente no construtor. A politica de ofertas
duplicadas do mesmo produto ainda deve ser confirmada e aplicada no banco.

Produto exige loja, nome com pelo menos uma letra Unicode e limite de 120
pontos de codigo. Descricao opcional de ate 1000 pontos de codigo. Os limites
sao verificados depois de strip(); nao ha truncamento. Descricao vazia ou em
branco vira null. Contagem Unicode nao e contagem de grafemas visuais.
Getters e setters de nome/descricao; falhas preservam os valores anteriores.
Loja fixa; ID inicialmente nulo, com persistencia ainda pendente.

Somente o lojista responsavel devera editar, mas essa permissao NAO esta
implementada na entidade. O servico precisara consultar o usuario autenticado.
O endereco e demais dados da loja continuam mutaveis pelo proprio objeto Loja.

ItemPedido preserva nome, descricao e preco do momento de sua criacao; deve ser criado na confirmacao da compra.
Essa classe ainda esta vazia: o historico de compras ainda nao foi implementado.

Testes locais: ProdutoCheck cobre entradas invalidas, fronteiras 120/121 e
1000/1001, caracteres suplementares Unicode, normalizacao, setters e isolamento
de produtos de lojas diferentes. OfertaProdutoCheck usa o novo construtor.
