# Rascunho do proximo commit - NAO ENVIAR AINDA

Titulo proposto:
feat(persistencia): integrar DAOs de pedidos, comandas, itens e pagamentos

Corpo proposto (revisar conforme entrega efetivamente concluida):
- Integrar registros, enums e DAOs do fluxo de compra por lojista.
- Coordenar escritas em transacoes com rollback integral.
- Preservar historico e validar estados e valores monetarios.

Estado atual: arquivos de pedido mestre e comanda presentes localmente.
PedidoMestreDao.inserir retorna o registro com ID/data; atualizarStatus e o nome local.
StatusComanda mantem permiteTransicaoPara por ser usado pelo ComandaLojistaDao.
Cancelamento permitido antes de qualquer coleta, inclusive AGUARDANDO_COLETA.
O service ainda deve garantir verificacao global, concorrencia e idempotencia.

Antes de liberar o commit:
- Finalizar ItemPedidoDao, PagamentoDao e dependencias.
- Integrar geracao/conclusao de entregas, estoque e pagamento simulado.
- Alinhar SQL, enums de pagamento e regra de CNH para bicicleta.
- Executar testes JDBC, rollback, concorrencia e autorizacao.
- Revisar exclusao fisica versus desativacao e o diff completo.
- Registrar resultados reais; compilacao nao equivale a teste de banco.

Incluir somente fontes e configuracoes necessarias, revisados individualmente.
Excluir PDFs, explicacoes, este rascunho, testes locais, scripts auxiliares,
credenciais, .env e artefatos de compilacao. Nao usar git add indiscriminado.
Filtro de historico por status: tarefa futura de prioridade media.
Nenhum commit ou push autorizado antes de concluir os DAOs e correlacoes.

## Validacao executada em 02/11/2026
Compilacao estrita e suites existentes aprovadas. Novo teste pedido/comanda:
49 verificacoes com FKs e rollback de estoque. Aceite: 40 verificacoes,
incluindo 10 disputas simultaneas. Detalhes e limites em
validacao-integracao-2026-11-02.md (material local, excluir do commit).
Autorizacao, cancelamento integral e exclusoes fisicas continuam pendentes.
Nao liberar commit ainda.

## Itens integrados
ItemPedidoRegistro.java e ItemPedidoDao.java adicionados.
Suite JDBC pedido/comanda/itens: 89 verificacoes aprovadas, com rollback de estoque.
Proximo conjunto: alinhar enums Java aos valores SQL e desenvolver
PagamentoRegistro.java/PagamentoDao.java. SQL nao pode ser alterado.
Revisar mensagem final do commit somente apos concluir esse conjunto e integracoes.

## Apoio a pagamento e entrega integrado
Criados PagamentoRegistro, OrdemEntregaRegistro e StatusOrdemEntrega.
MetodoPagamento e StatusPagamento alinhados ao SQL existente.
Compilacao estrita aprovada; 24 verificacoes em memoria aprovadas.
Regressao de dominio executada. Persistencia JDBC deste conjunto ainda pendente.
PDFs 15 a 19 em docs/explicacoes, fora do commit.
Proximos: PagamentoDao e ampliar OrdemEntregaDao; depois coordenacao nos services.
Malha de rotas e limites de carga configuraveis ainda pendentes, sem dados reais.

## Atualização de 03/11/2026
PagamentoDao e ampliação de OrdemEntregaDao compilados e validados: 134 verificações JDBC e 40 de aceite (10 disputas). Regressão de modelos e higiene aprovadas. Guias 20/21 disponíveis localmente; excluir PDFs, relatórios e testes da seleção do commit. Não enviar ainda: revisar integração dos services, cancelamento integral, autenticação e liberação do entregador. Nenhum commit/push realizado.
