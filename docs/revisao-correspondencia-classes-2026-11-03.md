# Revisao de correspondencia das classes Java

Terceira atualizacao: Compra/Pedido/ItemPedido/OrdemEntrega reconstituem registros
SQL com validacao de identidade, totais e estados preservados. Ausencias do SQL
nao sao inventadas. Ver guia 25 para detalhes e resumo de toda a revisao.
Primeira rodada de classes concluida; proxima etapa e matriz CRUD/terminal.
Nao significa migracao integral dos services nem conclusao dos requisitos.

Segunda atualizacao: Entregador reconstituido com estado/complemento reais;
Loja e visao de lojista, Produto preserva registro comercial e Oferta JDBC
consulta o mesmo snapshot. Alteracoes comerciais dessas visoes exigem DAO/
service e nova leitura. Ver guia 24 para contratos e limites. Listas abaixo
mantem o diagnostico inicial; proxima revisao e compra/pedido/itens/entrega.

## Atualizacao posterior ao levantamento

Primeira revisao implementada em Usuario, Consumidor, Administrador e Lojista:
reconstituicao a partir dos registros JDBC, identidade/perfil validados e
preservacao de documento, data e atividade. Lojista compoe usuario e complemento
com IDs iguais. getPerfil foi sobrescrito nos quatro perfis, incluindo Entregador.
Detalhes e limites em explicacoes/23-usuario-perfis-lojista.md. Os apontamentos
abaixo descrevem o estado anterior a essa revisao, nao devem ser tratados como
lista integral de pendencias atuais. Ainda faltam reconstituicao especifica de
Entregador, revisao de Loja e integracao do menu/fluxos com a hierarquia.

Data: 03/11/2026. Escopo: Java/POO; UML existente fora desta pasta fica para
conferencia posterior. Nenhuma classe de producao ou tabela foi alterada.

## Evidencia e limites

Consulta somente de metadados no MySQL configurado: SHOW TABLES e SHOW COLUMNS.
Nao foram lidos cadastros. Foram comparados campos de model, imports dos DAOs
e usos nos services. Esta revisao nao valida triggers, defaults, todas as FKs
nem os arquivos DER/UML do grupo. O DDL oficial nao esta versionado nesta pasta.

## Correspondencia da camada JDBC

| Tabela | Representacao Java | Avaliacao |
| --- | --- | --- |
| usuario | UsuarioRegistro | Projecao de consulta: inclui documento, perfil, data e atividade; omite senha_hash intencionalmente. UsuarioDao recebe hash separadamente no cadastro. |
| lojista | LojistaRegistro | Campos correspondentes; id representa id_lojista. |
| entregador | EntregadorRegistro | Campos correspondentes; cnh e numero, nao categoria. |
| produto | ProdutoRegistro | Campos correspondentes, incluindo lojista, preco, peso, estoque e atividade. |
| pedido_mestre | PedidoMestreRegistro | Campos correspondentes; valorFretes e calculado pela diferenca, nao coluna. |
| comanda_lojista | ComandaLojistaRegistro | Campos correspondentes; parte da compra por lojista. |
| item_pedido | ItemPedidoRegistro | Campos correspondentes; FK aponta para comanda, nao diretamente para pedido mestre. |
| pagamento | PagamentoRegistro | Campos correspondentes; uma linha por pedido segundo a chave unica consultada. |
| ordem_entrega | OrdemEntregaRegistro | Campos correspondentes; relacao com comanda, dados financeiros e datas. |
| auditoria_pedido | Sem classe localizada | Avaliar representacao de leitura conforme responsabilidade dessa tabela. |
| auditoria_sistema | Sem classe localizada | Avaliar representacao de leitura conforme responsabilidade dessa tabela. |

Enums persistentes conferidos: PerfilUsuario, TipoVeiculo,
StatusOperacaoEntregador, StatusPedidoMestre, StatusComanda, StatusOrdemEntrega,
MetodoPagamento e StatusPagamento correspondem aos valores mostrados no banco.
Long/long sao maiores que INT: os registros validam faixa; BigDecimal representa
DECIMAL; Boolean e datas opcionais preservam NULL onde o contrato permite.

Os records sao classes Java com campos privados finais e acessores gerados.
Nao sao uma incompatibilidade tecnica com JDBC. Como o escopo pede tambem
construtores/getters/setters, e preciso tornar claro quais sao DTOs imutaveis e
quais sao as classes principais de dominio. Nao converter tudo automaticamente
nem criar setters sem necessidade apenas por causa dessa separacao.

## Classes de dominio anteriores

