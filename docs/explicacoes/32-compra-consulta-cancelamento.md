# CompraService — consultas, histórico e cancelamento

Nexus Dispatch · 07/11/2026

## Consultar compras

`listarHistorico(idCliente, limite, deslocamento)` devolve uma página das compras do cliente ativo, das mais recentes às mais antigas. O limite vai de 1 a 100. Com limite 10 e deslocamento 0, o terminal recebe a primeira página. Com deslocamento 10, recebe a seguinte.

`buscarCompra(idCliente, idPedido)` retorna o pedido, seu pagamento quando houver e todas as comandas com os itens e entregas. A consulta usa os preços aplicados no checkout. Uma compra de outro cliente ou um ID inexistente retorna `Optional.empty()`. O bloqueio breve do pedido evita que uma leitura misture estados anteriores e posteriores a um cancelamento ou a uma entrega.

## Até quando é possível cancelar

`cancelarCompra(idCliente, idPedido)` recebe o ID do dono da compra. Numa compra paga, todas as comandas precisam ainda estar em `PAGO`. Quando qualquer loja aciona `iniciarPreparo`, a compra inteira sai do prazo de cancelamento.

O método retorna `true` ao cancelar uma compra própria. Repetir a chamada depois do cancelamento retorna `true` e deixa os valores e o estoque como ficaram após a primeira operação. Compra ausente, alheia ou fora do prazo retorna `false`.

Uma compra com pagamento recusado pode ser cancelada sem alterar estoque ou pagamento. O estado `CANCELADO` impede novas tentativas para esse ID. Compras antigas ainda em `CRIADO` com pagamento `PENDENTE` ou indefinido precisam ter o pagamento resolvido antes do cancelamento.

## O que acontece numa compra paga

1. O pagamento vai para `ESTORNADO`. O valor registrado inclui produtos e taxa, e a referência e a data da autorização continuam disponíveis para consulta.
2. As quantidades registradas nos itens voltam para o estoque, inclusive se um produto tiver sido desativado desde o checkout.
3. As ordens e comandas são canceladas. Os registros continuam no histórico.
4. Se uma ordem antiga já tinha entregador vinculado, o service o libera quando ele fica sem ordens ativas.
5. O pedido fica `CANCELADO` e a transação confirma todas as mudanças juntas. Se qualquer etapa falhar, as anteriores são revertidas.

Exemplo. Uma compra de R$ 100 em produtos com R$ 7 de taxa registra estorno simulado de R$ 107. Se uma das lojas já começou a preparar a comanda, o cancelamento é recusado e os estados permanecem como estavam.

## Concorrência e migração

O início do preparo, a nova tentativa de pagamento e o cancelamento disputam o bloqueio da mesma linha do pedido. O cancelamento confere os estados novamente após obter o bloqueio. Para ordens antigas já atribuídas, o service bloqueia primeiro os entregadores em ordem de ID, seguindo a sequência do fluxo de entrega. Os produtos também são atualizados em ordem de ID, como no checkout.

A migração `database/001-pagamento-estornado.sql` acrescenta `ESTORNADO` ao tipo da coluna de pagamento. Ela foi aplicada e verificada no `nexus_db` local. Outros ambientes precisam executar a mesma migração para aceitar o estorno. O fluxo continua usando o simulador interno de pagamento.

## Como verificar

Execute `./scripts/RunEntregaServiceCheck.ps1`. A suíte prepara um banco descartável, aplica a migração e exercita consulta, isolamento entre clientes, estorno integral, reposição única, rollback após falha, liberação de entregador e disputa entre preparo e cancelamento. A execução com as novas verificações passou em 201 casos. Os testes de domínio e a checagem de higiene também passaram.

O próximo arquivo é `ConsoleNexus.java`, que vai chamar esses métodos no menu.
