# LojistaService: consulta e edicao comercial

03/11/2026. Uma classe principal por entrega. Nesta etapa somente
LojistaService.java foi alterado entre os fontes de producao; testes e este
material sao apoio. EntregadorService continua rascunho para revisao separada.

## Para que serve

Um lojista tem duas fichas no banco. usuario guarda nome/razao social, documento,
email, perfil e atividade. lojista guarda nome fantasia, comissao, bairro e
endereco de origem. LojistaService consulta e altera a segunda ficha, reunindo
as duas quando precisa devolver o objeto Lojista completo.

Exemplo: mudar a loja de Rua A para Rua B altera o endereco de origem em lojista.
Nao deve trocar email do responsavel, saldo de produto ou valor de uma venda
antiga. Essa separacao e a responsabilidade principal do arquivo.

## Construtores e dependencia de conexao

LojistaService() usa ConexaoBD e as configuracoes externas existentes.
LojistaService(FabricaConexao) recebe uma forma de abrir conexoes. Isso permite
testar no banco descartavel sem mudar a configuracao real. Fabrica null e recusada
imediatamente, com mensagem explicita.

O campo fabrica e final: a referencia de configuracao do service nao e trocada
durante seu uso. O service nao conserva uma Connection aberta entre chamadas.

## buscar(id)

Confere a faixa INT positiva antes de conectar. A consulta ocorre numa transacao
dedicada e bloqueia primeiro usuario, depois lojista. Isso protege a composicao
das duas fichas contra alteracoes concorrentes feitas pelo mesmo protocolo.
Nao chama o banco duas vezes em transacoes independentes para montar um objeto.

Se usuario nao existe, retorna Optional.empty. Optional significa que o resultado
pode estar ausente; ausencia nao e erro de conexao. Se existe com outro perfil,
recusa o ID com IllegalArgumentException. Se e LOJISTA mas falta complemento,
informa cadastro incompleto com IllegalStateException, sem inventar dados.

Com as duas fichas validas, devolve Lojista com mesmo ID e perfil. Nenhuma senha
e consultada. Cadastro inativo pode ser consultado para gestao; isso nao o
autoriza a vender. O objeto devolvido e um snapshot: consultar novamente apos
uma alteracao para obter dados novos.

Leitura com bloqueios pode esperar outra transacao e dura somente ate commit
ou rollback dessa chamada. Nao ha bloqueio mantido depois de devolver o objeto.
Nao afirmar que todos os cruzamentos concorrentes foram testados nesta entrega.

## listar(limite, deslocamento)

Devolve uma pagina de LojistaRegistro, os complementos comerciais, ordenados por
ID. Limite de 1 a 100, deslocamento nao negativo. Valores invalidos sao recusados
antes de abrir conexao. Pagina sem registros e lista vazia, nao excecao.

Exemplo: listar(20, 0) consulta o inicio; listar(20, 20) pula os 20 primeiros.
A lista retornada nao permite adicionar/remover elementos. Cada record tambem
e imutavel; isso protege a ficha recebida, nao impede alteracoes futuras no banco.

Inclui cadastros inativos para gestao. Nao autentica uma pessoa, nao seleciona
somente lojas aptas a vender e nao reconstitui todos os dados de usuario de cada
linha. Perfil/complemento sao conferidos nas buscas individuais e alteracoes.

## atualizar(dados)

Recebe LojistaRegistro, que ja exige ID valido, textos dentro dos limites e
comissao de 0 a 100 representavel com duas casas. Cadastro de porcentagem nao
usa HALF_UP para corrigir entrada invalida: 12.345% e recusado, nao vira 12.35%.
HALF_UP continua aplicado ao calculo monetario da comissao na compra.

Abre transacao e bloqueia usuario antes do complemento. Usuario ausente retorna
false. Perfil diferente de LOJISTA e erro; complemento ausente tambem e erro.
Repetir exatamente os mesmos dados retorna true sem executar UPDATE desnecessario.
Isso e idempotencia: repetir a mesma intencao nao produz alteracoes adicionais.

