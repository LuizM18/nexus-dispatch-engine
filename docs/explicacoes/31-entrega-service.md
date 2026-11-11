# EntregaService: rota agrupada, coleta, conclusao e desistência

06/11/2026. Implementacao JDBC e regras logisticas aprovadas no chat.

## O que foi entregue

EntregaService coordena aceite unitario ou em lote, preparo da comanda, coleta,
conclusao e devolucao da atribuicao antes da coleta. MalhaEntrega calcula caminhos
em um grafo dirigido por veiculo. Dois DAOs receberam comandos de bloqueio,
consulta de manifesto, desvinculo e liberacao condicional. CompraService recebeu
um construtor com EntregaService para criar ordens junto ao checkout autorizado.
Nao houve mudanca de schema, commit ou push. O menu nao foi alterado nesta etapa.

## Construir e conectar

O construtor completo recebe fabrica JDBC, DadosLogisticos, percentual da
plataforma e limites por veiculo. DadosLogisticos representa uma fonte interna
confiavel: pontos das lojas/clientes, posicao do entregador, malha, classificacao
de produtos, prazos, capacidade util e duracao esperada de coleta. Nao aceitar
esses dados como decisao de elegibilidade enviada por um cliente.

O banco atual nao tem esses cadastros completos. Nao foi inventada uma localizacao
real, nem foi criada persistencia paralela. O teste fornece uma malha EXPLICITA de
demonstracao. O cadastro real e sua ligacao ao terminal continuam pendentes.
Sem DadosLogisticos, criar/aceitar ordens falha com mensagem explicativa; buscar,
coletar, concluir e desistir de atribuicoes persistidas continuam disponiveis.

No checkout completo usar:

```java
var entregas = new EntregaService(fabrica::abrir, dados, new BigDecimal("10"),
        EntregaService.limitesIniciais());
var compras = new CompraService(fabrica, percentualTaxaCompra, simulador, entregas);
```

Os construtores anteriores de CompraService preservam checkout isolado, SEM
criacao de ordens, por compatibilidade. Para o roteiro completo e obrigatorio
usar o novo construtor. A taxa da compra e diferente dos 10% da plataforma:
o percentual da taxa cobrada do cliente ainda precisa ser configurado.

## Operacoes publicas

- buscar(id): snapshot da ordem; nao reserva trabalho.
- aceitarEntrega(entregador, entrega): lote com uma ordem.
- aceitarRota(entregador, ids): valida todas as ordens e reserva todas ou nenhuma.
- iniciarPreparo(lojista, comanda): PAGO para PREPARANDO, confere propriedade.
- disponibilizarColeta(lojista, comanda): PREPARANDO para AGUARDANDO_COLETA.
- registrarColeta(entregador, entrega): somente o vinculado; muda ordem e comanda
  para EM_TRANSITO, com data da coleta gerada pelo banco.
- concluirEntrega(entregador, entrega): ordem ENTREGUE e comanda CONCLUIDO.
  Se todas as comandas estiverem concluidas, a compra vira CONCLUIDO.
- desistirAntesDaColeta(entregador, entrega): remove somente a atribuicao; mantem
  a ordem AGUARDANDO_COLETA, os valores, a comanda, o pagamento e o estoque.

false representa ausencia, estado divergente, disputa ou ator diferente.
Regra logistica invalida gera excecao explicativa. Erro JDBC e propagado.
Repetir uma coleta/conclusao nao grava outra data: retorna false.

## Rota e limites

Uma rota e um lote de ate 100 ordens da mesma compra e, consequentemente, do mesmo
cliente. O entregador precisa estar DISPONIVEL e sem ordens ativas no aceite.
A partir do aceite, fica EM_ROTA. Isso permite diversas ordens no mesmo lote,
substituindo a regra antiga de uma ordem ativa por entregador.

Nesta primeira implementacao a sequencia de coleta e por ID crescente da ordem.
A malha calcula o caminho mais rapido entre paradas, com distancia como desempate,
usando Dijkstra. Nao busca a permutacao global otima das lojas. Portanto pode
recusar um lote que seria viavel em outra ordem de coleta. E uma limitacao
explicita da primeira versao; otimizacao completa requer persistir o itinerario.

O service exige coleta nessa sequencia e todas as coletas antes de entregar ao
cliente. Nao acrescenta ordens a uma rota em andamento. O chamador pode escolher
um subconjunto de comandas e atribuir as restantes a outro entregador.

| Veiculo | Aproximacao | Desvio comida | Desvio outros |
| --- | --- | --- | --- |
| Bicicleta | 3,5 km | 1 km / 5 min | 3 km / 15 min |
| Moto | 7 km | 2 km / 7 min | 6 km / 20 min |
| Carro | 7 km | Proibido | 8 km / 25 min |
| Van | 15 km | Proibido | 12 km / 30 min |

Aproximacao e entregador ate primeira loja; nao e o comprimento maximo da entrega.
O desvio compara, para CADA comanda, rota agrupada com caminho direto loja-cliente.
Inclui espera/coleta nas lojas seguintes. Tanto distancia quanto tempo devem caber.
O menor prazo dos produtos e respeitado no planejamento; comida pronta limita
a duas lojas e a 30 minutos previstos desde a coleta. Carga mista com comida
aplica os limites de comida a toda a rota. Alimentos so podem ir em moto/bike.

