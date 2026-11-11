# ProdutoRegistro e ProdutoDao

Implementacao local de 26/10/2026 baseada no SHOW CREATE TABLE produto do MySQL.

## Por que existe ProdutoRegistro

No SQL, produto guarda lojista, categoria, descricao, preco, peso, estoque e
status. No dominio Java anterior, Produto referencia Loja e OfertaProduto guarda
preco/estoque. ProdutoRegistro representa a linha real do banco sem alterar essas
classes. A futura integracao dos services devera resolver essa diferenca.

## Campos e validacoes

| Campo | Regra |
|---|---|
| id | Long null antes da insercao; depois ID positivo na faixa INT |
| idLojista | ID positivo na faixa INT; FK exige lojista existente |
| nome | Obrigatorio, com letra, ate 120 pontos de codigo Unicode |
| categoria | Obrigatoria, ate 50 pontos de codigo; informar OUTROS quando apropriado |
| descricao | Opcional; branco vira null; limite de 65535 bytes UTF-8 de TEXT |
| precoUnitario | BigDecimal maior que zero, ate 99999999.99, escala 2 |
| pesoKg | BigDecimal maior que zero, ate 999.999, escala 3 |
| estoqueDisponivel | int nao negativo |
| ativo | Boolean que preserva NULL permitido pela tabela |

Valores decimais nao sao arredondados silenciosamente. O nome segue a regra
Java existente de possuir uma letra. A descricao segue TEXT do SQL, diferente
do limite de 1000 caracteres da antiga entidade Produto; os services deverao
estabelecer o contrato de entrada comum ao integrar as interfaces.
Categoria e status sao informados explicitamente na insercao. Para um cadastro
comum, o chamador pode fornecer OUTROS e true; null no status permanece null.

## Operacoes

- inserir: recebe record com ID null e retorna o ID gerado. Nao muda o record original.
- buscarPorId: devolve Optional; resultado inexistente e diferente de erro JDBC.
- listarPorLojista: filtra por lojista, ordena por ID, pagina de 1 a 100 registros.
- atualizarCadastro: altera nome, categoria, descricao, preco e peso. Mantem
  lojista, estoque e status, evitando sobrescrever valores de uma consulta antiga.
- definirAtivo: modifica status separadamente, limitado ao ID e lojista informados.
- baixarEstoque: subtrai quantidade positiva somente se o produto estiver ativo
  e houver saldo suficiente. Condicao e alteracao ficam no mesmo UPDATE.
- reporEstoque: soma quantidade positiva sem ultrapassar Integer.MAX_VALUE.
- excluir: remove somente o produto do lojista informado; FKs podem impedir.

Todas as alteracoes usam parametros vinculados. Informar idLojista limita a linha
afetada, mas nao comprova permissao do usuario. O service deve autorizar a operacao.
As atualizacoes retornam se o driver reportou linha afetada. Baixa false pode
significar ausencia, lojista divergente, inatividade ou saldo insuficiente.

## Exemplo de fluxo

```java
try (Connection conexao = ConexaoBD.conectar()) {
    ProdutoDao produtos = new ProdutoDao(conexao);
    Optional<ProdutoRegistro> resultado = produtos.buscarPorId(10L);
    resultado.ifPresent(produto -> System.out.println(produto.nome()));
}
```

Quem abre fecha a Connection. Os DAOs fecham apenas seus comandos/resultados.
Para uma compra, o futuro service devera coordenar baixa, pedido e itens na mesma
transacao. O UPDATE condicional nao implementa sozinho checkout nem idempotencia.

## Testes e requisitos atendidos

53 verificacoes locais passaram: limites Java, decimais, TEXT em bytes, CRUD,
parametros literais, FK para lojista, restricao de exclusao, paginacao, nulos,
estoque, produto inativo, overflow e rollback dos cadastros relacionados.
O teste cria banco nexus_test_produto_<UUID> com DDL copiado e remove ao terminar.
Nao copia dados reais nem triggers. Uma tabela minima simula referencia de item
para testar RESTRICT; nao testa o fluxo real de pedidos nem concorrencia simultanea.

Testes e executor estao em tests/local/, ignorado pelo Git:

```powershell
./tests/local/RunProdutoCheck.ps1 -SomenteModelo
./tests/local/RunProdutoCheck.ps1
```

A segunda opcao requer CREATE/DROP DATABASE. Ambas exigem Java 25 e os JARs do
pom.xml no repositorio Maven local. Nenhum service vazio foi criado e nao houve
commit ou envio automatico. Proximo DAO previsto: EntregadorDao.
