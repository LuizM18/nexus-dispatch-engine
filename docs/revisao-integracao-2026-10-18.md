# Revisao do projeto e integracao — 18/10/2026

## Escopo e resultado

Inspecionados todos os fontes Java de model, enums, service, dao, controller,
database, ponto de entrada e testes, alem do pom e configuracoes presentes.
Nao e uma certificacao de seguranca ou teste de um sistema em producao.
Nao houve commit, push ou alteracao da main.

Compra e OrdemEntrega foram integradas. Todas as entidades previstas em model
agora possuem implementacao inicial. Services, DAOs, controllers e DatabaseConfig
continuam sem operacoes; NexusDispatchApplication nao inicia um servidor.

Compilacao Java 25 com UTF-8 e -Xlint:all passou sem avisos do compilador.
Executados 12 programas de verificacao: 1.962 verificacoes numeradas e dois
programas de Bairro sem contagem. Todas passaram. Destas, 64 verificam a
integracao entre entidades em memoria. Nao ha banco ou chamadas HTTP nos testes.

## Ajustes desta revisao

- OrdemEntrega usa o StatusEntrega existente (AGUARDANDO_ENTREGADOR, ACEITA,
  EM_ROTA, ENTREGUE), evitando um segundo enum incompatível com o projeto.
- Ao receber outro objeto Pedido com o mesmo ID, a ordem usa a instancia
  pertencente a Compra. Assim, a retirada atualiza o pedido exibido na compra.
  A condicao foi testada com IDs simulados exclusivamente no teste.
- Compra nova rejeita pedidos ja em preparacao, em rota ou entregues. Recuperar
  compras antigas exigira uma operacao especifica de reconstituicao.
- Endereco e bairro sao copiados como textos imutaveis. Mudancas no cadastro nao
  alteram o destino das ordens. Precos e nomes historicos tambem foram verificados.
- Testado o mesmo entregador realizando pedidos de duas lojas em sequencia.

## Lacunas prioritarias reproduzidas

Os diagnosticos PENDENTE do teste reproduzem comportamentos atuais indevidos no
fluxo completo. Nao sao protecoes implementadas nem testes de seguranca aprovados.

| Prioridade | Evidencia e impacto | Implementacao necessaria |
|---|---|---|
| Alta | Dois aceitar() em ordens distintas permitem o mesmo entregador ocupado em ambas. | EntregaService deve verificar e registrar ocupacao de forma atomica no banco, do aceite a conclusao, com bloqueio/controle de concorrencia. |
| Alta | atualizarVeiculoECnh() funciona enquanto existe ordem ativa. | EntregadorService deve verificar ordens ativas na mesma estrategia de concorrencia do aceite. |
| Alta | Pedido.iniciarEntrega() pode ser chamado diretamente e deixar a ordem em ACEITA. | Centralizar as transicoes no servico; gravar pedido e ordem juntos e impedir rotas independentes que quebrem o vinculo. Avaliar restringir a API interna ao implementar o servico. |
| Alta | E possivel criar duas ordens para o mesmo pedido e reutilizar um pedido em duas compras. | FK de pedido para compra, UNIQUE de ordem por pedido, verificacoes no servico e tratamento de conflitos. |
| Alta | Criar Compra aceita loja fechada; nao processa pagamento nem diminui estoque. | CompraService deve conferir lojas, area, precos e saldo; coordenar pagamento simulado e transacao integral. Implementar baixa de estoque que nao permita saldo negativo. |

Esses casos foram reproduzidos sequencialmente em memoria. Nao foi executado
teste concorrente: ainda nao existe uma operacao transacional para testar.
Nao resolver com uma lista static de entregadores: ela perderia dados ao reiniciar
e nao protegeria diferentes instancias do servidor.

## Revisao por area e sequencia sugerida

1. **Infraestrutura e testes:** configurar Maven Wrapper, dependencias de testes e
   Spring Boot, ponto de entrada e pipeline. Hoje os checks sao programas main;
   os dois arquivos em src/test sao vazios. Maven nao foi localizado no PATH desta
   sessao; a compilacao utilizada foi javac. Migrar os casos para testes automaticos
   sem perder os diagnosticos das regras ainda ausentes.
