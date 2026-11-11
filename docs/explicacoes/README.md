# Explicacoes para estudar o Nexus Dispatch

Esta pasta reunira as explicacoes solicitadas ao longo do desenvolvimento.
Os textos sao material de estudo: descrevem o codigo atual e distinguem o que
ja funciona do que ainda sera implementado.

## Ordem de leitura

1. [Como as partes se conectam](01-visao-do-codigo.md)
2. [LojistaRegistro e LojistaDao](02-lojista.md)
3. [ProdutoRegistro e ProdutoDao](03-produto.md)

4. [EntregadorRegistro e StatusOperacaoEntregador](04-entregador.md)

Referencia complementar: [UsuarioDao](../usuario-dao.md).

Cada novo assunto tera um arquivo numerado, com finalidade, conceitos usados,
fluxo de execucao, validacoes e limites. Os testes pessoais ficam em tests/local,
ignorados pelo Git; a documentacao Markdown leve pode acompanhar o projeto.


## Regra acordada para arquivos Java
Sempre que um arquivo Java for enviado ao Guilherme, fornecer codigo comentado e material de ensino em PDF nesta pasta, com finalidade, explicacao dos metodos, exemplos, regras e limites. Apresentar o codigo antes de implementar no repositorio quando essa sequencia for solicitada. PDFs sao materiais locais ignorados pelo Git.


- [EntregadorDao: integracao e limites dos testes](05-entregador-dao-integracao.md)

- [Aceite transacional e testes concorrentes](06-aceite-transacional.md). PDFs de ensino: 06-entregador-dao-aceite.pdf, 07-ordem-entrega-dao.pdf e 08-entrega-service.pdf.

- [Pedido mestre: registro, estados e proximas etapas](10-pedido-mestre-registro.md). PDFs de ensino: 09-status-pedido-mestre.pdf e 10-pedido-mestre-registro.pdf.

- [PedidoMestreDao: operacoes, transacoes e limites](11-pedido-mestre-dao.md).

- [Comanda: registro, estados, DAO e verificacoes](12-comanda-lojista.md).

- [CompraService: checkout, recusa e nova tentativa](22-compra-service.md). PDF local: 22-CompraService.pdf.

- [Usuario, perfis e lojista: reconstituicao e polimorfismo](23-usuario-perfis-lojista.md). PDF local: 23-Usuario-Perfis-Lojista.pdf.

- [Entregador, Loja, Produto e Oferta: alinhamento JDBC](24-entregador-loja-produto-oferta.md). PDF local: 24-Entregador-Loja-Produto-Oferta.pdf.

- [Compra, Pedido, ItemPedido e OrdemEntrega; resumo geral das revisoes](25-compra-pedido-item-entrega.md). PDF local: 25-Revisao-Compra-Pedido-Item-Entrega.pdf.

- [ProdutoService e primeiro console JDBC](26-produto-service-console.md). PDF local: 26-ProdutoService-Console.pdf.

- [Cadastros, senhas e primeiro uso com banco vazio](27-cadastros-senhas-primeiro-uso.md). PDF local: 27-Cadastros-Senhas-Primeiro-Uso.pdf.

- [UsuarioService: entrega focada, metodos e testes](28-usuario-service.md). Os outros services iniciados aguardam revisao separada.

- [LojistaService: consulta, edicao comercial e testes](29-lojista-service.md). EntregadorService ainda aguarda entrega separada.

- [EntregadorService: cadastro operacional, disponibilidade e concorrencia](30-entregador-service.md). Conclui a sequencia de entregas individuais desta sessao.

- [EntregaService: malha, rota agrupada, coleta, conclusao e desistência](31-entrega-service.md).
  Atualizacao de 06/11/2026; PDF local 31-EntregaService.pdf e teste JDBC reproduzivel.

- [CompraService — consultas, histórico e cancelamento](32-compra-consulta-cancelamento.md). PDF local `32-CompraService-Cancelamento.pdf`.

- [Terminal por perfil e roteiro da banca](33-terminal-banca.md). PDF local `33-ConsoleNexus.pdf`.
