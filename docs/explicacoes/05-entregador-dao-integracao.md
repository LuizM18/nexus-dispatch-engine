# EntregadorDao: integracao local - 27/10/2026

Implementado em src/main/java/com/nexusdispatch/dao/EntregadorDao.java.
O PDF 05-entregador-dao.pdf acompanha a proposta original; o estado de validacao
mais recente e registrado aqui. A logica implementada corresponde aquela proposta.

23 verificacoes JDBC passaram, usando UsuarioDao e EntregadorDao na mesma Connection.
Foram testados cadastro, ID compartilhado, duplicidade de ID, perfil incorreto,
usuario inexistente/inativo, disponibilidade, estado esperado divergente,
bloqueio em rota, atualizacao para van, SQL parametrizado com apostrofos,
paginacao, leitura de NULL e rollback conjunto. UsuarioDao passou 61 verificacoes;
os checks de entidades anteriores tambem passaram.

Executar novamente: ./tests/local/RunEntregadorDaoCheck.ps1

Isolamento: CREATE TEMPORARY TABLE a partir do DDL real, sem copiar cadastros.
A FK entregador-usuario e retirada somente da copia temporaria porque MySQL nao
suporta FK em tabela temporaria. Logo o teste NAO comprova FK, ON DELETE CASCADE,
triggers, concorrencia entre conexoes nem autorizacao de pessoas. Tabelas reais
nao foram modificadas. A Connection fecha as tabelas temporarias automaticamente.

Seguranca restante: o service deve autorizar cada operacao, coordenar estado
com ordens e impedir entregas simultaneas. Bloqueio por EM_ROTA depende do estado
persistido estar coerente. Documentacao, categoria da CNH, PBT e capacidade da van
ainda precisam de integracao. Nao foi adicionada exclusao fisica ao DAO.

A pasta Nexus Dispatch nao possui .git; nexus-dispatch-engine retornou acesso negado.
Nao houve sincronizacao, stage, commit ou push. Incluir DAO e este documento na
revisao do proximo commit, preservando alteracoes de terceiros. Testes em tests/local
continuam ignorados pelo Git conforme a organizacao existente.
