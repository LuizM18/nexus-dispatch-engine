# Nexus Dispatch

Marketplace local que conecta consumidores, lojas e entregadores, com catálogo por loja e intermediação das entregas.

## Estado atual

Atualizado em **18/10/2026**. Primeira versão das entidades Java; desenvolvimento incremental na branch **`Java_files`** e integração na **`main`**.

O projeto está na construção das entidades e validações. Já possui cadastro de lojas, produtos próprios de cada loja, ofertas e histórico de entradas de estoque em memória. **Ainda não há aplicação web executável, conexão com banco, login ou fluxo completo de compra e entrega.**

O `pom.xml` define **Java 25**. Spring Boot, dependências web, driver JDBC/MySQL e testes integrados ao Maven ainda precisam ser configurados. `NexusDispatchApplication` permanece sem método de entrada.

## Entidades implementadas

| Classe | Implementação atual | Pendências principais |
|---|---|---|
| `Usuario` | Superclasse abstrata com ID, nome, email, telefone opcional e hash recebido no construtor. | Persistência, unicidade de email, autenticação e geração segura do hash no serviço. |
| `Consumidor` | Dados herdados e um endereço obrigatório, com consulta e troca validada. | Persistência e autorização; Compra já preserva o endereço histórico. |
| `Lojista`, `Administrador` | Construtores que reutilizam a superclasse. | Operações e permissões dos perfis nos serviços. |
| `Bairro` | Nome, cidade, atendimento e validações. | Persistência e consulta à base cadastrada. |
| `Endereco` | Logradouro, número, complemento, CEP e bairro; construtor e setters validados. | Persistência e revisão da regra de CEP. |
| `Entregador` | Veículo, CNH, PBT, lotação e disponibilidade manual; atualização validada. | Ocupação, área de atuação e bloqueio durante entrega. |
| `Loja` | Nome, CNPJ numérico, endereço de coleta e responsável; abertura/fechamento. | Unicidade de CNPJ, permissões e conferência documental. |
| `Produto` | Loja proprietária fixa, nome obrigatório e descrição opcional. | Autorização do lojista no serviço e persistência. |
| `OfertaProduto` | Produto fixo, preço, estoque e histórico de entradas em memória. | Baixa de compra, concorrência e persistência. |

`Compra` e `OrdemEntrega` estão implementadas com validações locais, endereço histórico e integração entre estados. `Pedido` reúne itens de uma loja, protege a lista e controla a sequência de estados. `ItemPedido` implementa quantidade, subtotal, dados históricos e reconstituição de itens salvos. Os serviços, DAOs e controllers continuam como estruturas iniciais.

## Regras implementadas

### Usuários, bairros e endereços

- Nome e email de usuário são obrigatórios; telefone em branco ou ausente vira `null`.
- Email possui verificação básica de formato. Email e `senhaHash` são finais na interface atual, sem operações de troca implementadas.
- A entidade exige um hash preenchido, mas não o gera nem comprova seu algoritmo. O futuro serviço deve produzi-lo com biblioteca apropriada; não expor o campo em telas, respostas ou logs.
- Endereço não pertence a `Usuario`. A loja possui endereço de coleta; o consumidor exige um endereço no cadastro e pode substituí-lo.
- Bairro exige nome e cidade com pelo menos uma letra.
- Endereço exige logradouro com letra, número com algum dígito ou `s/n`, bairro não nulo e CEP numérico. Complemento é opcional.
- A regra atual de CEP aceita **8 a 10 dígitos de 0 a 9**, sem máscara, preservando zeros iniciais. Essa regra precisa de revisão e não comprova a existência do CEP.
- IDs começam como `null`; a atribuição pelo banco ainda não foi implementada.

### Loja e catálogo

**Decisão do grupo:** cada produto pertence a uma única loja. Isso substitui o catálogo genérico compartilhado do PDF de 30/09/2026.

