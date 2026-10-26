# Compras, pedidos e entregas

Regras acordadas em 18/10/2026:

- Uma Compra pode reunir produtos de varias lojas, gerando um Pedido por loja.
- Cada Pedido tera sua propria OrdemEntrega, com andamento independente.
- As entregas nao precisam ter entregadores diferentes. A atribuicao devera respeitar disponibilidade e regras do servico; nao ha implementacao de atribuicao nesta etapa.
- Pedido nasce com todos os itens e nao permite alterar a lista depois.
- A mesma oferta nao pode aparecer em duas linhas do mesmo pedido. Varias unidades ficam na quantidade de um ItemPedido; o carrinho devera agrupa-las.
- A mesma oferta pode aparecer em pedidos diferentes, sujeita ao estoque na confirmacao.
- Todo o estoque pode ser vendido. Nao existe mais reserva minima de tres unidades.
- Pedido segue CONFIRMADO -> EM_PREPARACAO -> EM_ROTA -> ENTREGUE. Cancelamento fica para depois.
- ItemPedido guarda nome, descricao e preco historicos. Subtotal nao inclui frete ou comissao.

Implementado: validacoes locais de Pedido, ItemPedido e disponibilidade total em OfertaProduto.
Compra e OrdemEntrega implementadas em memoria. Pendente: autorizacao, pagamento, exclusividade de entrega, baixa transacional de estoque, concorrencia e banco. Uma entrega ativa por entregador e a regra aprovada, ainda dependente do servico.
Pedido ainda nao tem reconstituicao com ID, data e estado salvos; isso deve ser adicionado com o DAO, sem recriar pedidos antigos como CONFIRMADO.