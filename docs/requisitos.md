# Requisitos Java e estado da entrega

Referencia: escopo V4 fornecido pelo usuario, secoes 5.3, 8.3 e 9.
Consolidacao: 03/11/2026. UML existente, ainda indisponivel para conferencia.

## Matriz de atendimento

| ID | Requisito | Estado atual | Falta concluir |
| --- | --- | --- | --- |
| J01 | Classes principais correspondentes as tabelas | Primeira rodada concluida para as nove tabelas de negocio: registros e reconstituicao de dominio/visoes. Dois logs sem classes. | Integrar ao terminal/services, definir papel dos logs e destino dos modelos locais sem persistencia; conferir UML. |
| J02 | Encapsulamento, construtores, getters/setters | Presentes; records sao projecoes imutaveis. | Revisar por classe e justificar ausencia de setters de identidade/credenciais. |
| J03 | Heranca com sobrescrita | Usuario e quatro perfis com getPerfil sobrescrito. | Demonstrar no terminal alem dos testes. |
| J04 | Polimorfismo e colecoes | toRegistro usa getPerfil sobrescrito; listas/mapas existentes. | Integrar ao fluxo final e conferir UML. |
| J05 | Conexao real com DriverManager/JDBC | ConexaoBD e MySQL configurados. | Demonstrar em instalacao reproduzivel. |
| J06 | DAO por entidade principal com SELECT/INSERT/UPDATE/DELETE | Nove DAOs, CRUD parcial; matriz abaixo. | Completar operacoes pertinentes com regras de integridade. |
| J07 | PreparedStatement e try-with-resources | Presentes; alguns comandos concatenam identificadores internos fixos. | Conferir todos os comandos e preferir SQL fixo para atender literalmente ao escopo. |
| J08 | Aplicacao adiciona/lista/busca/remove dados reais | Parcial: CRUD de produtos e cadastro/listagem de clientes, lojistas e entregadores pelo console. | Completar CRUD de perfis/entidades, compra e entrega no menu e demonstrar roteiro completo. |
| J09 | Cada classe/metodo comentado e explicado | Parcial, cobertura desigual. | Revisar fontes; PDF nao substitui comentarios no codigo. |
| J10 | UML fiel ao codigo/tabelas | Conferencia adiada; usuario informa UML pronto. | Comparar quando disponivel, sem recriar agora. |
| J11 | Codigo Java digital/impresso e defesa | Pendente como pacote final. | Selecionar versao, material e roteiro de demonstracao. |

## CRUD atual

X indica metodo existente, nao fluxo pronto no terminal.

| DAO | SELECT | INSERT | UPDATE | DELETE fisico |
| --- | --- | --- | --- | --- |
| UsuarioDao | X | X | X | X |
| LojistaDao | X | X | X | X |
| ProdutoDao | X | X | X | X |
| EntregadorDao | X | X | X | Pendente |
| PedidoMestreDao | X | X | X | Pendente |
| ComandaLojistaDao | X | X | X | Pendente |
| ItemPedidoDao | X | X | Pendente | Pendente |
| PagamentoDao | X | X | X | Pendente |
| OrdemEntregaDao | X | X | X | Pendente |

Desativacao/cancelamento por UPDATE nao deve ser declarado como atendimento
literal de DELETE sem alinhamento com o professor. Nao acrescentar exclusao
irrestrita de pagamentos/historico: definir operacoes seguras, FKs e entidades
principais da demonstracao. Logs nao sao automaticamente cadastros editaveis.

## Decisoes do Nexus e limites

Regras escolhidas pelo usuario, detalhadas em regras-de-negocio.md:
compra mult lojista com uma comanda por lojista; taxa configuravel sobre produtos
da compra (percentual ainda indefinido); comissao separada; HALF_UP; baixa de
estoque na confirmacao; recusa preserva pedido CRIADO; retry reenvia carrinho e
verifica totais/saldo; aceite exclusivo pelo service.

Pagamento simulado nao significa persistencia simulada: pedido/pagamento/estoque
sao gravados no MySQL. Cobranca bancaria real nao e requisito explicito.
API web, site, login completo e malha avancada tambem nao sao minimos explicitos
do documento. Se adotados pelo Nexus, precisam de justificativa e testes.
Perfil de objeto nao comprova autenticacao nem autorizacao.

DDL/modelo logico do grupo sao dependencias para Java. Nao redesenhar banco/DER
nesta revisao. Sem o SQL oficial, instalacao do zero nao esta comprovada.

## Evidencia e reproducibilidade

Ultimas execucoes registradas: compilacao completa UTF-8 -Xlint:all -Werror,
RunChecks incluindo 34 checks novos de reconstituicao, higiene aprovada e
CompraServiceIntegrationCheck com 114 checks em banco UUID descartavel,
incluindo rollback e disputa pela ultima unidade. Nao equivalem a auditoria total.
CorrespondenciaComercialCheck acrescenta 97 verificacoes aos checks de dominio.
CompraPersistidaCheck acrescenta 172 verificacoes do grafo compra/comanda/itens/ordem.
Suite JDBC em tests/local e ignorada pelo Git: decidir disponibilizacao dos
testes de entrega sem alterar silenciosamente a selecao de arquivos acordada.
