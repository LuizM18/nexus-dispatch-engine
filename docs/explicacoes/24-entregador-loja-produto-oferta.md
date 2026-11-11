# Entregador, Loja, Produto e Oferta: alinhamento JDBC

Revisao de 03/11/2026, apos a revisao de Usuario/perfis/Lojista.
Nenhuma tabela ou DAO foi alterado. UML aguarda conferencia.

## Regra central: snapshot de consulta

Snapshot e uma fotografia dos dados lidos. Nao acompanha alteracoes posteriores
do banco e nao e uma reserva de estoque. Alteracoes comerciais/operacionais dos
objetos JDBC devem passar pelo DAO/service e por nova leitura apos commit.
Os construtores antigos continuam locais para preservar o dominio anterior e
seus testes; nao sao um segundo caminho de persistencia para o terminal.

## Entregador

Entregador(UsuarioRegistro, EntregadorRegistro) reune usuario ENTREGADOR e
complemento com o mesmo ID. Perfil/ID incorretos sao recusados. O SQL fornece
numero da CNH, tipo de veiculo, bairro e estado operacional; nao fornece categoria
da CNH, PBT ou passageiros. Esses tres atributos permanecem null na leitura,
inclusive para VAN. Isso preserva dados, mas nao comprova elegibilidade do veiculo.

getPerfil continua a sobrescrita ENTREGADOR. getStatusOperacao devolve
DISPONIVEL, EM_ROTA, INATIVO ou NULL preservado. getCnh devolve o numero;
getBairroOperacao devolve texto. toEntregadorRegistro retorna o complemento
original e recusa modelo local sem dados SQL.

isDisponivel so retorna true para estado DISPONIVEL e usuario ativo quando
reconstituido. Usuario inativo/NULL ou estado NULL/EM_ROTA nao fica disponivel.
O modelo antigo inicia INATIVO e continua com as operacoes locais existentes.

ficarDisponivel, ficarIndisponivel e atualizarVeiculoECnh recusam o snapshot
JDBC com IllegalStateException: nao simulam mudanca persistida nem liberam
entregador EM_ROTA. Alterar via EntregadorDao/service e consultar novamente.
getTipoVeiculo preserva o SQL. getCategoriaCnh/getPesoBrutoTotalKg/
getCapacidadePassageiros permanecem consultas dos dados locais opcionais.
definirVeiculoECnh e validarVeiculoECnh continuam validando o construtor antigo;
essas regras nao sao usadas para inventar informacoes ausentes da consulta SQL.

## Loja

Nao existe tabela loja. Loja(Lojista) e uma visao comercial do complemento do
lojista: exige dados persistidos, usa nome fantasia, bairro e origem textual.
getId devolve o ID do lojista; nao e uma nova PK de loja. getLojista mantem a
associacao, getNome devolve o nome fantasia carregado.

getDocumentoFiscal consulta o documento do usuario. getCnpj retorna null na
visao JDBC, pois nao ha CNPJ separado nem regra que transforme todo documento
fiscal em CNPJ. getEndereco tambem retorna null: nao decompor texto livre em
CEP/rua/numero ficticios. getEnderecoOrigem e getBairroOperacao preservam os textos.

getStatusAberta retorna NULL na visao JDBC: nao existe coluna aberta/fechada.
isAberta recusa essa visao em vez de tratar desconhecido como false. abrir,
fechar, setNome e setEndereco tambem recusam; alteracoes do cadastro comercial
pertencem ao lojista e seus DAOs. O construtor antigo com nome/CNPJ/Endereco
continua local e conserva validacoes e abertura em memoria. Esse estado nao
deve ser alegado como persistido nem usado como dado do SQL no futuro menu.

## Produto

Produto(Loja, ProdutoRegistro) exige ID salvo e id_lojista igual ao ID da visao
comercial. Preserva a linha inteira, incluindo categoria, peso, atividade,
preco e estoque. toRegistro retorna esse registro imutavel; recusa produto
antigo local sem dados comerciais SQL. isPersistido identifica esse modo.

getId/getLoja/getNome/getDescricao recuperam identidade, associacao e textos.
A descricao SQL aceita TEXT valido mesmo acima do limite local antigo de 1000
caracteres; nao cortar dados durante a leitura. getCategoria/getPesoKg/getAtivo
devolvem os campos SQL, incluindo atividade NULL. No modo local, esses tres
campos sao desconhecidos e retornam null.

