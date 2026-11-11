# Primeiro DAO: usuarios

Implementacao de 25/10/2026, tomando o MySQL importado como referencia,
conforme decisao do usuario. A estrutura foi consultada por metadados e
SHOW CREATE TABLE, sem ler os cadastros existentes.

## Por que UsuarioDao

O banco possui usuario, lojista e entregador, mas nao possui bairro ou loja.
Usuario e a base dos perfis e dos pedidos. Implementar BairroDao agora exigiria
criar uma tabela que nao faz parte do banco recebido.

As entidades Java anteriores nao correspondem diretamente ao SQL: Usuario tem
telefone e nao tem documento fiscal; Consumidor exige Endereco. Por isso foi
adicionado UsuarioRegistro, uma representacao imutavel da linha do banco, sem
senha_hash. Os modelos anteriores e suas validacoes permanecem intactos.
Ainda sera necessario integrar esses modelos com os cadastros persistidos;
esta etapa nao implementa automaticamente o cadastro completo de cada perfil.

## Arquivos e responsabilidades

- UsuarioDao: INSERT, SELECT, UPDATE e DELETE com PreparedStatement.
- PerfilUsuario: CLIENTE, LOJISTA, ENTREGADOR e ADMIN, como no ENUM SQL.
- UsuarioRegistro: ID, nome, documento fiscal, email, perfil, data e status.
- `UsuarioDaoIT` verifica o DAO no MySQL em um catálogo descartável.
- `RunUsuarioDaoCheck.ps1` é um atalho para executar o cenário JDBC pelo Maven.
- `RunChecks.ps1` é um atalho para executar os testes de domínio em memória.

## Operacoes

| Metodo | Resultado |
| --- | --- |
| inserir(nome, documento, email, hash, perfil) | ID gerado pelo MySQL |
| buscarPorId(id) | Optional com cadastro ou vazio |
| buscarPorEmail(email) | Optional com cadastro ou vazio |
| listar(limite, deslocamento) | Lista imutavel, ordenada por ID |
| atualizarCadastro(id, nome, documento, email) | Indica se o driver reportou linha afetada |
| definirAtivo(id, ativo) | Ativa ou desativa, preservando o historico |
| excluir(id) | Exclusao fisica, sujeita as chaves estrangeiras |

As atualizacoes retornam a contagem reportada pelo driver. Com a configuracao
atual do Connector/J, linhas encontradas contam mesmo quando os valores sao
iguais. Alterar useAffectedRows na conexao pode mudar essa semantica.

## Validacoes

- ID entre 1 e Integer.MAX_VALUE, correspondente ao INT assinado do SQL.
- Nome obrigatorio, com pelo menos uma letra e ate 120 pontos de codigo Unicode.
- Documento obrigatorio, ate 18 pontos de codigo. Nao valida digitos de CPF/CNPJ.
- Email obrigatorio, formato basico e ate 100 pontos de codigo. Nao comprova
  existencia ou propriedade do email.
- Nome, documento e email recebem strip(); valores longos sao rejeitados, nao cortados.
- Hash recebido sem alteracao, com 60 caracteres e sem espacos, conforme CHAR(60).
  Essa verificacao nao comprova algoritmo ou seguranca. A geracao e verificacao
  da senha ficam para o servico de autenticacao; o DAO nao recebe senha original.
- Perfil obrigatorio, restrito ao enum.
- Listagem entre 1 e 100 registros por chamada, com deslocamento nao negativo.
- Unicidade de email/documento e garantida pelos indices do MySQL, inclusive
  em concorrencia. SQLException e propagada ao servico, sem imprimir dados.

status_ativo e data_cadastro permitem NULL no banco; UsuarioRegistro preserva
esses valores como null. Consultas incluem usuarios inativos e nao retornam hash.
Nao ha autenticacao nem autorizacao implementadas nesta camada. O futuro servico
devera controlar quem pode cadastrar perfis, consultar dados ou excluir usuarios.

## Conexao e transacao

O DAO recebe uma Connection e fecha somente PreparedStatement e ResultSet.
Quem abre a conexao deve fecha-la. O DAO nao chama commit, rollback ou setAutoCommit:
assim, varios DAOs poderao participar da mesma transacao.

```java
try (Connection conexao = ConexaoBD.conectar()) {
    UsuarioDao usuarios = new UsuarioDao(conexao);
    Optional<UsuarioRegistro> cadastro = usuarios.buscarPorId(1L);
}
```

Para gravacoes coordenadas, o servico deve desabilitar autoCommit, executar as
operacoes e confirmar com commit, ou fazer rollback em caso de falha.
Cadastrar LOJISTA ou ENTREGADOR em usuario ainda exige inserir os dados da tabela
complementar na mesma transacao; isso sera feito nas proximas implementacoes.

## Verificacao realizada

Configure o MySQL descartável conforme [Testes com Maven](../README.md#testes-com-maven). Para rodar apenas o cenário de integração do DAO junto com os testes de domínio, execute no PowerShell.

```powershell
mvn -Pintegracao '-Dit.test=UsuarioDaoIT' verify
```

O cenário usa o esquema de teste versionado e verifica CRUD, geração de IDs, normalização, apóstrofos, os quatro perfis, valores padrão, nulos, paginação, entradas inválidas, limites de tamanho, duplicidade e rollback. O catálogo é removido no encerramento do cenário.

## Proxima etapa

Implementar o cadastro complementar de lojista junto com UsuarioDao em uma
transacao. Depois, integrar produto ao id_lojista real. O banco atual guarda
preco, peso e estoque em produto, diferente do modelo Java com OfertaProduto;
essa adaptacao ainda esta pendente.
