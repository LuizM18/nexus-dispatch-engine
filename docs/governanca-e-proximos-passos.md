# Governanca e proximas implementacoes

Atualizacao em 25/10/2026. Os arquivos atuais da pasta Nexus Dispatch sao a
referencia desta entrega. A PR #2 de LuizM18/nexus-dispatch-engine foi consultada
como contexto: fechada sem merge, branch Java_files, commit 841c30d.
O comentario de auditoria pede remocao de classes vazias e documentos binarios.
Nao houve nova aprovacao do linter nem alteracao da branch remota.

## Limpeza realizada

Removidas 25 classes vazias sem referencias operacionais: 5 controllers,
8 DAOs (todos exceto UsuarioDao), 8 services, NexusDispatchApplication,
DatabaseConfig e 2 testes JUnit vazios. Responsabilidades futuras ficam neste
documento, sem arquivos publicos de fachada. A copia de seguranca e o PDF
historico ficam fora do projeto. Nenhum dado de banco foi removido.

Administrador foi preservado: seu construtor invoca as validacoes de Usuario
e seus comportamentos herdados sao exercitados por UsuarioCheck. O mesmo vale
para Lojista. Isso nao implementa permissoes administrativas. A aceitacao desse
desenho pelo revisor ainda precisa ser confirmada; nao foi adicionado metodo
artificial apenas para satisfazer verificacao por quantidade de linhas.

.gitignore passou a excluir archive, outputs, work e artefatos PDF/DOCX/JAR.
Ignorar arquivos nao retira arquivos ja rastreados: na copia Git, sera preciso
remover o PDF em outputs e as 10 imagens em work do indice e da arvore de entrega.
Os checks e scripts de work devem ser substituidos pelos caminhos tests/manual
e scripts desta pasta. O gerador historico agora exige --output-dir fora do projeto.

CheckSourceHygiene.ps1 verifica placeholders simples e artefatos de entrega.
Ele e um apoio local, nao o motor que produziu REJECTED_BY_LINTER.

## Sequencia de arquivos a implementar

1. model/LojistaRegistro.java e dao/LojistaDao.java: representar e persistir a
   tabela lojista (id_lojista, nome_fantasia, taxa_comissao_pct, bairro_operacao,
   endereco_origem), respeitando o DDL real, sem inventar taxa ou endereco.
2. service/CadastroLojistaService.java: inserir usuario com perfil LOJISTA e
   seus dados complementares na mesma Connection e transacao. Se a segunda
   insercao falhar, desfazer a primeira. Acrescentar teste de integracao isolado.
3. service/UsuarioService.java e testes reais: gerar/verificar hash com biblioteca
   apropriada, definir regras de documento/email e controlar autorizacao.
   Compatibilizar a estrategia de hash com a coluna atual CHAR(60).
4. model/ProdutoRegistro.java, dao/ProdutoDao.java e testes: integrar produto ao
   id_lojista, categoria, preco_unitario, peso_kg, estoque_disponivel e status_ativo.
   O Java atual separa OfertaProduto; essa diferenca deve ser resolvida explicitamente.
5. DAOs de pedido_mestre, comanda_lojista e item_pedido, seguidos de CompraService:
   mapear Compra/Pedido ao SQL, coordenar estoque, totais e rollback integral.
6. Persistencia de entregador e ordem_entrega, seguida de EntregaService:
   aceite exclusivo, transicoes conjuntas e testes de concorrencia.
7. Controllers e inicializacao da aplicacao somente quando os servicos tiverem
   operacoes reais, respostas sem hash e regras de acesso testadas.

Os nomes novos sao propostas; nao foram criadas classes vazias correspondentes.
BairroDao, LojaDao e OfertaProdutoDao nao devem ser recriados supondo tabelas
inexistentes no SQL atual.

## Proposta de commit

Titulo sugerido: feat(jdbc): implementar UsuarioDao e remover estruturas vazias

Incluir codigo JDBC, modelos de persistencia, testes, scripts de verificacao,
pom.xml, .env.example sem credenciais e documentacao Markdown leve. Incluir as
remocoes dos placeholders e dos antigos artefatos versionados de outputs/work.
Excluir .env, .vscode, target, archive, backups e todo o pacote para Drive.

Antes de aplicar na branch, conferir diff contra a copia Git, preservar arquivos
de terceiros e repetir compilacao, checks, teste JDBC e git diff --check.
A copia Git nao foi sincronizada e nenhum commit, push ou merge foi executado.
O manifesto externo compara arquivos atuais com a copia local Java_files;
ele nao confirma que a branch remota permaneceu inalterada.
