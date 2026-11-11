# Nexus Dispatch

Projeto academico de logistica local: consumidores compram produtos de lojas e entregadores realizam as entregas. Uma compra pode gerar um pedido e uma entrega por loja.

## Estado atual - 03/11/2026

Atualização de 07/11. O terminal abre menus de cliente, lojista, entregador e administrador. Para ensaiar o pedido com uma taxa de exemplo, rode `./scripts/RunConsole.ps1 -TaxaCompraPct 7`. O [roteiro da banca](docs/explicacoes/33-terminal-banca.md) cobre CRUD, carrinho, disponibilidade e cadastro inicial de administrador.

Atualizacao de 06/11: EntregaService agora possui aceite em lote, preparo,
coleta, conclusao coordenada e desistência anterior a coleta. Checkout com o
novo construtor cria ordens atomicamente, com rateio proporcional e 10% iniciais
configuraveis para plataforma. Malha dirigida e limites por veiculo implementados;
cadastro real da malha/metadados e integracao ao menu ainda pendentes. Construtores
antigos de CompraService continuam como checkout isolado sem criar ordens.
Ver [guia atualizado](docs/explicacoes/31-entrega-service.md).
Validacao: `./scripts/RunEntregaServiceCheck.ps1`, 161 checks JDBC aprovados.
Os pontos abaixo preservam o retrato de 03/11 e sao atualizados por esta nota.

Esta secao substitui as listas historicas abaixo. Referencias atuais:
[requisitos Java](docs/requisitos.md), [arquitetura](docs/arquitetura.md) e
[sequencia de trabalho](docs/cronograma.md).

- Nove DAOs de negocio e registros correspondentes; CRUD ainda parcial.
- CompraService: checkout, pagamento simulado, taxa configuravel, comissoes,
  estoque transacional, comandas/itens e retry. Nao cria entregas.
- EntregaService: aceite exclusivo; coleta/conclusao/liberacao ainda pendentes.
- Usuario/Consumidor/Administrador/Lojista reconstituem registros; getPerfil
  sobrescrito nos quatro perfis. Entregador reconstitui estado/complemento;
  Loja/Produto/Oferta possuem visoes JDBC sem estoque paralelo ao snapshot.
  Compra/Pedido/ItemPedido/OrdemEntrega reconstituem vinculos/totais e estados
  SQL; primeira rodada de correspondencia das classes concluida, integracao final pendente.
- Primeiro menu funcional: ConsoleNexus, CRUD de produtos, reposicao e atividade
  via ProdutoService. Selecao de lojista por ID em demonstracao, sem login.
  Execute `./scripts/RunConsole.ps1`. Menus dos demais fluxos ainda pendentes.
- Opcao 9 cadastra cliente/lojista/entregador; opcao 10 lista usuarios e IDs.
  Lojista novo fica selecionado para cadastrar produtos. Banco vazio nao exige
  inserts manuais. CadastroService grava usuario/complemento juntos; SenhaService
  usa PBKDF2 do JDK em formato P2 de 60 caracteres. Isso ainda nao e login.
- UML existente segundo o usuario; conferencia adiada por falta de acesso.
- O DDL base e a atualizacao V6.1 estao na raiz do repositorio. A instalacao do zero ainda precisa ser verificada.
- Ultimas verificacoes registradas: compilacao estrita, regressao incluindo
  34 checks novos de Usuario persistido, 97 de correspondencia comercial e
  172 de compra persistida e 114 checks de checkout/perfis/visoes/menu/cadastros em
  banco descartavel. Suite JDBC local ignorada pelo Git; nao acompanha todo clone.
- Alteracoes atuais ainda locais, sem novo commit/push nesta etapa.

Proximo desenvolvimento: demais operacoes CRUD de perfis/entidades e services/
menus de compra e entrega necessarios ao roteiro de demonstracao.
Login completo, web e pagamento financeiro real nao sao minimos explicitos do
escopo Java. Banco real continua obrigatorio nas operacoes demonstradas.

## Historico de implementacao - 27/10/2026

As listas a seguir preservam o registro daquela data; itens indicados como
pendentes podem ja ter sido implementados. Consulte a secao atual e requisitos.md.

- Java 25 e Maven; entidades e enums com validacoes locais.
- Driver MySQL, dotenv-java e JUnit declarados no pom.xml.
- Conexao JDBC ao servidor MySQL 8.4.13 confirmada pelo usuario no VS Code.
- Banco `nexus_db` importado e acessivel; estrutura consultada no MySQL local.
- UsuarioDao implementado conforme a tabela real, com PerfilUsuario e UsuarioRegistro.
- LojistaDao e LojistaRegistro implementados localmente; testes novos em tests/local, ignorados pelo Git.
- ProdutoDao e ProdutoRegistro implementados localmente, incluindo operacoes condicionais de estoque.
- EntregadorRegistro e StatusOperacaoEntregador alinhados ao SQL local: ID compartilhado com usuario, CNH como numero, bairro em texto e estados operacionais. Novo cadastro inicia INATIVO; DAO ainda pendente.
- Material de estudo organizado em docs/explicacoes, com indice e explicacoes por arquivo.
- Classes vazias removidas: os demais DAOs, servicos e controllers serao criados com implementacao real.
- Nao existe fluxo web completo; DatabaseConfig e o ponto de entrada vazio foram removidos.
- O banco e a referencia para a integracao; as entidades anteriores ainda precisam de adaptacoes.
- Os checks manuais validam entidades em memoria; nao comprovam persistencia, autorizacao ou concorrencia.

## Onde trabalhar