getPrecoUnitario/getEstoqueDisponivel consultam a fonte comercial unica do
snapshot. setNome/setDescricao recusam o modo JDBC: usar atualizarCadastro do
DAO/service e nova leitura. O construtor antigo e seus validadores de nome e
descricao continuam locais, com as regras anteriores, sem dados comerciais
obrigatorios ficticios. Nenhum UPDATE e executado por um getter/setter.

## OfertaProduto

Nao existe tabela oferta_produto. OfertaProduto(Produto) exige produto JDBC e
e somente uma visao dele. Nao recebe preco/estoque inicial de fora: o construtor
antigo com tres argumentos recusa produto JDBC. Assim, duas visoes do mesmo
produto consultam a mesma fonte, sem um saldo comercial paralelo.

getPreco/getEstoque delegam ao Produto; getProduto/getLoja preservam associacoes.
getId permanece null, porque nao existe chave SQL de oferta; para identificar
o produto, usar getProduto().getId(). getQuantidadeDisponivelParaVenda retorna
zero para atividade FALSE/NULL e o saldo para TRUE. Isso nao reserva unidades;
o checkout ainda precisa validar o banco na sua transacao.

setPreco/adicionarEstoque recusam o modo JDBC; alterar por ProdutoDao/service
e consultar novamente. getEntradasEstoque tambem recusa o modo JDBC, pois nao
existe historico de entradas consultado nesse schema. Nao registrar saldo lido
como reposicao, nem apresentar lista vazia como comprovacao de nenhum evento.

O construtor antigo e registrarEntrada/validarPreco conservam o comportamento
local, incluindo historico em memoria, limite int com addExact e preco nao
negativo. Essa compatibilidade nao altera o contrato SQL: ProdutoRegistro exige
preco/peso positivos e casas decimais exatas. Oferta local nao e cadastro SQL.

## Exemplo de leitura

Lojista perfil = new Lojista(usuarioRegistro, lojistaRegistro);
Loja loja = new Loja(perfil);
Produto produto = new Produto(loja, produtoRegistro);
OfertaProduto oferta = new OfertaProduto(produto);

Cada registro vem de seu DAO. Consultar na mesma transacao quando a operacao
exigir consistencia. Apos compra com commit, o snapshot antigo preserva o saldo
anterior; nova consulta retorna o saldo confirmado. Nunca usar esse saldo antigo
para decidir compra sem verificacao transacional.

## Verificacoes e limites

RunChecks e higiene aprovados. CorrespondenciaComercialCheck: 97 checks novos,
cobrindo estados/NULL, perfis e IDs incorretos, ausencia de dados ficticios,
descricao TEXT longa, atividade de produto e bloqueio de mutacoes/estoque paralelo.
Compilacao completa UTF-8 -Xlint:all -Werror aprovada.
CompraServiceIntegrationCheck: 51 checks JDBC aprovados, incluindo reconstituicao
real de entregador, loja/produto/oferta, snapshot antigo e nova leitura apos
commit, regressao de checkout, rollback e disputa pela ultima unidade.
Teste em banco UUID descartavel com DDL/FKs reais, removido no finally.

Nao ha nova tabela ou coluna. Categoria/PBT/passageiros, CNPJ separado, endereco
estruturado e abertura permanecem sem persistencia no SQL atual. Definir se sao
necessarios a demonstracao antes de qualquer mudanca no banco do grupo.
Compra/Pedido/ItemPedido/OrdemEntrega antigos ainda precisam de revisao.
Nao houve migracao integral dos services para a hierarquia nem integracao do
menu nesta etapa. Chamadas diretas no dominio antigo ainda nao garantem a
exclusividade transacional dos services. Sem novo commit/push.

## Questoes para estudar

1. Por que uma nova consulta e necessaria depois de alterar estoque no banco?
2. Por que NULL de abertura de loja nao pode ser interpretado como fechada?
3. Qual a diferenca entre numero e categoria de CNH na reconstituicao?

Material complementar: 24-Entregador-Loja-Produto-Oferta.pdf.