| Classe | Relacao conceitual | Diferenca e impacto |
| --- | --- | --- |
| Usuario | usuario | Tem telefone sem coluna correspondente; falta documentoFiscal, perfil, dataCadastro e atividade. O ID nao possui reconstituicao. Nao pode ser reconstruido fielmente pelo DAO atual. |
| Consumidor | usuario com perfil CLIENTE | Nao existe tabela consumidor. Exige Endereco estruturado sem persistencia correspondente no schema consultado. |
| Administrador | usuario com perfil ADMIN | Nao existe tabela administrador; herda dados de Usuario. Nao tem sobrescrita de comportamento. |
| Lojista | usuario LOJISTA + lojista | Contem apenas dados herdados, enquanto nome fantasia, comissao, bairro e origem ficam no registro JDBC. |
| Entregador | usuario ENTREGADOR + entregador | Categoria da CNH, PBT e passageiros existem no dominio, nao no SQL. Numero da CNH e bairro estao no registro; boolean disponivel nao representa os tres estados SQL. |
| Loja | dados parcialmente em lojista | Nao existe tabela loja. CNPJ separado, endereco estruturado e aberta nao possuem o mesmo desenho no SQL atual. |
| Produto | produto | Tem loja/nome/descricao; preco e estoque ficam em OfertaProduto. SQL concentra esses dados em produto e usa id_lojista. Categoria, peso e atividade tambem precisam de correspondencia. |
| OfertaProduto | parte de produto | Nao existe tabela oferta_produto. Historico EntradaEstoque e apenas em memoria. Nao criar DAO de tabela inexistente. |
| Compra | pedido_mestre | Mantem consumidor, pedidos e endereco historico em memoria; SQL guarda cliente/totais/estado e nao esse endereco. Falta reconstituicao. |
| Pedido | comanda_lojista | No dominio antigo significa pedido por loja; SQL chama isso de comanda. Estados diferentes; faltam comissao/repasse e reconstituicao. |
| ItemPedido | item_pedido | Aponta para oferta e preserva nome/descricao historicos; SQL aponta para produto/comanda e preserva preco/observacao. Nao recupera nomes/descricoes historicos inexistentes no banco. |
| OrdemEntrega | ordem_entrega | Aponta para Pedido, tem endereco e datas de criacao/aceite sem colunas equivalentes. Estados antigos nao coincidem com StatusOrdemEntrega. |
| Endereco | objeto auxiliar | Sem tabela endereco; id sem persistencia. Nao prometer recuperacao do endereco do consumidor pelo schema atual. |
| Bairro | objeto auxiliar | Sem tabela bairro; banco usa textos de bairro. id e cidade/atendido nao possuem persistencia propria. |

Compra.EnderecoEntrega e OfertaProduto.EntradaEstoque sao tipos auxiliares.
ItemCarrinho/Resultado do CompraService sao entrada/saida de uma operacao;
Linha/Carrinho sao detalhes internos. Nao precisam de tabela por serem classes.
Enums, DAOs e services tambem nao exigem tabelas correspondentes.

## Diagnostico

Ha dois conjuntos de representacoes parcialmente desconectados. Os DAOs usam
os registros, e CompraService usa esse fluxo JDBC. As classes antigas conservam
regras/testes em memoria, mas nao sao reconstituidas pelos DAOs atuais. Portanto,
os testes antigos nao provam que a hierarquia orientada a objetos esta integrada
ao fluxo persistente. Ter dominio + DTO nao e errado; falta integrar ou reduzir
de forma explicita a duplicacao, com uma fonte unica para cada regra.

StatusPedido e StatusEntrega sao estados do dominio anterior. Nao gravar seus
names diretamente nas tabelas atuais; nao criar traducao automatica sem verificar
as transicoes, pois os estados nao possuem correspondencia completa.

## Ordem recomendada de correcao

1. Usuario: alinhar dados persistidos, separar projecao publica do hash e definir
   reconstituicao com ID/estado salvos. Preservar a hierarquia de perfis e preparar
   comportamento util para sobrescrita/polimorfismo.
2. Consumidor/Administrador/Lojista/Entregador: definir como perfis e complementos
   SQL formam os objetos, sem inventar tabelas ou campos. Resolver lacunas de
   endereco/veiculo explicitamente; nao descartar validacoes sem avaliar impacto.
3. Produto/OfertaProduto/Loja: definir representacao principal de produto ligada
   ao lojista e quem concentra preco/estoque. Evitar saldos independentes.
4. Compra/Pedido/ItemPedido: alinhar nomes, relacionamentos e estados com
   pedido_mestre/comanda_lojista/item_pedido; recuperar historico real.
5. OrdemEntrega: alinhar dados e estados; manter efeitos conjuntos nos services.
6. Auditorias: verificar se sao mantidas pelo banco ou aplicacao antes de criar
   classes/consultas. Nao acrescentar UPDATE/DELETE de logs indiscriminadamente.

Nao remover classes, renomear arquivos ou modificar o banco nesta etapa de
levantamento. Mudancas posteriores devem preservar os fluxos JDBC existentes e
ter verificacoes de reconstituicao e de comportamento pertinentes. Depois,
conferir a correspondencia com o UML ja produzido pelo grupo.
