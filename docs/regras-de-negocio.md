# Compras, pedidos e entregas

## Cancelamento da compra — 07/11/2026

- O cliente pode cancelar a própria compra inteira enquanto todas as comandas ainda estiverem em `PAGO`. Quando qualquer loja registrar `PREPARANDO`, encerra-se o prazo para toda a compra.
- Na compra paga, o estorno simulado cobre produtos e taxa. A mesma transação repõe o estoque, cancela as comandas e ordens e libera entregadores vinculados que ficarem sem outras ordens ativas.
- Uma compra com pagamento recusado pode ser cancelada sem estorno ou reposição. Após cancelar, o cliente não pode tentar pagar novamente a mesma compra.

## Decisoes para EntregaService — 06/11/2026

Estas decisoes atualizam os pontos correspondentes das secoes anteriores;
registram regras aprovadas, ainda sem indicar implementacao concluida.

- Criar uma ordem por comanda assim que o pagamento for aprovado. Aceite apenas
  quando a loja informar que os produtos estao prontos para coleta.
- Distribuir a taxa da compra proporcionalmente ao valor dos produtos de cada
  comanda. Centavos residuais seguem maiores restos, com desempate por ID da
  comanda; soma das parcelas deve ser exatamente igual a taxa cobrada.
- Usar inicialmente 10% da parcela de frete de cada entrega para a plataforma,
  como parametro configuravel de teste, sujeito a mudanca. O restante vai ao
  entregador. Essa parcela e separada da comissao comercial do lojista.
- Permitir aceite dentro da distancia de aproximacao calculada pela malha,
  do entregador ate a primeira loja, e com veiculo compativel com a carga.
  Bairro nao comprova distancia. Origem dos dados da malha ainda pendente.
- Permitir varias comandas simultaneas na mesma rota; isso substitui a regra
  anterior de uma entrega ativa por entregador. Inicialmente agrupar somente
  comandas da mesma compra e cliente, preservando cada comanda inteira.
- Limites iniciais configuraveis aprovados:

| Veiculo | Aproximacao maxima | Desvio adicional com comida pronta | Desvio adicional com nao pereciveis |
| --- | --- | --- | --- |
| Bicicleta | 3,5 km | 1 km e 5 minutos | 3 km e 15 minutos |
| Moto | 7 km | 2 km e 7 minutos | 6 km e 20 minutos |
| Carro | 7 km | Nao permitido | 8 km e 25 minutos |
| Van | 15 km | Nao permitido | 12 km e 30 minutos |

- Alimentos somente em bicicleta ou moto; carro e van atendem outros produtos.
- Desvio e o acrescimo sobre a rota direta de cada comanda, incluindo espera
  e coleta adicional. Respeitar simultaneamente distancia e tempo adicionais.
- Comida pronta: inicialmente ate duas lojas, com produtos prontos para coleta,
  e meta operacional de ate 30 minutos entre coleta e entrega por comanda.
  Prazo ajustavel por produto; nao representa certificacao de conservacao.
- Outros pereciveis seguem prazo e condicoes de transporte do produto. Carga
  mista segue a condicao mais restritiva e exige compatibilidade de transporte.
- Somar carga a bordo em cada trecho. Se nao couber ou exceder os limites de
  percurso/prazo, distribuir comandas entre entregadores distintos.
- Capacidade inicial de teste: bicicleta e moto, 20 kg; carro, referencia de
  450 kg, configuravel por veiculo. Os 50 L citados foram referencia de tamanho,
  nao conversao de peso: verificacao de volume depende de dados proprios.
  Van: usuario mencionou 3.500 kg; carga util ainda precisa ser identificada.
  No modelo existente, 3.500 kg e limiar de PBT para categoria da CNH, nao
  capacidade de mercadoria. Malha e volume dos produtos ainda nao implementados.
- O entregador vinculado registra coleta e conclusao. Concluir a entrega conclui
  a comanda; concluir todas as comandas conclui a compra.
- A conclusao da ultima comanda ativa da rota libera automaticamente o
  entregador; concluir apenas uma das comandas nao libera o veiculo ocupado.
- Permitir desistir da atribuicao antes da coleta, devolvendo a ordem para
  outro entregador. Depois da coleta, o entregador nao pode desistir/cancelar.
  Desistir da atribuicao nao cancela a compra nem devolve estoque ou pagamento.

Implementacao de 06/11: consultar explicacoes/31-entrega-service.md para metodos,
construcao do checkout completo, sequencia inicial de coleta por ID, dados ainda
ausentes do cadastro e evidencias JDBC. As regras acima nao implicam menu pronto.

## Estoque, comissao e taxa da compra — 03/11/2026

- Conferir disponibilidade e baixar estoque somente na confirmacao do pagamento,
  na mesma transacao das demais alteracoes da compra. Cada nova tentativa deve
  conferir novamente o estoque; uma recusa nao efetiva baixa de estoque.
- Calcular a comissao com a porcentagem cadastrada do lojista sobre o total dos
  produtos da respectiva comanda. Arredondar a comissao final para duas casas
  decimais com RoundingMode.HALF_UP; repasse = produtos - comissao.
- Cobrar do cliente uma taxa percentual unica sobre a soma dos produtos de toda
  a compra, sem incluir a propria taxa na base. O percentual sera configuravel
  e seu valor ainda sera definido; nao assumir percentual zero ou outro default.
- Arredondar a taxa final da compra para duas casas com RoundingMode.HALF_UP.
  Total do cliente = total dos produtos + taxa da compra.
- A taxa do cliente e separada da comissao descontada do lojista. A distribuicao
  da taxa entre entregas/entregadores ainda nao foi definida.

O modelo persistente atual chama a diferenca entre valor_total e valor_produtos
de valorFretes(). A integracao deve documentar essa correspondencia com a taxa
da compra, sem somar um segundo frete nem inventar colunas no SQL.
Estas regras orientam os proximos services; nao representam implementacao pronta.

## Pagamento recusado e nova tentativa — 03/11/2026

Regra aprovada: manter o pedido em CRIADO quando o pagamento for RECUSADO,
permitindo nova tentativa no mesmo pedido. O PagamentoDao atual reutiliza a
mesma linha de pagamento, passando de RECUSADO para PENDENTE com novo codigo
de tentativa. A resposta deve corresponder ao codigo da tentativa atual.
Pedido cancelado ou fora de CRIADO nao pode iniciar nova tentativa por esse fluxo.

O schema utilizado nas validacoes JDBC registradas permite esse comportamento,
mas nao guarda o historico completo de tentativas: a linha anterior e atualizada.
Nao ha DDL oficial versionado em database; esta decisao nao comprova o estado
de outra instalacao do banco.

O service ainda deve coordenar persistencia do pedido recusado, confirmacao do
pagamento, estoque, comandas e itens. Manter o pedido nao implica reservar estoque;
a politica de reserva e os dados necessarios para reconstruir o carrinho ainda
precisam ser definidos na implementacao. A regra esta aprovada, nao implementada
integralmente pelos services.

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
