# Como as partes se conectam

O caminho planejado e: terminal ou interface -> service -> DAO -> MySQL.
O terminal ainda sera construido, e os services ainda nao coordenam o fluxo.

## O papel de cada arquivo

- Record: transporta dados. O construtor pode rejeitar valores invalidos.
- DAO: prepara SQL, envia parametros, le resultados e devolve objetos Java.
- Service: aplicara regras entre entidades, permissoes e transacoes.
- ConexaoBD: abre a conexao JDBC usando as configuracoes locais.

UsuarioDao, LojistaDao e ProdutoDao ja possuem operacoes. As entidades antigas
como Produto, OfertaProduto e Loja continuam representando regras em memoria;
nao foram automaticamente integradas ao esquema atual por criar os novos records.

## Conceitos que aparecem nos DAOs

| Conceito | Significado neste projeto |
|---|---|
| Connection | Uma conexao aberta com o banco, compartilhavel por varios DAOs |
| PreparedStatement | Comando SQL com parametros ?, vinculados por setLong, setString etc. |
| ResultSet | Resultado da consulta; next() avanca para a proxima linha |
| Optional | Deixa explicito se a busca encontrou um registro ou nao |
| BigDecimal | Numero decimal preciso para preco, peso e porcentagem |
| record | Objeto com componentes imutaveis e acessores gerados pelo Java |
| try-with-resources | Fecha comandos e resultados mesmo quando ocorre uma excecao |
| SQLException | Comunica uma falha de acesso ou de restricao do banco |
| commit e rollback | Confirmam ou desfazem uma transacao, sob controle do chamador |

Exemplo de leitura: buscarPorId(10) valida o ID, prepara SELECT, vincula o 10,
executa a consulta e transforma a linha em record. Se nenhuma linha existir,
retorna Optional.empty(). Falha de banco nao deve ser confundida com ausencia.

Nao confundir validacao com autorizacao: aceitar um ID valido nao prova que o
usuario autenticado pode alterar aquele cadastro. Essa regra pertence ao service.