2. **Banco e DAOs:** definir esquema e migracoes, FKs, unicidade de email/CNPJ e
   lojista/loja, ordem/pedido e limites de valores. Implementar IDs e reconstituicao
   sem recalcular precos/endereco/data historicos nem exigir estoque atual.
   ItemPedido possui reconstituir; as demais entidades ainda precisam desse fluxo.
   Consultas parametrizadas e configuracao de credenciais fora do codigo.
3. **CompraService:** montar um pedido por loja; impedir reutilizacao; buscar
   precos no servidor; validar area/loja/saldo; aprovar pagamento simulado; gravar
   compra, itens, pedidos, ordens e baixa numa transacao com rollback. Incluir
   idempotencia para repeticao da confirmacao e testes de saldo concorrente.
4. **EntregaService/EntregadorService/PedidoService:** corrigir as lacunas acima,
   testar dois aceites simultaneos, atualizacao de veiculo concorrente e rollback
   de transicoes. Uma entrega ativa inclui ACEITA e EM_ROTA. Disponivel nao e
   sinonimo de livre; ficarIndisponivel nao cancela uma entrega em andamento.
5. **Usuarios e permissoes:** gerar/verificar hash de senha no servico e autenticar;
   checar dono da loja, consumidor da compra, entregador da ordem e administrador.
   Os setters validam dados, nao identidade. getSenhaHash existe: nunca serializar
   Usuario diretamente em respostas; usar DTO sem hash. Nao ha endpoint hoje que
   comprove vazamento, mas ha risco ao conectar entidades diretamente a uma API.
6. **Catalogo e estoque:** persistir entradas e saidas; manter overflow protegido;
   definir se um Produto pode ter varias ofertas na mesma loja. Hoje a duplicidade
   rejeitada e da mesma oferta, nao necessariamente de ofertas distintas do mesmo
   produto. Limites de texto/numeros devem coincidir entre modelo, DTO e banco.
7. **Financeiro e pagamento:** decidir casas decimais/arredondamento, frete, taxas e
   comissao. Zero e permitido; valores negativos sao rejeitados. MetodoPagamento
   e StatusPagamento ainda nao estao ligados a Compra. Definir como DINHEIRO se
   encaixa na aprovacao simulada anterior a confirmacao. Nao inventar valores.
8. **Controllers e interface:** so depois dos contratos dos servicos, implementar
   rotas, validacao de entrada, respostas sem dados sensiveis, tratamento de erros
   e protecoes adequadas ao modo de autenticacao. Ainda nao ha UI funcional.

## Decisoes e limitacoes adicionais

- Cancelamento, devolucao, estorno e reatribuicao continuam fora do fluxo aprovado.
- Endereco aceita CEP de 8 a 10 digitos conforme a regra atual do codigo; revisar
  essa politica com o grupo antes de tratar como validacao de um endereco real.
- CNPJ e apenas formato numerico de 14 digitos, sem digitos verificadores ou
  consulta. Email e validacao basica; telefone nao possui formato definido.
- Endereco de entrega e historico, mas endereco de coleta da Loja continua
  mutavel. Definir se a ordem deve preservar a coleta antes de mudar o cadastro.
- LocalDateTime nao registra fuso. Definir padrao de datas ao configurar o banco.
- Transicoes locais nao sao thread-safe e nao substituem uma transacao. Mesmo
  listas imutaveis contem referencias a pedidos cujo estado pode mudar.
- A exposicao do construtor publico do record EnderecoEntrega permite instancias
  isoladas sem validacao; hoje Compra so usa dados de Endereco validado e nao recebe
  esse record de fora. Validar na futura entrada/reconstituicao que o aceitar.
- Historico de estoque vive em memoria; nao ha persistencia de nenhuma entidade.
- Nenhuma garantia global deve ser anunciada como concluida ate existirem servicos,
  restricoes de banco e testes de concorrencia correspondentes.

## Como reproduzir

Na raiz do repositorio, executar `./work/RunChecks.ps1` com Java 25 no PATH.
O script compila src e todos os checks, abortando na primeira falha. A secao
PENDENTE confirma as limitacoes conhecidas e deve virar teste de rejeicao quando
os servicos forem implementados. Nao usar sua saida como aprovacao de seguranca.