| Pasta ou arquivo | Finalidade |
|---|---|
| `src/main/java` | Codigo da aplicacao: model, enums, database, dao, service e controller |
| `src/main/resources` | Recursos da aplicacao |
| `src/test/java` | Reservado para testes JUnit reais; placeholders removidos |
| `src/test/java` | Testes JUnit de domínio, serviços JDBC e terminal |
| `src/test/resources` | Esquema MySQL para os testes de integração |
| `scripts` | Comandos auxiliares de verificacao e geracao |
| `docs` | Requisitos, decisoes e documentacao das entidades |
| `database` | Migracao de estorno e referencias aos scripts SQL do grupo |
| `archive` | Apoio local ignorado; PDF historico preservado fora da pasta do projeto |
| `archive/apoio-local` | Imagens, caches e materiais temporarios antigos; ignorados pelo Git |
| `target` | Saidas de compilacao e verificacao; ignoradas pelo Git |
| `.env` | Credenciais locais; nunca commitar |
| `.env.example` | Modelo sem senha para configurar outra maquina |
| `pom.xml` | Dependencias e compilacao Maven |

## Executar

Abra esta pasta como raiz no VS Code. Preencha o `.env` local usando `.env.example` como referencia.

Em Executar e Depurar, selecione `Nexus: testar servidor MySQL` para testar apenas o acesso ou `Nexus: testar banco nexus_db` para testar o banco importado. As configuracoes locais ficam em `.vscode/launch.json`.

Com Maven no PATH: `mvn compile`. Isso exige acesso ao repositorio Maven para obter dependencias. Nao inclui teste real do MySQL.

## Testes com Maven

Com JDK 25 e Maven 3.9 no PATH, execute os testes de domínio na raiz do projeto.

```powershell
mvn test
```

Para rodar a suíte completa, use MySQL 8.4 acessível por TCP em `127.0.0.1`, com TLS habilitado. Prepare uma conta de teste com permissão para criar e remover catálogos `nexus_test_*`, criar tabelas e triggers e consultar e modificar os dados desses catálogos. Defina a porta, o usuário e a senha na sessão do terminal. Exemplo em PowerShell 7.

```powershell
$env:NEXUS_TEST_DB_PORT = '3306'
$env:NEXUS_TEST_DB_USER = 'nexus_test'
$env:NEXUS_TEST_DB_PASS = Read-Host 'Senha do MySQL de teste' -MaskInput
mvn -Pintegracao verify
```

O perfil `integracao` executa os testes de domínio, o DAO de usuários, os serviços de compra e entrega, e os fluxos do terminal com entrada simulada. Cada cenário com JDBC cria um catálogo vazio com nome aleatório, carrega o esquema versionado e remove o catálogo ao terminar, inclusive se uma asserção falhar. A configuração usa apenas as variáveis `NEXUS_TEST_DB_*`. Em caso de encerramento forçado do processo, confira se ficou algum catálogo de teste antes de removê-lo.

Os relatórios ficam em `target/surefire-reports` (domínio) e `target/failsafe-reports` (integração). Ao alterar o esquema usado pelos DAOs, atualize também `src/test/resources/esquema-mysql-teste.sql`. Ele reúne as estruturas usadas na suíte, incluindo as chaves estrangeiras e o estado de pagamento `ESTORNADO`.

Execute tambem `./scripts/CheckSourceHygiene.ps1` antes de preparar a revisao.
Consulte [Governanca e proximas implementacoes](docs/governanca-e-proximos-passos.md).

## Documentacao

- [Indice de documentos](docs/README.md)
- [Organizacao e mudancas](docs/organizacao.md)
- [Revisao de integracao](docs/revisao-integracao-2026-10-18.md)

## Proximos passos

1. Verificar a instalacao completa com os scripts SQL do grupo em um banco descartavel.
2. PedidoMestreDao incluido: insercao com retorno de ID/data, busca, historico paginado e atualizacao condicional de estado. Compilacao verificada; integracao JDBC deste DAO ainda pendente. EntregadorDao e aceite transacional ja existem; categoria da CNH e dados da van continuam pendentes no SQL.
3. ComandaLojistaDao, ComandaLojistaRegistro e StatusComanda incluidos; faltam ItemPedidoDao e PagamentoDao. Alinhar os meios de pagamento e completar os services transacionais. Integracao JDBC da comanda ainda pendente.
4. Coordenar compra, pedidos, itens, estoque e entregas na mesma transacao, com rollback total em falha.

O codigo Java e os scripts de banco do grupo estao reunidos no repositorio LuizM18/nexus-dispatch-engine.

Entrega de 27/10: [explicacao do registro de entregador](docs/explicacoes/04-entregador.md) e [preparacao do proximo commit](docs/proximo-commit-entregador.md). A conexao Git da pasta atual foi configurada em 27/10/2026.


EntregadorDao integrado localmente em 27/10: 23 checks JDBC passaram com tabelas temporarias. Ver docs/explicacoes/05-entregador-dao-integracao.md. Proximo passo: services transacionais de cadastro e alinhamento dos campos de veiculo no SQL. Codigo presente no commit inicial; documentacao complementar nesta entrega.

Aceite exclusivo implementado no EntregaService: 40 verificacoes passaram, incluindo dez disputas entre duas conexoes e rollback por erro SQL. Ver docs/explicacoes/06-aceite-transacional.md para limites e proximos passos.


PedidoMestreRegistro e StatusPedidoMestre incluidos em 27/10: 28 verificacoes em memoria passaram. Persistencia pendente no PedidoMestreDao. [Estado e proximas etapas](docs/explicacoes/10-pedido-mestre-registro.md).
