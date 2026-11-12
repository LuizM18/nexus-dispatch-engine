# Aceite exclusivo de entregas - 27/10/2026

Implementado em EntregadorDao.ocuparDisponivel, OrdemEntregaDao e EntregaService.
O service abre uma conexao dedicada, inicia transacao, ocupa um entregador ativo
DISPONIVEL, consulta ordens ativas e vincula uma ordem livre de comanda pronta
para coleta. Confirma tudo junto; indisponibilidade ou erro reverte tudo.

Aceitar preserva AGUARDANDO_COLETA e datas de coleta/entrega. EM_ROTA no cadastro
do entregador representa ocupado desde o aceite. Coleta e conclusao coordenadas
com a comanda ainda nao foram implementadas: nao liberar manualmente em rota.

40 verificacoes passaram, incluindo 10 disputas concorrentes com duas conexoes,
ordem inexistente/cancelada, estado antigo incoerente e erro SQL por trigger.
Os 23 testes anteriores do DAO tambem passaram. Executar:
./tests/local/RunAceiteCheck.ps1

Teste cria banco nexus_test_aceite_UUID, copia estruturas com CREATE TABLE LIKE,
insere somente dados sinteticos e remove exclusivamente esse banco ao terminar.
Nao altera nexus_db. LIKE nao copia FKs ou triggers; portanto nao valida esses
vinculos do esquema real. Deadlock SQLState 40001 e tratado no teste como tentativa
perdedora; o service reverte e propaga o erro para tratamento futuro na interface.

Limites: nao implementa autenticacao, autorizacao do ator, proximidade territorial,
conferencia documental, retirada/coleta ou conclusao. Todos os aceites devem passar
pelo service; SQL direto e alteracoes externas podem quebrar o protocolo.
O modelo antigo OrdemEntrega em memoria ainda nao usa este fluxo JDBC.

Novos arquivos: dao/OrdemEntregaDao.java e service/EntregaService.java.
Alterado: dao/EntregadorDao.java. PDFs didaticos 06, 07 e 08 nesta pasta incluem
codigo integral correspondente. Nenhum commit ou push realizado.
