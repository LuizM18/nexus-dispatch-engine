# Compra, Pedido, ItemPedido e OrdemEntrega: revisao e resumo geral

03/11/2026. Guia Java para estudo; sem alteracao do banco ou novo commit.

## Primeiro: o que estamos corrigindo?

No inicio, varias classes funcionavam com objetos na memoria do Java. Memoria
significa os dados que existem durante a execucao. O banco tinha outra estrutura:
nomes de campos, estados e relacionamentos nem sempre eram iguais aos das classes.

Isso nao tornava todos os testes anteriores inuteis: eles verificam regras dos
objetos. Mas passar nesses testes nao provava que tudo podia ser salvo e depois
recuperado corretamente do MySQL. O escopo exige essa ligacao real via JDBC.

JDBC e o mecanismo usado pelo Java para conversar com o banco. DAO e a classe
que concentra comandos SQL. Registro e a ficha com dados de uma linha consultada.
Service coordena a operacao completa quando varias fichas precisam mudar juntas.

## Exemplo: uma compra com duas lojas

Imagine comprar arroz da loja A e pao da loja B. Existe uma compra geral do
cliente. Dentro dela, cada loja recebe sua parte, chamada comanda no banco.
Cada comanda tem seus itens e sua entrega independente.

- Compra Java corresponde a pedido_mestre no banco.
- Pedido Java, no sentido antigo de pedido por loja, corresponde a comanda_lojista.
- ItemPedido corresponde a item_pedido e pertence a uma comanda.
- OrdemEntrega corresponde a ordem_entrega e tambem pertence a uma comanda.

Nao criamos tabelas adicionais so para repetir os nomes das classes. Os nomes
antigos foram preservados, com a correspondencia explicita para evitar confusao.

## O que mudou nos quatro arquivos desta etapa

Compra(Consumidor, PedidoMestreRegistro, List<Pedido>) recupera uma compra salva.
Confere cliente/ID, pertencimento das comandas, duplicacao e total. A lista deve
estar completa; nao passar uma pagina como se fosse a compra inteira. Compra
PAGO/CONCLUIDO sem comandas e recusada; CRIADO/CANCELADO sem comandas pode ser
recuperado, pois o carrinho recusado nao fica salvo em item_pedido.

Compra.toRegistro devolve a ficha salva. getStatusPedidoMestre consulta o estado
geral; getValorTotal e getValorTaxaCompra recuperam valores historicos sem aplicar
a porcentagem atual novamente. getSubtotal usa o valor salvo dos produtos,
mesmo quando uma compra recusada nao tem itens persistidos. getId/getConsumidor/
getPedidos continuam mostrando identidade e associacoes; a lista nao e editavel.
getDataHoraCriacao preserva a data do banco. getEnderecoEntrega fica null na
leitura SQL, pois o banco atual nao armazena esse destino historico.
O construtor antigo continua criando apenas o modelo local anterior.

Pedido(Loja, ComandaLojistaRegistro, List<ItemPedido>) recupera a comanda de uma
loja. Exige itens completos, sem produto/ID repetido, com a FK correta e subtotal
igual ao salvo. toRegistro devolve a comanda; getIdPedidoMestre identifica a
compra geral. getTaxaVendaRetida/getRepasseLojista preservam os valores daquela
venda, sem recalcular com a comissao de hoje. getSubtotal soma os precos
historicos dos itens, e nao os precos atuais dos produtos.

Pedido.getStatusComanda usa os estados reais do SQL, incluindo espera de coleta
e cancelamento. getDataHoraStatus preserva a data do estado; getDataHoraCriacao
fica null no snapshot, porque a tabela nao possui uma data de criacao separada.
Nao apresentar a data de uma atualizacao como data de nascimento do pedido.
getStatus antigo e iniciarPreparacao/iniciarEntrega/concluirEntrega recusam
snapshots JDBC: mudanca de estado deve passar pelo service/DAO e nova leitura.
No modelo antigo local, esses metodos continuam funcionando como antes.
A comparacao de ofertas agora reconhece tambem o mesmo produto, evitando linhas
duplicadas que usavam visoes diferentes de uma oferta sem ID proprio.