Com dados diferentes, chama LojistaDao.atualizar. Campos alterados: nome fantasia,
percentual da comissao, bairro e origem textual. Nao altera usuario, produto ou
comanda. Um cadastro inativo pode corrigir dados antes de reativar, mas a edicao
nao muda automaticamente sua atividade.

Exemplo: uma comanda de R$ 20 ja guardou R$ 3 de comissao e R$ 17 de repasse.
Mudar a porcentagem cadastrada para 25% nao transforma essa comissao antiga em
R$ 5. A nova porcentagem vale para calculos futuros; historico salvo e preservado.

## Como a transacao funciona

O apoio TransacaoJdbc, existente da entrega anterior, abre conexao nova, exige
autoCommit inicial e usa READ_COMMITTED. Sucesso confirma commit; SQLException
ou erro de validacao/estado executa rollback. Falha de rollback fica suprimida
no erro original. try-with-resources fecha a conexao em sucesso e falha.

Nao se deve passar conexao com uma transacao em andamento. Cada chamada tem
sua propria transacao; nao e um mecanismo para agrupar varias chamadas de service
numa compra maior. Falha no commit pode ter resultado incerto: consultar antes
de repetir uma escrita apos erro de conexao.

## Exemplo de uso e retorno

LojistaService service = new LojistaService();
service.buscar(id);
service.listar(20, 0);
service.atualizar(new LojistaRegistro(id, nomeFantasia, comissao, bairro, origem));

ID deve ser o consultado/cadastrado, sem copiar um numero ilustrativo para a
base real. buscar devolve Optional de Lojista, listar devolve lista de registros,
atualizar devolve boolean. O terminal trata cada tipo sem declarar sucesso quando
a atualizacao retorna false. Mesmo ID nunca e prova de autenticacao.

## Testes executados

Compilacao completa UTF-8 -Xlint:all -Werror, higiene e git diff --check aprovados.
LojistaServiceIntegrationCheck passou com 41 verificacoes: entrada invalida sem
abrir conexao, consulta, ausencia, perfil incorreto, complemento ausente, paginacao,
lista imutavel, atualizacao e repeticao, isolamento de outro lojista, preservacao
de dados comuns, cadastro inativo, preco/estoque de produto e comissao historica.

Tambem foi induzida falha SQL de UPDATE para verificar rollback e uma nova
tentativa sem bloqueio abandonado. Conexoes foram conferidas como fechadas em
sucesso e erro. Teste em banco UUID descartavel com DDL/FKs reais, removido no
finally. Nenhum cadastro original foi lido para o fixture ou alterado.

Teste local em tests/local/LojistaServiceIntegrationCheck.java, ignorado pelo Git.
Triggers originais nao sao copiados automaticamente por SHOW CREATE TABLE.
Nao houve teste novo concorrente, teste de todo o terminal nem auditoria completa.
Somente compilar outros services nao significa que eles estejam entregues.

## Limites e proximo arquivo

Cadastro inicial continua em CadastroService. Dados comuns e atividade continuam
em UsuarioService. Nao ha DELETE fisico, troca de perfil, senha, novo banco,
login ou autorizacao de sessao neste arquivo. O menu 12 ja tinha rascunho de
integracao; as telas nao foram o foco nem declaradas integralmente testadas aqui.
Nenhum outro fonte de producao foi alterado nesta entrega. Sem commit/push.

Proximo arquivo separado: EntregadorService.java.

## Questoes para estudo

1. Por que uma alteracao de comissao nao deve mudar comandas antigas?
2. Qual a diferenca entre Optional vazio, false e uma excecao nesses metodos?
3. Por que editar cadastro de lojista inativo nao deve reativa-lo automaticamente?

PDF local: 29-LojistaService.pdf.
