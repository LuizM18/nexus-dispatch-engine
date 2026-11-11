# Cadastros e primeiro uso com banco vazio

03/11/2026. CadastroService, SenhaService e novas opcoes de ConsoleNexus.
Sem migracao SQL, dependencias novas ou insercao de exemplos na base original.

## O problema resolvido

O menu anterior exigia lojista existente, mas a consulta feita na base do
usuario mostrou zero registros nas nove tabelas de negocio. Agora o primeiro
lojista pode ser criado pelo terminal, sem INSERT manual no SQL. O cadastro
retorna o ID e seleciona o lojista automaticamente para cadastrar produtos.

Uma transacao grava usuario e complemento comercial juntos. Se complemento
falhar, rollback remove a insercao do usuario daquela tentativa. Isso evita
cadastros incompletos, como usuario LOJISTA sem sua ficha comercial.

## Como usar

Executar ./scripts/RunConsole.ps1 na raiz com JDK 25 e .env configurado.
Se um terminal antigo ja estiver aberto, sair com 0 e executar a versao nova.

1. Escolher 9, depois 2 para lojista. Informar nome, documento fiscal, email,
   nome fantasia, comissao em percentual, bairro e origem textual.
2. Informar senha de 15 a 128 caracteres e repetir. Espacos nao sao cortados.
   Console nativo oculta a digitacao; entrada redirecionada/IDE pode nao ocultar
   e informa isso. Nunca usar senhas reais em roteiros de teste compartilhados.
3. Aguardar ID confirmado. O lojista fica selecionado automaticamente. Escolher
   2 no menu principal para cadastrar produto (opcao 2 do submenu era lojista).
4. Usar 10 para listar IDs, nomes, perfis e atividade. Documento/email/hash
   nao aparecem nessa listagem. Escolher 1 para selecionar outro lojista ativo.
5. Cliente: 9, depois 1. Entregador: 9, depois 3. Entregador informa tipo de
   veiculo, numero da CNH quando exigido e bairro. Nasce INATIVO explicitamente.
6. Escolher 0 no submenu para voltar, ou no menu principal para sair.

Isso ainda e demonstracao por ID, nao login/autorizacao. Nao existe cadastro
livre de ADMIN pelo menu. Categoria da CNH, PBT, passageiros e endereco estruturado
do cliente continuam fora do SQL e nao recebem valores ficticios.

## CadastroService: contratos

DadosUsuario guarda somente nome/documento/email; senha nao vai em record ou
toString. DadosLojista guarda campos da tabela lojista; DadosEntregador guarda
os campos presentes no SQL. Validacao final fica nos registros e DAOs existentes.

Construtor padrao usa ConexaoBD e SenhaService; construtor com FabricaConexao
permite executar o mesmo codigo em banco descartavel.
cadastrarCliente insere perfil CLIENTE, sem tabela complementar inventada.
cadastrarLojista insere usuario LOJISTA e complemento com o mesmo ID.
cadastrarEntregador insere usuario ENTREGADOR e complemento INATIVO.
Todos devolvem UsuarioRegistro sem senha, apos commit bem-sucedido.

Metodo privado cadastrar calcula o hash antes de abrir transacao, exige conexao
nova e autoCommit inicial, insere usuario e complemento e recupera o cadastro.
Confirma com commit ou reverte SQL/erros de validacao com rollback. Falha de
rollback fica suprimida no erro original. O usuario e validado pelo DAO; um
complemento invalido apos inserir usuario tambem reverte toda a transacao.
Falha de conexao no commit pode ter resultado incerto: nao repetir automaticamente.

listarUsuarios usa conexao dedicada e paginacao do UsuarioDao, sem credenciais.
Ainda nao ha edicao/remocao geral de perfis pelo menu. Essas operacoes exigem
avaliar compras, produtos, ordens e estados operacionais antes de desativar/apagar.

## SenhaService: armazenamento

Usa SecretKeyFactory/PBEKeySpec do JDK com PBKDF2WithHmacSHA256 e 600.000
iteracoes. Nao implementa uma cifra propria nem armazena senha em texto puro.
Parametro de trabalho consultado na referencia primaria OWASP:
https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html

