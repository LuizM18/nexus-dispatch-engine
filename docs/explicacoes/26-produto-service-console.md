# ProdutoService e primeiro terminal JDBC

03/11/2026. Entrega incremental, sem encerrar todos os requisitos do projeto.

## Como executar e usar

Na raiz do projeto, com JDK 25, .env configurado e banco do grupo preparado:

./scripts/RunConsole.ps1

O script usa JARs do pom.xml no repositorio Maven local. Se faltarem, executar
mvn compile primeiro. Compila os fontes com UTF-8 -Xlint:all -Werror em
target/console e inicia ConsoleNexus. Nao cria schema nem embute credenciais.

1. Escolher 1 e informar ID de lojista existente e ativo. Selecao de ID e modo
   de demonstracao; nao autentica uma pessoa nem substitui login.
2. Escolher 2 para cadastrar produto e anotar ID retornado. Valores decimais
   aceitam virgula ou ponto, sem separador de milhar: 12,50 e peso 1,250 kg.
3. Usar 3 para listar paginas de 20 produtos; deslocamento 0 e o inicio, 20 a
   pagina seguinte. Usar 4 para buscar por ID.
4. Usar 5 para editar cadastro completo, 6 para repor quantidade positiva e
   7 para ativar/desativar. Cadastro nao sobrescreve estoque/atividade.
5. Usar 8 para remover. Produto e exibido antes da confirmacao s/n; historico
   de compra bloqueia remocao. Nesse caso usar desativacao.
6. Usar 0 para sair e consultar novamente ao reabrir para verificar persistencia.

Nao ha menu de cadastro de lojista ainda: este fluxo exige um ja existente.
Os dados do console sao do MySQL configurado; nao apenas memoria do Java.

## Responsabilidades

Console pergunta e mostra mensagens; ProdutoService valida o contexto e
coordena transacoes; ProdutoDao executa SQL. O terminal nao implementa calculos
de compra nem precisa montar SELECT/UPDATE/DELETE.

## ProdutoService: metodos e motivos

- Construtor padrao usa ConexaoBD; FabricaConexao injetavel permite banco de teste.
- consultarLojista reune usuario e complemento e retorna Lojista reconstruido;
  getPerfil sobrescrito aparece na selecao do console.
- cadastrar exige ProdutoRegistro sem ID e retorna a linha salva com ID gerado.
- listar usa paginacao; buscar filtra por lojista e devolve Optional vazio se
  ausente ou de outro lojista. Evita revelar dados fora do contexto escolhido.
- atualizar exige ID e grava so nome/categoria/descricao/preco/peso. O saldo
  recebido pode estar antigo e nao deve ser regravado.
- reporEstoque exige quantidade positiva e usa UPDATE atomico; definirAtivo
  retira/recoloca a venda sem apagar historico.
- excluir usa excluirSemHistorico do DAO, sem apagar compras vinculadas.
- executar exige conexao nova em autoCommit, configura READ_COMMITTED, valida
  lojista e confirma com commit ou reverte com rollback. Fecha a conexao; falha
  de rollback fica como excecao suprimida sem esconder o erro principal.
- carregarLojista exige LOJISTA ativo com complemento comercial a cada operacao.
  validarId restringe a chave a INT positivo. Consulta comum nao bloqueia mudanca
  concorrente de perfil/atividade nem implementa autorizacao de sessao.

## ProdutoDao: excluirSemHistorico

Exige transacao explicita e confere propriedade. SELECT FOR UPDATE bloqueia
o produto antes de conferir item_pedido. Se houver item, recusa e orienta
desativar; caso contrario, usa o DELETE existente. Nao depende de CASCADE.
A FK protege integridade; metadados consultados confirmaram RESTRICT entre
item_pedido e produto. O metodo antigo excluir continua disponivel, mas o
terminal usa o protegido. Chamadas diretas ao DAO nao autenticam um ator.
A disputa inserir item versus excluir nao foi exercitada nesta suite: depende
do bloqueio/FK, com rollback em falhas. Nao alegar teste concorrente desse caso.

## ConsoleNexus: metodos

- main monta service e entrada/saida UTF-8; nao conecta ate uma operacao.
- Construtor recebe Reader/PrintWriter para testes de roteiro. executar repete
  menu, trata argumentos/estados invalidos e SQLException, encerra com 0 ou EOF.
- selecionarLojista so troca o contexto apos consulta bem-sucedida.
- lerCadastro coleta todos os campos antes de salvar. Saldo/atividade so sao
  pedidos no cadastro; na edicao sao preservados e nao gravados pelo DAO.
- listar pede deslocamento; buscar restringe resultado ao lojista escolhido.
- atualizar consulta antes e pede cadastro completo; remover exibe produto e
  exige s/n. Regra de historico fica no service/DAO, nao so na interface.
- mostrar imprime somente produto, sem hash/senha; resultado distingue sucesso
  de nenhuma linha alterada, sem tratar false como operacao concluida.
- ler usa linha inteira e detecta fim de entrada; inteiro/decimal traduzem erro
  de formato para portugues. simNao exige s/n, sem interpretar texto qualquer
  como confirmacao. Entrada incompleta nao efetiva a escrita.

Erros SQL exibem SQLState, sem stack trace ou credenciais. Nao ha repeticao
automatica de escrita apos erro de conexao: resultado do commit pode ser incerto;
consultar antes de repetir a operacao.

## Verificacao e limites

Compilacao estrita, RunChecks e higiene aprovados. Suite JDBC ampliada: 89
verificacoes (60 de regressao e 29 novas de service/menu), em banco UUID
descartavel com DDL/FKs reais, removido ao final. Dados originais nao alterados.

Inclui CRUD, separacao entre lojistas, preservacao de saldo/atividade no UPDATE,
reposicao, texto SQL literal, bloqueio de remocao historica, transacao obrigatoria,
roteiro de console, cancelamento/confirmacao de DELETE, virgula decimal,
entrada invalida e EOF sem conexao desnecessaria. Apoio em
tests/local/CompraServiceIntegrationCheck.java, ignorado pelo Git.

Faltam os outros menus/CRUDs, cadastros transacionais e services completos de
entrega. Nao foram adicionadas exclusoes indiscriminadas em pagamentos/compras.
Continuam pendentes UML, disponibilizacao de testes e pacote final. Sem commit/push.
PDF local de estudo: 26-ProdutoService-Console.pdf.

## Para estudar

1. Por que edicao de cadastro nao pode regravar estoque antigo?
2. Qual a diferenca entre remover e desativar um produto ja vendido?
3. Por que informar ID de lojista nao significa autenticar esse usuario?