- Loja usa o contato do lojista, sem duplicar seus dados pessoais.
- CNPJ obrigatório com 14 dígitos ASCII, sem máscara, após remover espaços externos. Não há cálculo dos dígitos, suporte alfanumérico, consulta cadastral ou unicidade implementada.
- Loja começa fechada. Nome e endereço podem ser alterados; CNPJ e responsável não possuem setters.
- Abrir/fechar apenas altera o estado. O serviço deverá verificar permissões e impedir novas compras quando a loja estiver fechada.
- Produto exige nome com pelo menos uma letra e até **120 pontos de código Unicode**. Descrição opcional de até **1.000 pontos de código**; ausência vira `null`.
- Limites são verificados depois de remover espaços externos. Textos longos são recusados, não truncados.
- Oferta obtém a loja por `produto.getLoja()`, sem aceitar outra loja no construtor.
- Somente o lojista responsável deverá editar seus produtos; essa autorização ainda não existe no código.

### Preço e estoque

- Preço usa `BigDecimal`, é obrigatório e não pode ser negativo. **Zero é permitido.**
- A política de casas decimais e arredondamento ainda precisa ser definida; atualmente não há limite de casas.
- Estoque inicial não negativo; reposições positivas, com proteção contra estouro de `int`.
- Cada entrada registra quantidade, data/hora local, saldo anterior e saldo posterior. Estoque inicial positivo também gera registro.
- O histórico é consultado por uma cópia não modificável, com registros imutáveis.
- Todo o estoque pode ser vendido: a disponibilidade é igual ao saldo atual, sem reserva mínima.
- Esse cálculo não realiza compra, baixa nem bloqueio de estoque. Operações simultâneas dependem do serviço e do banco.
- **O histórico existe apenas em memória e é perdido ao encerrar a aplicação.**

### Entregadores

- Um veículo por entregador. Bicicleta permite CNH ausente; moto aceita A/AB/AC; carro enquadrado em B aceita B/AB/C/AC.
- VAN representa o recorte do projeto: carga, combustão, sem reboque e até oito passageiros além do motorista.
- Van com PBT até 3.500 kg aceita B/AB/C/AC; acima disso, C/AC.
- Criação e atualização conjunta validam antes de alterar os atributos, preservando valores após erro.
- Características e documentos do veículo não são comprovados pelo sistema.

## Enums

| Enum | Valores |
|---|---|
| `TipoVeiculo` | `CARRO`, `MOTO`, `BICICLETA`, `VAN` |
| `CategoriaCnh` | `A`, `B`, `AB`, `C`, `AC` |
| `MetodoPagamento` | `PIX`, `CARTAO`, `DINHEIRO` |
| `StatusPagamento` | `PENDENTE`, `RECUSADO`, `APROVADO` |
| `StatusPedido` | `CONFIRMADO`, `EM_ROTA`, `EM_PREPARACAO`, `ENTREGUE` |
| `StatusEntrega` | `AGUARDANDO_ENTREGADOR`, `ACEITA`, `EM_ROTA`, `ENTREGUE` |

Pedido e OrdemEntrega implementam transições locais. Pagamento e coordenação transacional nos serviços ainda não estão implementados.

## Compra e entrega: implementação e pendências

- Compra pode reunir produtos de várias lojas, com um pedido e uma ordem de entrega por loja. As entregas são independentes e não exigem entregadores diferentes; a atribuição respeitará a disponibilidade no serviço.
- `ItemPedido` preserva **nome, descrição e preço unitário** do momento da compra, além de quantidade e subtotal.
- CompraService ainda deverá verificar loja aberta, quantidade positiva e saldo suficiente, inclusive sob concorrência.
- Pagamento será simulado; valores monetários usarão `BigDecimal`.
- Confirmação será transacional: qualquer falha desfaz os registros e baixas de estoque daquela operação.
- Ainda pendente: aceite exclusivo, uma ordem ativa por entregador e bloqueio de veículo do aceite até a conclusão. A entidade isolada não garante essas regras.
- Fechar uma loja não cancela pedidos confirmados.
- Frete, comissão e arredondamento financeiro ainda precisam de regras finais.

## Testes e verificação

Na última execução, em **18/10/2026**, todos os arquivos Java de `src` e as verificações locais compilaram com Java 25, UTF-8 e `-Xlint:all`, sem erros ou avisos do compilador.