Formato local versionado P2, nao bcrypt: P2$600000$ seguido de salt em Base64URL,
separador $ e derivado em Base64URL. Salt aleatorio de 16 bytes, derivado de
20 bytes; sem padding, total de 60 caracteres compativel com senha_hash CHAR(60).
Formato escolhido para esta aplicacao; nao e serializacao universal de PBKDF2.
Nao afirmar conformidade FIPS por usar o algoritmo. Outro consumidor de senhas
deve conhecer/verificar P2; hashes ficticios ou bcrypt nao sao aceitos como P2.
Banco estava vazio na verificacao, portanto nao houve migracao de credenciais.

gerar valida politica inicial (15 a 128 unidades UTF-16, nao somente espacos),
gera salt com SecureRandom e deriva a chave. Nao modifica o array do chamador.
Senhas iguais geram hashes diferentes por causa do salt.
verificar aceita somente versao/parametros/tamanhos conhecidos e codificacao
canonica; nao aceita iteracoes arbitrarias. Deriva novamente e compara bytes
com MessageDigest.isEqual. Nao existe fluxo de login integrado nesta etapa.
derivar usa 160 bits, limpa PBEKeySpec e traduz indisponibilidade do algoritmo.
Arrays derivados temporarios sao limpos onde utilizados; o hash persistido nao
e apagado. Isso reduz exposicao em memoria, sem prometer eliminacao de toda copia
feita por JVM/entrada/driver. Nao cortar/normalizar silenciosamente a senha.

## ConsoleNexus: novas operacoes

Sobrecarga de construtor recebe CadastroService para testes isolados. Main
usa cadastros reais e System.console quando presente. A leitura de todos os
campos vem da mesma origem (Console ou Reader), evitando buffers concorrentes.

cadastrarPerfil funciona sem lojista escolhido. Valida opcao, coleta dados e
senha/confirmacao, chama o metodo de perfil e so mostra sucesso depois do commit.
Lojista confirmado torna-se o contexto atual. Cliente/entregador nao substituem
o lojista escolhido. Arrays de senha/confirmacao sao limpos no finally, inclusive
em erro ou fim de entrada durante a confirmacao.
listarUsuarios mostra somente ID/nome/perfil/atividade, ate 20 linhas por pagina.
lerSenha usa readPassword no console nativo; fallback Reader preserva espacos e
avisa falta de ocultacao. ler usa a mesma origem para as outras linhas.
executar trata as opcoes 9/10 antes de exigir lojista; erro 1062 mostra mensagem
de duplicidade sem expor consulta, hash ou stack trace. Demais operacoes continuam.

## Testes executados

Compilacao UTF-8 -Xlint:all -Werror, RunChecks e higiene aprovados.
Suite de integracao ampliada: 114 verificacoes, 89 anteriores e 25 novas.
Valida cadastros nos tres perfis, lojista utilizavel pelo ProdutoService,
hash CHAR(60), verificacao correta/incorreta, salt aleatorio, entrada invalida,
rollback por complemento invalido e falha SQL induzida na segunda insercao,
email duplicado, entregador INATIVO, listagem de IDs, roteiro de cadastro de
lojista e primeiro produto e confirmacao de senha divergente sem gravacao.
Verificacao PBKDF2 inclui vetor independente calculado com hashlib do Python.

Teste em banco UUID descartavel com DDL/FKs reais e remocao no finally; nao
gravou exemplos nos cadastros originais. Triggers originais nao sao copiados
automaticamente por SHOW CREATE TABLE; nao afirmar cobertura dos triggers do
grupo. Teste de apoio permanece em tests/local, ignorado pelo Git.

## Proximas pendencias

CRUD restante de perfis/entidades, menus de compra/entrega, integracao de
preparacao/coleta/conclusao/liberacao e cancelamento. Distribuicao da taxa ainda
precisa de definicao. Conferencia do UML, SQL oficial para instalar em outra
maquina e empacotamento final continuam pendentes. Sem novo commit/push.

## Para estudar

1. Por que o usuario e o complemento precisam de um unico commit?
2. Por que a senha nao deve aparecer na ficha retornada ou na listagem?
3. Qual a diferenca entre criar um usuario e autenticar sua identidade?

PDF local: 27-Cadastros-Senhas-Primeiro-Uso.pdf.