Os limites medem planejamento com os tempos fornecidos pela malha, sem transito
em tempo real. Atrasos reais nao impedem registrar uma entrega que ja ocorreu.
Nao existe monitoramento de temperatura, acondicionamento ou alerta de atraso.
Classificacao/prazos devem ser cadastrados; categoria textual nao e usada para
adivinhar conservacao. Compatibilidade fisica/quimica entre cargas nao e modelada.

## Peso, volume e van

O peso e a soma de pesoKg do produto JDBC multiplicado pela quantidade do item.
Como todas as coletas precedem o destino unico, o pico e a carga total do lote.
Peso ausente/zero ou capacidade util ausente recusam o aceite.

Bicicleta/moto tem teto inicial de 20 kg; carro, 450 kg. A capacidade configurada
por veiculo pode ser menor. Van exige capacidade util explicita da fonte interna,
sem default de 3.500 kg. PBT nao e capacidade de mercadorias. As regras existentes
de CNH/PBT/passageiros em Entregador nao foram alteradas. O snapshot JDBC nao
comprova esses dados: a fonte interna deve fornecer capacidade validada.

50 litros nao sao conversao de 20 kg. Volume nao e verificado porque nao existe
no cadastro atual. Peso/classificacao sao consultados no aceite e nao possuem
snapshot logistico historico na ordem; alteracoes posteriores no cadastro nao
reavaliam automaticamente uma rota ja aceita. Essa persistencia e evolucao futura.

## Dinheiro e checkout

criarOrdens e um hook interno, chamado com a MESMA Connection de CompraService
apos pagamento autorizado, baixa e criacao de comandas/itens. Cada comanda recebe
uma ordem enquanto PAGO. Nao faz commit separado. Falha de malha/metadados/SQL
reverte pagamento, estoque, itens, comandas e ordens juntos. Pagamento recusado
nao cria ordens. Nova tentativa autorizada tambem passa pelo hook.

Rateio proporcional ao valor dos produtos de cada comanda, usando maiores restos
para conservar centavos. Primeiro distribui partes inteiras de centavo; depois
entrega os centavos restantes aos maiores restos, desempate pelo ID da comanda.
Exemplo: taxa total R$ 0,01, produtos R$ 1 e R$ 2: parcelas R$ 0 e R$ 0,01.

Plataforma recebe inicialmente 10% de CADA parcela, arredondado HALF_UP; entregador
recebe a diferenca exata. O percentual e configuravel no construtor. Nao desconta
novamente a comissao comercial do lojista. Valores sao snapshots da criacao:
alterar percentual depois nao recalcula ordens antigas.

A distancia historica loja-cliente usa MOTO como referencia para alimento e CARRO
para outros produtos, arredondada a duas casas do schema. O aceite calcula a rota
com o veiculo efetivamente selecionado. Sem caminho nessa referencia, o checkout
completo falha. O schema exige distancia positiva; pontos coincidentes ainda
precisam de tratamento de modelagem, sem gravar distancia ficticia.

## Transacoes e concorrencia

TransacaoJdbc exige conexao nova com autoCommit=true, muda para READ_COMMITTED,
executa commit em sucesso e rollback em excecao. Falha de rollback e suprimida
na original. A conexao e fechada inclusive em erro. Nao existem retries cegos
apos falha de commit: consultar estado antes de repetir escrita.

Aceite bloqueia entregador, usuario, pedido e ordens em ordem crescente.
Operacoes de coleta/conclusao/desistencia bloqueiam entregador e pedido antes
do manifesto ativo. Pedido serializa conclusoes simultaneas feitas por dois
entregadores da mesma compra, evitando deixar a compra em PAGO indevidamente.
UPDATEs condicionais continuam protegendo cada transicao.

Liberacao usa NOT EXISTS de ordens ativas. Entregador so fica DISPONIVEL depois
da ultima ordem; usuario desativado/inconsistente passa a INATIVO. Concluir uma
parte nunca libera um veiculo com outra comanda ativa. Desistir depois da coleta
e recusado; cancelamento integral/estorno continua fora deste arquivo.

## Verificacao reproduzivel

Configure o MySQL conforme [Testes com Maven](../../README.md#testes-com-maven).
Execute `mvn -Pintegracao '-Dit.test=SistemaIT' verify` para rodar os cenários de serviços e terminal junto com os testes de domínio.
Os cenários JDBC usam catálogos descartáveis com as tabelas e chaves estrangeiras do esquema de teste versionado.

As verificações de entrega incluem rateio residual,
10%, checkout/retry/recusa, malha dirigida, veiculo, falta de dados, capacidade,
raio/desvio/prazo, alimentos em carro recusados, coleta em ordem, ator errado,
desistencia, liberacao da ultima parte, rollback SQL induzido, duas conexoes
competindo por um lote e conclusoes concorrentes da mesma compra.

## Para estudar

1. Por que concluir a primeira comanda nao libera o entregador da rota inteira?
2. Qual a diferenca entre desistir da atribuicao e cancelar uma compra paga?
3. Por que bloquear o pedido evita perder a conclusao em duas entregas simultaneas?
4. Por que os 10% da plataforma nao sao a taxa cobrada do cliente?
5. Por que PBT e carga util da van precisam ser tratados separadamente?

PDF local: 31-EntregaService.pdf, com este guia e os fontes comentados.