ItemPedido(OfertaProduto, ItemPedidoRegistro) recupera quantidade, preco aplicado,
observacao, ID e FK da comanda. Confere se o produto e o correto; nao exige
estoque atual para ler venda antiga. toRegistro preserva a ficha original;
getIdComanda/getObservacao mostram os novos dados recuperados.

O banco nao guarda nome/descricao historicos do item. Por isso getNomeProduto e
getDescricaoProduto retornam null no snapshot. getNomeProdutoAtual e
getDescricaoProdutoAtual mostram o cadastro consultado agora, com outro nome
de metodo para deixar essa diferenca clara. possuiTextosHistoricos informa
se o objeto possui o historico local antigo. getPrecoUnitario/getSubtotal
continuam usando o preco aplicado na venda. O construtor local e a fabrica antiga
reconstituir recusam ofertas JDBC para nao introduzir textos historicos inventados.

OrdemEntrega(Pedido, OrdemEntregaRegistro, Entregador) recupera a ordem da comanda
correta. Entregador informado deve corresponder ao ID salvo; se nao ha vinculo,
o parametro deve ser null. toRegistro e getStatusEntrega preservam o estado SQL,
inclusive DEVOLVIDO/CANCELADO. isEmAndamento reconhece EM_TRANSITO ou espera
de coleta com entregador vinculado; uma ordem sem vinculo nao ocupa entregador.

getModalidadeFrete/getDistanciaKm/getValorRepasseEntregador/getTaxaFreteRetida
mostram os dados salvos. getDataHoraRetirada/getDataHoraConclusao usam as datas
de coleta/entrega do SQL. getDataHoraCriacao/getDataHoraAceite e o endereco
historico permanecem null: nao existem essas colunas. getStatus antigo e
aceitar/registrarRetirada/concluirEntrega recusam snapshot JDBC; operacoes
conjuntas de estado e disponibilidade pertencem aos services. O dominio local
anterior continua disponivel para compatibilidade, sem prometer persistencia.

## Resumo de todas as revisoes anteriores

Usuario ganhou recuperacao de ID, documento fiscal, data e atividade do banco.
Consumidor, Administrador e Lojista podem ser reconstruidos com essas fichas.
Lojista reune a ficha usuario e a ficha lojista, exigindo que os IDs coincidam.
Nao criamos senha ficticia para conseguir montar um objeto consultado: consulta
publica nao traz senha_hash. Telefone e endereco que nao existem no SQL nao sao
apresentados como dados persistidos.

Os quatro perfis sobrescrevem getPerfil. Heranca significa reaproveitar dados
comuns de Usuario nas subclasses. Sobrescrita significa que cada subclasse tem
sua versao do metodo. Polimorfismo permite usar uma referencia Usuario e obter
o perfil real do objeto; toRegistro usa isso. Ainda precisamos demonstrar essa
utilidade no menu final e conferir o UML ja feito pelo grupo.

Entregador recupera numero da CNH, veiculo, bairro e estado operacional real.
Numero de CNH nao e categoria. Categoria/PBT/passageiros nao foram inventados
na leitura porque nao estao guardados no SQL. DISPONIVEL, EM_ROTA e INATIVO
substituem uma decisao baseada apenas em true/false no caminho JDBC.

Loja passou a ter uma visao dos dados comerciais do lojista: nao existe uma
tabela loja separada. Documento fiscal nao e automaticamente um CNPJ distinto.
Estado de abertura e endereco estruturado nao sao recuperados de colunas que
nao existem. Os construtores antigos continuam como modelo local.

Produto recupera a ficha comercial completa: nome, categoria, descricao, peso,
atividade, preco e saldo. OfertaProduto JDBC e uma visao desse produto; nao
possui outro saldo ou outro preco. Assim evitamos tratar o banco e a oferta
em memoria como dois estoques independentes na leitura persistente.

