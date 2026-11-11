# LojistaRegistro e LojistaDao

Implementacao local de 26/10/2026. O SQL da main e a tabela lojista do MySQL
foram conferidos antes da implementacao. Nenhuma alteracao do banco real foi feita.

## Para entender os dois arquivos

LojistaRegistro transporta os dados e valida seus valores. E um record imutavel:
possui acessores como id() e nomeFantasia(), sem setters. Para modificar o
cadastro, criamos outro registro com o mesmo ID e os novos dados.

LojistaDao executa SQL e transforma cada linha consultada em LojistaRegistro.
Ele recebe uma Connection pronta, fecha seus comandos e resultados e devolve
o resultado ao chamador. Nao fecha a conexao nem executa commit ou rollback.

| Campo Java | Coluna SQL | Validacao |
|---|---|---|
| id | id_lojista | Positivo, ate Integer.MAX_VALUE; mesmo ID de usuario |
| nomeFantasia | nome_fantasia | Obrigatorio, ate 120 pontos de codigo Unicode |
| taxaComissaoPct | taxa_comissao_pct | BigDecimal entre 0 e 100, representavel com duas casas |
| bairroOperacao | bairro_operacao | Obrigatorio, ate 100 pontos de codigo Unicode |
| enderecoOrigem | endereco_origem | Obrigatorio, ate 255 pontos de codigo Unicode |

Textos recebem strip() e nao sao truncados. A comissao nao e arredondada:
12.5 e 12.500 viram 12.50, mas 12.345 e rejeitado. O valor 12.50 significa
12,5%, nao um valor em reais. BigDecimal deve ser criado a partir de texto.
Os campos comerciais nao exigem conter letras: essa regra nao existe no DDL.

## Operacoes do DAO

- inserir(registro): grava o complemento usando o ID do usuario ja cadastrado.
- buscarPorId(id): retorna Optional com registro ou Optional.empty().
- listar(limite, deslocamento): pagina de 1 a 100 linhas, ordenada por ID.
- atualizar(registro): altera os quatro campos comerciais, preservando o ID.
- excluir(id): remove apenas o complemento. Nao remove usuario; FKs podem impedir.

Atualizacao e exclusao retornam se o driver reportou linha afetada. A atualizacao
segue a configuracao de contagem do Connector/J, assim como UsuarioDao.
Listagens incluem usuarios ativos ou inativos; nao implementam autorizacao.

## Leitura de buscarPorId passo a passo

1. validarId rejeita um identificador fora da faixa antes de executar SQL.
2. prepareStatement prepara SELECT com o parametro ?.
3. setLong(1, id) vincula o ID ao primeiro parametro, sem concatenar entrada.
4. executeQuery executa a consulta e retorna um ResultSet.
5. next avanca o cursor para a primeira linha, caso exista.
6. mapear le as colunas e constroi LojistaRegistro; getBigDecimal preserva o decimal.
7. Optional informa se um cadastro foi encontrado. try-with-resources fecha os recursos.

Erros JDBC sao propagados como SQLException. Se um registro antigo do banco
violar as validacoes Java (por exemplo, texto em branco), mapear reporta erro de
leitura com SQLState 22000. Falha de leitura nao e confundida com cadastro ausente.

## O que continua sendo responsabilidade do service

A FK garante usuario existente, mas nao garante perfil LOJISTA. O futuro service
deve verificar perfil e permissoes, decidir a comissao e coordenar UsuarioDao e
LojistaDao na mesma transacao. Uma falha deve provocar rollback pelo chamador.
ON DELETE CASCADE em usuario remove o complemento lojista; dependencias de outras
tabelas podem bloquear a exclusao. Nao expor essas operacoes como menu sem regras.

## Verificacoes realizadas

Passaram 30 verificacoes em memoria do registro e 36 verificacoes JDBC do DAO.
Os 12 checks anteriores tambem passaram. O teste JDBC criou um banco isolado
nexus_test_lojista_<UUID>, copiou apenas DDL e removeu o banco ao final. Cobriu
CRUD, duplicidade, FK real para usuario, CHECK da comissao, CASCADE e rollback.
RESTRICT foi verificado com uma tabela dependente minima, nao com produtos reais.
Triggers e integracao do fluxo completo permanecem fora do escopo.

Por solicitacao do usuario, os testes novos, comentarios didaticos e seu executor
ficam em tests/local/, ignorado pelo Git. Os testes anteriores em tests/manual
permanecem versionados. RunChecks.ps1 nao depende da pasta local.

Para estudo local sem MySQL: ./tests/local/RunLojistaDaoCheck.ps1 -SomenteModelo.
Para os testes JDBC locais: ./tests/local/RunLojistaDaoCheck.ps1 (requer permissoes
CREATE/DROP DATABASE). Esses arquivos nao estarao presentes em um clone do Git.

Proxima etapa na sequencia dos DAOs: ProdutoDao, apos revisar os campos reais
de produto e a diferenca entre esse esquema e Produto/OfertaProduto em Java.
