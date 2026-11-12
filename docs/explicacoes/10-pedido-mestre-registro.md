# Pedido mestre: registro e estados

Implementacao local em 27/10/2026, branch Java_files.

Arquivos incluidos:
- model/PedidoMestreRegistro.java
- enums/StatusPedidoMestre.java

Codigo identico a proposta apresentada e reproduzida nos PDFs de ensino
09-status-pedido-mestre.pdf e 10-pedido-mestre-registro.pdf nesta pasta.
O enum antigo StatusPedido permanece separado por representar outros estados.

## Comportamento

ID Long nulo antes da insercao, cliente com ID positivo na faixa INT,
valores BigDecimal nao negativos dentro de DECIMAL(10,2), duas casas sem
arredondamento silencioso e total maior ou igual ao valor dos produtos.
novoPedido soma produtos e fretes e inicia CRIADO. valorFretes calcula a diferenca.
Data pode ser nula conforme o SQL; o futuro DAO devera omitir a coluna no INSERT
para aplicar CURRENT_TIMESTAMP e definir uma politica de fuso na leitura.

## Verificacao desta entrega

28 checks em memoria passaram. Compilacao de todos os fontes principais com
Java 25, UTF-8 e -Xlint:all, checks anteriores de entidades, higiene local e
verificacao do diff passaram. Executar ./tests/local/RunPedidoMestreCheck.ps1.
Testes pessoais continuam em tests/local, ignorados pelo Git conforme combinado.
Nenhuma consulta de escrita no banco foi necessaria. Persistencia do pedido
mestre ainda nao implementada. Nenhum commit ou push nesta etapa.

## Estado e sequencia de desenvolvimento

Backend parcial: conexao JDBC, DAOs de usuario/lojista/produto/entregador e
aceite transacional de ordens existem. O aceite foi testado anteriormente com
duas conexoes. A criacao de compras completas, coleta, conclusao e cancelamento
integrados ainda faltam. Os modelos anteriores em memoria nao usam automaticamente
os services JDBC; os diagnosticos PENDENTE desses checks continuam pertinentes.

1. PedidoMestreDao: inserir, buscar e listar por cliente, recuperar ID e data.
   Criacao somente apos confirmacao; controlada pela futura transacao de compra.
2. ComandaLojistaDao e ItemPedidoDao: separar por loja e congelar preco do item.
3. PagamentoDao: simular pagamento, apos alinhar meios Java/SQL e comportamento
   de cancelamento/reversao. Os meios finais ainda nao foram confirmados.
4. CompraService: criar pedido/comandas/itens/pagamento/ordens, baixar estoque e
   garantir rollback integral. Ampliar OrdemEntregaDao para criar ordens.
5. Integrar coleta/conclusao e cancelamento integral, coordenando entregador,
   ordem, comanda, pedido, estoque e pagamento. O limite temporal de cancelamento
   ainda precisa de confirmacao; nao tratar proposta anterior como regra aprovada.
6. Completar services de cadastro, autorizacao, dados de veiculo/CNH e territorio;
   conectar interface HTML/CSS ao backend e verificar o fluxo completo.

Faltam quatro DAOs novos no recorte principal: PedidoMestreDao,
ComandaLojistaDao, ItemPedidoDao e PagamentoDao. OrdemEntregaDao requer ampliacao.
Esta contagem nao e uma estimativa do total de arquivos nem percentual de conclusao.
