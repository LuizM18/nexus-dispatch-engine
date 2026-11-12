# CompraService: checkout e nova tentativa

Implementacao de 03/11/2026. Pagamento exclusivamente simulado.

## Finalidade e uso

CompraService coordena os DAOs na mesma Connection e transacao. O percentual
da taxa e obrigatorio no construtor; nao existe valor padrao. O simulador e
injetado pelo codigo do servidor, nunca escolhido pelo cliente HTTP.

Exemplo didatico: `new CompraService(new BigDecimal("5.00"), tentativa -> true)`.
O numero 5 e ilustrativo, nao uma configuracao aprovada para producao.
Cada ItemCarrinho informa produto, quantidade e observacao; precos e comissoes
vem do banco. O ID do cliente deve vir da camada autenticada, ainda futura.

## Metodos e tipos

- FabricaConexao.abrir: fornece conexao nova, dedicada, em autoCommit.
- SimuladorPagamento.autorizar: decisao local e rapida, sem rede ou cobranca real.
- ItemCarrinho: valida ID, quantidade positiva e observacao de ate 255 caracteres.
- Resultado: devolve pedido, pagamento e lista imutavel de comandas.
- comprar: inicia pedido CRIADO e pagamento; persiste recusa ou confirma compra.
- tentarNovamente: exige propriedade, pedido CRIADO sem comandas e codigo da
  recusa atual. Reutiliza a linha de pagamento com novo identificador de tentativa.
- calcularPercentual: base vezes percentual dividido por 100; HALF_UP ao final.
- executar: delimita transacao, valida cliente, coordena pagamento e persistencia,
  confirma commit ou executa rollback preservando eventual falha de rollback.
- carregarCarrinho: bloqueia produtos por ID crescente, confere atividade/saldo,
  bloqueia lojistas, carrega comissoes e calcula totais com BigDecimal.
- bloquear: SELECT FOR UPDATE com identificadores internos fixos e ID parametrizado.
- validarCarrinho: copia entradas, ordena e recusa repeticao de produto; quantidade
  repetida deve estar agrupada pelo carrinho. Limite operacional: 100 produtos.
- validarPercentual/validarId: conferem limites antes do acesso ao banco.
- Linha.subtotal: preco do banco multiplicado pela quantidade. Carrinho agrupa
  linhas, lojistas, subtotais e taxa durante a transacao, sem persistencia propria.

## Ordem das operacoes

1. Validar entrada; abrir conexao dedicada e usar READ_COMMITTED.
2. Na nova tentativa, bloquear pedido existente e verificar dono/estado.
3. Conferir perfil CLIENTE ativo; bloquear e consultar produtos/lojistas.
4. Calcular produtos, taxa da compra e total; validar limites DECIMAL(10,2).
5. Inserir pedido/pagamento ou preparar nova tentativa com codigo atual.
6. Simular pagamento e registrar resultado na mesma transacao.
7. Recusado: commit de pedido CRIADO/pagamento RECUSADO, sem itens/comandas/baixa.
8. Autorizado: baixar estoque, criar uma comanda por lojista e seus itens,
   marcar pedido PAGO e confirmar tudo em um unico commit.
9. Falha: rollback de todas as alteracoes da chamada. Em retry, a recusa anterior
   permanece se a confirmacao falhar. Nao repetir automaticamente a operacao.

## Valores

Taxa do cliente: HALF_UP sobre a soma dos produtos da compra inteira.
Comissao do lojista: HALF_UP sobre os produtos da respectiva comanda.
Repasse do lojista = subtotal - comissao; total do cliente = produtos + taxa.
Exemplo ilustrativo: R$ 24,50 a 5% resulta em R$ 1,23.
A taxa ocupa a diferenca ja representada por PedidoMestreRegistro.valorFretes();
nenhuma coluna foi criada e nao ha segundo frete adicionado.

## Limites explicitos

Nao ha tabela de carrinho/itens pendentes no schema disponivel. Nova tentativa
recebe novamente o carrinho e recalcula tudo; se produtos ou taxa total mudarem,
recusa a tentativa nesse pedido e exige nova compra. Composicao diferente com os
mesmos totais e aceita; nao garante identidade do carrinho recusado original.
Historico de tentativas nao e mantido, pois PagamentoDao atualiza a mesma linha.

Nao implementa login, autorizacao HTTP, elegibilidade territorial, loja aberta,
entregas/rotas, distribuicao da taxa, cancelamento integral, expiracao de pedido,
estorno ou integracao com provedor financeiro. Validar perfil nao autentica ator.
Nao existe chave de idempotencia para criar compra: repetir comprar cria outra;
tentarNovamente em pedido ja PAGO e recusado e nao duplica a baixa.
O simulador roda sob bloqueios: nao substituir por chamada de rede financeira.
Deadlock/falha de commit sao propagados; timeout apos commit pode ter resultado
incerto e exige consulta antes de qualquer repeticao de compra.

## Verificacao executada

Compilacao completa UTF-8 -Xlint:all -Werror aprovada. Regressao RunChecks e
CheckSourceHygiene aprovados. CompraServiceIntegrationCheck: 40 verificacoes
aprovadas em MySQL com DDL/FKs reais copiados para banco UUID descartavel,
removido no finally. Sem alteracao do schema ou dados da base original.

Cobre HALF_UP, duas lojas, recusa sem baixa, retry na mesma linha, dono errado,
codigo incorreto/antigo, repeticao apos pagamento, preco alterado, nova recusa,
rollback apos autorizacao/baixa e durante retry, falta de estoque e disputa de
duas conexoes pela ultima unidade. Nao comprova concorrencia de dois retries,
cancelamento concorrente, autorizacao HTTP nem seguranca completa.
Teste de apoio e PDF ficam locais, ignorados pelo Git. Nenhum commit/push.

## Para estudar

1. Por que salvar RECUSADO exige commit, em vez de rollback da compra inteira?
2. Como FOR UPDATE e o UPDATE condicional trabalham juntos para proteger estoque?
3. Por que uma API nao deve receber um booleano do cliente autorizando pagamento?

PDF complementar: 22-CompraService.pdf, com este guia e o codigo comentado.
