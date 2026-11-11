# Validacao local - 02/11/2026

Escopo: fontes atuais de src/main/java, incluidos arquivos locais ainda nao commitados.
Nenhum commit, stage ou push. Nenhuma instalacao. Nao equivale a auditoria completa.

## Resultados executados
- Compilacao completa: javac UTF-8 -Xlint:all -Werror, aprovada.
- RunChecks: todas as suites de dominio em memoria aprovadas; limites reportados abaixo.
- Higiene de fontes: aprovada. git diff --check sem erros; avisos LF/CRLF existentes.
- UsuarioDao: 61 verificacoes JDBC em tabela temporaria.
- LojistaRegistro: 30; LojistaDao: 36, com FKs em banco descartavel.
- Produto: 53, incluindo modelo/JDBC e entradas semelhantes a injecao SQL.
- EntregadorDao: 23 JDBC; tabelas temporarias, sem comprovacao de FKs.
- PedidoMestreRegistro: 28 verificacoes.
- Aceite: 40 verificacoes, incluindo 10 disputas de duas conexoes e rollback por falha induzida.
  A copia CREATE TABLE LIKE nao preserva FKs; valida concorrencia, nao integridade referencial completa.
- CompraPersistenciaCheck: 49 verificacoes novas, com DDL real e FKs em banco UUID descartavel.
  Cobre pedido/comanda, ID/data, consultas, paginacao, estados desatualizados,
  rejeicao de escrita em autoCommit, FKs invalidas, commit visivel em outra conexao,
  rollback conjunto de pedido/comanda/estoque e preservacao de commit anterior.
- Bancos descartaveis removidos pelos finally dos testes; sem copia de dados reais.
- .env e extensoes .pem/.key nao encontrados entre arquivos rastreados nesta verificacao.

## Limites e pendencias antes da liberacao
- Nao existe ainda autenticacao/autorizacao completa. Filtrar por idCliente nao valida o ator.
- LojistaDao depende do service para validar perfil; FK valida existencia apenas.
- UsuarioDao, LojistaDao e ProdutoDao ainda oferecem DELETE fisico: adequar regra de desativacao.
- Aceite transacional impede duas ordens pelo fluxo testado, mas dominio antigo pode ser chamado diretamente.
- Cancelamento integral/idempotencia e disputa coleta versus cancelamento ainda nao implementados.
- Pagamento/itens e checkout completo ainda pendentes; nao foram declarados testados.
- SQL versionado e enums de pagamento ainda precisam alinhamento; banco local nao prova instalacao do zero.
- TLS da conexao local exige criptografia; validacao da identidade e exigida para host remoto no codigo.
- Nao houve pentest HTTP, teste de sessao nem auditoria de dependencias contra base atual de vulnerabilidades.

Novo teste local: tests/local/CompraPersistenciaCheck.java, ignorado pelo Git.
Proximo passo: ItemPedidoRegistro/ItemPedidoDao conforme colunas reais de item_pedido.
Commit permanece bloqueado por decisao do usuario ate concluir DAOs e correlacoes.

## Integracao de ItemPedidoRegistro e ItemPedidoDao
- Fontes adicionados nos pacotes model e dao; SQL preservado.
- Compilacao completa UTF-8 -Xlint:all -Werror aprovada.
- CompraPersistenciaCheck ampliado: 89 verificacoes no total (40 novas para itens).
- Testado: ID gerado, imutabilidade da entrada/lista, observacao realmente SQL NULL,
  texto semelhante a injecao armazenado literalmente, produto/tabela preservados,
  paginação e separacao de comandas, produto inexistente/de outro lojista/inativo,
  bloqueio de insercao em todos os estados diferentes de PAGO, autoCommit indevido,
  preco historico apos alteracao do produto, limites de entrada e rollback de
  pedido/comanda/item/estoque com FKs reais em base descartavel removida no finally.
- Nao comprova checkout completo, autorizacao do ator nem concorrencia de preco,
  preparacao/cancelamento: coordenacao do service ainda pendente.
- Mantida limitacao do SQL: nome/descricao historicos nao sao persistidos.
- PDFs de ensino 13 e 14 permanecem em docs/explicacoes, ignorados pelo Git.
- Sem stage, commit ou push. Teste local ignorado pelo Git.

## Validação de PagamentoDao e OrdemEntregaDao - 03/11/2026
- Compilação completa: javac -encoding UTF-8 -Xlint:all -Werror, aprovada.
- PagamentoEntregaIntegrationCheck: 134 verificações (89 de regressão + 45 novas), aprovadas em banco descartável com DDL/FKs reais, removido ao final.
- Novos cenários: valor divergente, pagamento duplicado, token antigo/incorreto, texto SQL literal, recusa/retry/autorização, pedido cancelado, criação duplicada de ordem, coleta por outro entregador, transições repetidas, datas, cancelamento antes/depois da coleta, rollback e transação obrigatória.
- Erro inicial no fixture: bairro_atuacao corrigido para bairro_operacao; nova execução integral aprovada. SQL do projeto inalterado.
- AceiteConcorrenciaCheck: 40 verificações e 10 disputas com duas conexões, aprovadas.
- RunChecks e CheckSourceHygiene: aprovados. Modelos antigos ainda permitem contornar o service; isso permanece pendente, não representa segurança completa.
- A suíte nova testa operações dos DAOs. Cancelamento integral, autenticação, liberação do entregador após conclusão e confirmação coordenada ainda dependem dos services. Corrida entre duas inserções de ordem não foi exercitada nesta suíte.
- Guias: docs/explicacoes/20-PagamentoDao.pdf e 21-OrdemEntregaDao.pdf, com explicação por método, código e três questões cada.
- Nenhum commit, stage ou push executado. Documentação e testes locais devem ficar fora do próximo commit de código.
