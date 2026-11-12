# Preparacao do proximo commit: entregador

Titulo sugerido:

    feat(model): mapear entregador conforme o esquema MySQL

Arquivos de codigo novos:

- src/main/java/com/nexusdispatch/model/EntregadorRegistro.java
- src/main/java/com/nexusdispatch/enums/StatusOperacaoEntregador.java

Documentacao nova/atualizada:

- README.md
- docs/README.md
- docs/explicacoes/README.md
- docs/explicacoes/04-entregador.md
- docs/governanca-e-proximos-passos.md
- docs/proximo-commit-entregador.md

Nao incluir .env, target, .vscode, archive ou tests/local (testes pessoais ignorados).

## Situacao da copia Git

A pasta atual Nexus Dispatch nao contem .git. Em 27/10/2026 a tentativa de consultar
nexus-dispatch-engine retornou acesso negado. Portanto nao foi possivel comparar,
copiar para essa pasta, preparar o indice ou verificar sua branch atual.
Nenhum commit, push ou merge foi executado. Estes arquivos estao preparados apenas
na copia de desenvolvimento Nexus Dispatch.

Antes de commitar na copia Git, comparar os caminhos acima, preservar mudancas
de terceiros e transferir somente esta entrega. Nao substituir o repositorio
inteiro. Repetir compilacao, verificacoes e git diff --check nessa copia.
Revisar o diff e conferir a branch com git status e git branch --show-current.

## Escopo e limites para a descricao do commit

Registro alinhado as cinco colunas reais da tabela entregador, com ID compartilhado
com usuario, CNH como numero, quatro veiculos e enum de estado operacional.
Fabrica de novos cadastros inicia INATIVO e exige bairro-base. O construtor preserva
null de status/bairro permitido pelo esquema. Sem DDL ou alteracao de dados reais.

Van mantida; categoria da CNH, PBT e capacidade continuam no modelo de dominio,
sem colunas de persistencia. A migracao desses campos permanece pendente.
EntregadorDao e cadastro transacional ainda nao foram implementados.

Validacao: 32 checks novos em memoria, verificacoes anteriores e higiene local
passaram. Nao afirmar que o fluxo de persistencia do entregador esta concluido.

Atualizacao: incluir tambem src/main/java/com/nexusdispatch/dao/EntregadorDao.java e docs/explicacoes/05-entregador-dao-integracao.md. 23 checks JDBC passaram; limites documentados.

Incluir OrdemEntregaDao.java, EntregaService.java, atualizacao de EntregadorDao.java e docs/explicacoes/06-aceite-transacional.md. Testes locais e PDFs permanecem ignorados.

## Situacao atual apos configurar Git
A pasta Nexus Dispatch agora e o repositorio Guiiprmode/Nexus-Dispatch. O commit inicial 0c32c38 ja contem os fontes Java citados acima. A entrega atual atualiza somente documentacao na branch Java_files. Os relatos de acesso negado acima se referem a antiga copia nexus-dispatch-engine. Nao houve push nesta entrega.