Snapshot significa fotografia da consulta. Depois que o banco muda, a fotografia
antiga nao muda junto. Para mostrar o saldo atualizado, precisamos consultar
novamente. Isso evita fingir que mudar uma variavel Java executou um UPDATE.
Os snapshots comerciais bloqueiam metodos locais de alteracao; as mudancas
persistentes passam pelos DAOs/services. Campos locais antigos continuam
existindo para compatibilidade e ainda nao sao um fluxo completo de producao.

## Regras de compra que continuam valendo

CompraService calcula os valores usando os dados do banco. A taxa do cliente
incide sobre o total dos produtos da compra; seu percentual ainda sera definido
explicitamente. A comissao do lojista e separada. HALF_UP significa arredondar
para o mais proximo e subir no empate: 1,225 vira 1,23. Esse arredondamento e
aplicado ao resultado final de cada calculo aprovado, nao ao preco historico
consultado novamente nem a valores com casas invalidas enviados para cadastro.

Estoque e conferido e baixado na confirmacao. Pagamento recusado mantem o pedido
CRIADO para nova tentativa e nao baixa estoque. A tentativa reenvia o carrinho,
pois o banco atual nao salva seus itens antes da aprovacao. Se o total mudar,
o fluxo recusa a nova tentativa nesse pedido. Historico completo de tentativas
tambem nao existe: o pagamento reutiliza a mesma linha.

Transacao significa confirmar varias mudancas como uma unidade. Se uma etapa
falha, rollback desfaz as mudancas daquela tentativa. Isso e necessario para
nao deixar, por exemplo, estoque baixado sem os itens da compra salvos.
Pagamento continua simulado; os registros no banco sao reais. Integracao com
cartao/banco financeiro real nao e exigida pelo escopo fornecido.

## O que esta pronto e o que ainda falta

Esta etapa conclui a primeira rodada de correspondencia das principais classes
com o banco. Nao significa projeto encerrado nem migracao integral dos services
para a hierarquia. Construtores/regras antigas continuam presentes e nao garantem
sozinhos exclusividade de entrega ou autorizacao. Duas tabelas de auditoria
continuam exigindo definicao de papel; nao adicionar edicao/exclusao de logs
sem entender sua responsabilidade.

Proximo desenvolvimento: fechar matriz CRUD dos DAOs, definir remocao segura
e iniciar main/menu. Depois integrar cadastros e concluir os services de entrega
necessarios ao roteiro. Falta definir distribuicao da taxa entre entregas.
CRUD significa criar, consultar, atualizar e remover registros; desativacao
por UPDATE nao equivale automaticamente ao DELETE pedido no escopo.

Tambem faltam conferir UML quando acessivel, revisar comentarios, disponibilizar
testes e instrucoes reproduziveis e preparar demonstracao/defesa. SQL oficial do
grupo e dependencia para outra maquina. Web/login completo e cobranca financeira
real nao sao obrigacoes explicitas do minimo Java apresentado.

## Testes desta etapa

Compilacao completa UTF-8 -Xlint:all -Werror, RunChecks e higiene aprovados.
CompraPersistidaCheck acrescenta 172 verificacoes: estados SQL, identidade,
listas/totais, preco historico com produto sem estoque, ausencia de dados
historicos inexistentes e bloqueio de transicoes locais em snapshots.
CompraServiceIntegrationCheck passou com 60 verificacoes JDBC, incluindo o grafo
compra/comandas/itens/ordem recuperado dos DAOs, mais regressao de checkout,
rollback e concorrencia. Banco UUID descartavel com DDL/FKs reais removido ao
final, sem mudar schema ou cadastros originais. Nao foi uma auditoria completa
nem demonstracao de interface, que ainda nao existe. Sem novo commit/push.

## Para voce explicar na apresentacao

1. Nosso Java agora recupera dados reais sem inventar campos que o banco nao guarda.
2. A compra geral se divide em comandas por loja; itens e entregas pertencem a elas.
3. O DAO conversa com o banco; o service coordena as regras; o terminal chama essas operacoes.
4. Testamos leitura, falhas e concorrencia, mas ainda precisamos concluir CRUD e menu.

Material de apoio: 25-Revisao-Compra-Pedido-Item-Entrega.pdf.