| Verificação local | Resultado |
|---|---|
| `work/CompraEntregaIntegrationCheck.java` | 64 verificações de integração em memória passaram |
| `work/PedidoCheck.java` | 35 verificações passaram |
| `work/ItemPedidoCheck.java` | 45 verificações passaram |
| `work/ConsumidorCheck.java` | 32 verificações passaram |
| `work/ProdutoCheck.java` | 64 verificações passaram |
| `work/OfertaProdutoCheck.java` | 67 verificações passaram |
| `work/LojaCheck.java` | 41 verificações passaram |
| `work/UsuarioCheck.java` | 180 verificações passaram |
| `work/EntregadorCheck.java` | 1.330 verificações passaram |
| `work/EnderecoCheck.java` | 104 verificações passaram |
| `work/bairro-check/BairroSetterCheck.java` | Passou |
| `work/bairro-check/BairroLetterCheck.java` | Passou |

Cobertura inclui entradas inválidas, limites, normalização, preservação após erro, estoque, histórico e isolamento entre produtos de lojas diferentes.

Testes executados diretamente com `javac` e `java`, **ainda não integrados ao Maven**. `CompraServiceTest` e `EntregaServiceTest`, em `src/test`, são estruturas vazias. Servidor, banco, autenticação, concorrência real e fluxo completo ainda não foram testados, pois não estão implementados.

Executar os testes locais com `./work/RunChecks.ps1`. Os diagnósticos `PENDENTE` reproduzem falhas de regras globais ainda sem serviços; não significam proteção implementada.

## Organização

| Local | Responsabilidade |
|---|---|
| `src/main/java/com/nexusdispatch/model` | Entidades e validações locais |
| `src/main/java/com/nexusdispatch/enums` | Opções fixas do domínio |
| `src/main/java/com/nexusdispatch/service` | Futuros serviços, permissões e transações |
| `src/main/java/com/nexusdispatch/dao` | Futuras consultas e gravações |
| `src/main/java/com/nexusdispatch/controller` | Futuras rotas e integração com telas |
| `src/main/java/com/nexusdispatch/database` | Configuração de banco, se necessária |
| `src/test` | Estrutura para testes integrados |
| `work` | Testes locais e materiais auxiliares |
| `docs` | Decisões detalhadas e documentos ainda em elaboração |

## Próximas etapas

1. Revisar as lacunas reproduzidas no relatório de integração e configurar testes automatizados.
2. Complementar a reconstituição de **Pedido.java** quando houver persistência; a criação e as transições já estão implementadas.
3. Implementar **CompraService**: lojas abertas, área, pagamento simulado, confirmação e estoque transacionais. Compra.java já reúne os pedidos e preserva o endereço.
4. Implementar **EntregaService** e complementar área/ocupação do entregador. OrdemEntrega.java já coordena as transições locais do pedido.
5. Configurar Maven, Spring Boot, testes automatizados e conexão JDBC/MySQL; alinhar o esquema com o grupo.
6. Implementar DAOs e serviços, unicidade de CNPJ/email, vínculo exclusivo lojista/loja e transações.
7. Implementar autenticação, autorização, geração segura de hashes e respostas sem dados sensíveis. Confirmação de email foi adiada.
8. Integrar estoque e histórico ao banco, garantir aceite exclusivo e bloquear troca de veículo durante entregas.
9. Implementar controllers e interface HTML/CSS; Thymeleaf é a proposta de templates.

Infraestrutura e testes podem avançar em paralelo às entidades, com responsáveis definidos.

## Documentação e decisões pendentes

- [Revisão completa e integração — 18/10/2026](docs/revisao-integracao-2026-10-18.md)
- [Usuário e perfis](docs/usuario.md)
- [Consumidor e endereço obrigatório](docs/consumidor.md)
- [Loja e validações pendentes](docs/loja.md)
- [Produto por loja](docs/produto.md)
- [Oferta, estoque e histórico](docs/oferta-produto.md)
- [Revisão de entregador e limites de segurança](docs/revisao-entregador.md)

O [PDF de requisitos v2.0](outputs/Nexus_Dispatch_Requisitos_Classes_Java_Atualizado.pdf), de 30/09/2026, é uma referência anterior. Precisa incorporar produtos próprios por loja, venda de todo o estoque, histórico de entradas, endereço fora de Usuario, autenticação e as regras atuais de veículos. `outputs/github-README.md` também é anterior e não representa o estado atual.

A política de ofertas duplicadas do mesmo produto, limites adicionais de cadastro e cancelamento após confirmação precisam de alinhamento. Unicidade, permissões e conferência documental pendentes não devem ser confundidas com funcionalidades já protegidas pelas entidades.
