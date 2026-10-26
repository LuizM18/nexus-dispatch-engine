# Nexus Dispatch

Projeto academico de logistica local: consumidores compram produtos de lojas e entregadores realizam as entregas. Uma compra pode gerar um pedido e uma entrega por loja.

## Estado atual - 26/10/2026

- Java 25 e Maven; entidades e enums com validacoes locais.
- Driver MySQL, dotenv-java e JUnit declarados no pom.xml.
- Conexao JDBC ao servidor MySQL 8.4.13 confirmada pelo usuario no VS Code.
- Banco `nexus_db` importado e acessivel; estrutura consultada no MySQL local.
- UsuarioDao implementado conforme a tabela real, com PerfilUsuario e UsuarioRegistro.
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
| `tests/manual` | Verificacoes existentes com metodo main |
| `scripts` | Comandos auxiliares de verificacao e geracao |
| `docs` | Requisitos, decisoes e documentacao das entidades |
| `database` | Local reservado para o SQL oficial do grupo |
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

Para validar as entidades com Java 25, execute no PowerShell:

```powershell
./scripts/RunChecks.ps1
```

Os diagnosticos `PENDENTE` sao lacunas conhecidas, nao regras protegidas. Os checks nao substituem os futuros testes JDBC com MySQL real.

Para testar UsuarioDao no MySQL real, em tabela temporaria isolada da conexao:

```powershell
./scripts/RunUsuarioDaoCheck.ps1
```

O teste passou em 61 verificacoes e nao altera cadastros reais. Consulte
[UsuarioDao: implementacao e validacoes](docs/usuario-dao.md) para requisitos e limites.

Execute tambem `./scripts/CheckSourceHygiene.ps1` antes de preparar a revisao.
Consulte [Governanca e proximas implementacoes](docs/governanca-e-proximos-passos.md).

## Documentacao

- [Indice de documentos](docs/README.md)
- [Organizacao e mudancas](docs/organizacao.md)
- [Revisao de integracao](docs/revisao-integracao-2026-10-18.md)

## Proximos passos

1. Guardar o script SQL oficial na pasta database; o esquema ja esta importado no MySQL.
2. Integrar os perfis Java ao banco, comecando pelo cadastro complementar de lojista.
3. Implementar os demais DAOs com PreparedStatement e recursos fechados corretamente.
4. Coordenar compra, pedidos, itens, estoque e entregas na mesma transacao, com rollback total em falha.

O repositorio Git fica na pasta `nexus-dispatch-engine`. A pasta de edicao `Nexus Dispatch` e uma copia sem `.git`; as alteracoes desta entrega foram consolidadas no repositorio para commit local na branch `Java_files`. O envio ao GitHub depende da aprovacao do usuario. Nao trabalhar alternadamente nas copias sem comparar as alteracoes.
