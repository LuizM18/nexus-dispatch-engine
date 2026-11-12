# Arquitetura Java atual

Fluxo: ConsoleNexus -> service -> DAO -> JDBC -> MySQL. O terminal autentica
a conta ativa com e-mail e senha e abre o menu do perfil retornado. Usa o ID
autenticado nas operacoes da propria conta, como pedidos do cliente,
disponibilidade do entregador e produtos do lojista. Regras de negocio e
transacoes permanecem nos services.

- model: dominio e registros JDBC; validar objetos nao persiste dados.
- enums: perfis/estados; enums antigos ainda precisam de reconciliacao.
- database: ConexaoBD, DriverManager e configuracao externa.
- dao: SQL parametrizado e recursos; Connection recebida do chamador.
- service: coordenacao, regras e transacoes; conexao dedicada por chamada.
- src/test/java: testes de domínio e integração executados pelo Maven.
- tests/local: apoio JDBC local ignorado; nao e suite JUnit distribuida.

CompraService: checkout, pagamento simulado, taxa/comissoes, estoque, comandas,
itens e retry. Nao cria entregas nem autentica ator. Retry reenvia carrinho e
exige totais atuais iguais aos salvos; nao preserva a composicao original.

ProdutoService: transacao dedicada por operacao, contexto de lojista ativo,
propriedade, cadastro, consulta, atualizacao, reposicao, atividade e remocao.
ProdutoDao.excluirSemHistorico protege compras vinculadas com bloqueio/FK.

CadastroService: usuario e complemento LOJISTA/ENTREGADOR na mesma transacao.
UsuarioService autentica consultando a credencial ativa e verificando o hash
com SenhaService. O hash usa PBKDF2 do JDK, formato P2 de 60 caracteres e salt
aleatorio. A resposta do login traz apenas o cadastro, sem o hash.

EntregaService: aceite coordenado/exclusivo. Coleta, conclusao, liberacao e
cancelamento integral ainda nao estao integrados nos services, mesmo havendo
operacoes de apoio nos DAOs.

DAOs retornam registros. Usuario/Consumidor/Administrador/Lojista ja permitem
reconstituicao dos dados consultados; Lojista une usuario+lojista com mesmo ID.
Consulta publica nao recupera hash. Entregador reconstitui seu complemento e
estado operacional, sem inventar categoria da CNH/PBT/passageiros. Loja e visao
comercial do lojista; Produto concentra o snapshot comercial e Oferta JDBC
delega preco/estoque a ele. Mutacoes dessas visoes passam por DAO/service e
nova leitura. Construtores antigos continuam locais para compatibilidade.
Telefone/endereco e certos atributos de veiculo nao devem ser declarados
persistidos. Compra/Pedido/ItemPedido/OrdemEntrega agora reconstituem o grafo
salvo com estados SQL e validacao de identidade/totais. Estados locais antigos
nao sao traduzidos automaticamente; mutacoes de snapshots passam por services.
Nome/descricao historicos de item e endereco historico permanecem desconhecidos
onde nao existem no SQL. Falta integrar a consulta no menu e concluir services.
Ver revisao-correspondencia-classes-2026-11-03.md e guias 22 a 25.
