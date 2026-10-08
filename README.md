# Nexus Dispatch

Projeto de marketplace local para aproximar consumidores e pequenos comerciantes, com catálogo de produtos e intermediação das entregas.

## Estado atual

Atualizado em **08/10/2026**.

As classes `Bairro` e `Endereco` possuem atributos, construtores, validações, getters e setters. `Entregador` agora valida veículo, CNH, PBT e lotação no construtor e na atualização conjunta. Os seis enums já têm valores definidos. Usuario possui dados comuns e validações, com construtores nos quatro perfis. As demais entidades e as camadas de serviços, acesso a dados e controllers ainda são estruturas iniciais.

O `pom.xml` define **Java 25**, mas ainda não configura Spring Boot, dependências web, driver do banco ou ferramentas de testes. `NexusDispatchApplication` ainda não possui método de entrada nem inicialização do Spring Boot. O projeto ainda não pode ser iniciado como servidor web e não possui persistência nem telas implementadas.

## O que já foi implementado

### Bairro

- Atributos: `id`, `nome`, `cidade` e `atendido`.
- Nome e cidade obrigatórios, com pelo menos uma letra.
- Remoção de espaços no início e no fim dos textos.
- Getters e setters; alterações inválidas preservam o valor anterior.
- O identificador começa como `null` e não possui setter; sua atribuição será definida na integração com o banco.

### Endereco

- Atributos: `id`, `logradouro`, `numero`, `complemento`, `cep` e `bairro`.
- Logradouro obrigatório, com pelo menos uma letra.
- Número obrigatório, contendo pelo menos um dígito ou sendo `s/n`, sem diferenciar maiúsculas e minúsculas nessa comparação. Valores como `12A` e `bloco2` são aceitos; a validação não remove letras.
- CEP obrigatório, com **8 a 10 dígitos de 0 a 9**, sem hífen, letras ou espaços internos. Guardado como texto para preservar zeros à esquerda. A validação verifica o formato definido no projeto, não a existência do CEP.
- Complemento opcional: quando recebido como `null`, é guardado como uma string vazia.
- Bairro obrigatório como referência a um objeto `Bairro`. A consulta ao cadastro na base e a verificação de atendimento ainda serão implementadas.
- Remoção de espaços nas extremidades dos textos.
- Getters e setters; alterações inválidas dos campos obrigatórios preservam os valores anteriores.
- A cidade é mantida em `Bairro`, sem duplicação em `Endereco`.
- O identificador começa como `null` e não possui setter.

### Enums

| Enum | Valores atuais |
|---|---|
| `TipoVeiculo` | `CARRO`, `MOTO`, `BICICLETA`, `VAN` |
| `CategoriaCnh` | `A`, `B`, `AB`, `C`, `AC` |
| `MetodoPagamento` | `PIX`, `CARTAO`, `DINHEIRO` |
| `StatusPagamento` | `PENDENTE`, `RECUSADO`, `APROVADO` |
| `StatusPedido` | `CONFIRMADO`, `EM_ROTA`, `EM_PREPARACAO`, `ENTREGUE` |
| `StatusEntrega` | `AGUARDANDO_ENTREGADOR`, `ACEITA`, `EM_ROTA`, `ENTREGUE` |

Os enums definem as opções disponíveis. A compatibilidade de veículo/CNH foi implementada em `Entregador`; transições entre estados continuam pendentes. VAN representa o recorte de van de carga a combustão, sem reboque e com até oito passageiros além do motorista. PBT até 3.500 kg aceita B/AB/C/AC; acima disso, C/AC. Essas características e os documentos ainda não são comprovados pelo cadastro.

A implementação passou em 1.313 verificações locais de `work/EntregadorCheck.java`, além das verificações existentes de endereço e bairro. A compilação Java passou sem avisos. Consulte [a revisão e suas limitações de segurança](docs/revisao-entregador.md). Não há aplicação publicada nem certificação de segurança.

### Usuario e perfis

Usuario agora centraliza nome, email, telefone opcional e hash recebido do serviço, além de ID inicialmente nulo. Os quatro perfis chamam seu construtor. Foram executadas 180 verificações adicionais. Consulte [as regras e limitações](docs/usuario.md). Autenticação e geração segura de hash ainda estão pendentes.

## Organização

- `src/main/java/com/nexusdispatch/model`: entidades do domínio.
- `src/main/java/com/nexusdispatch/enums`: valores enumerados.
- `src/main/java/com/nexusdispatch/service`: regras de negócio e transações.
- `src/main/java/com/nexusdispatch/dao`: acesso aos dados.
- `src/main/java/com/nexusdispatch/controller`: rotas e integração com as telas.
- `src/main/java/com/nexusdispatch/database`: configuração de acesso ao banco.
- `src/test`: estruturas de testes e configuração de teste ainda pendentes de implementação.
- `docs`: arquivos reservados para documentação detalhada.
- `outputs/Nexus_Dispatch_Requisitos_Classes_Java_Atualizado.pdf`: documento de requisitos versão 2.0, de 30/09/2026.
- `work`: verificações locais e arquivos auxiliares de desenvolvimento.

## Verificações realizadas

Na revisão de 04/10/2026:

- Os **46 arquivos Java de `src`** compilaram com Java 25, sem erros e sem avisos do compilador.
- `Endereco` passou em **104 verificações locais**, cobrindo construtor, getters, setters, entradas válidas e inválidas, tratamento de espaços e preservação de valores após alterações rejeitadas.
- As verificações locais de `Bairro` passaram, incluindo campos obrigatórios, exigência de letras, nomes com acentos, tratamento de espaços, atendimento e preservação de valores anteriores.
- A tentativa de iniciar `NexusDispatchApplication` confirmou a ausência do método de entrada da aplicação.

As verificações executadas estão em `work/EnderecoCheck.java`, `work/bairro-check/BairroSetterCheck.java` e `work/bairro-check/BairroLetterCheck.java`. Ainda não estão integradas ao Maven. `CompraServiceTest` e `EntregaServiceTest`, em `src/test`, permanecem vazios e não executam verificações.

Servidor web, banco, interface e fluxo completo de compras ainda não foram testados, pois não estão implementados. A compilação direta não confirma uma construção pelo Maven; o Maven não estava disponível na sessão de revisão.

## Regras de negócio previstas

Estas regras orientam as próximas implementações:

- Uma compra pode incluir produtos de várias lojas.
- Cada pedido pertence a uma única loja e gera sua própria ordem de entrega.
- Produtos da mesma loja podem ser entregues juntos; produtos de lojas diferentes têm entregas separadas.
- A confirmação da compra deve ser atômica: em caso de falha, nenhuma compra, pedido, item, ordem de entrega ou baixa de estoque dessa operação deve persistir.
- O pagamento será simulado.
- Preço e estoque pertencem à oferta de um produto em uma loja.
- Valores monetários serão representados por `BigDecimal`.
- A primeira versão previa ausência de login; autenticação e autorização passaram a ser próximas etapas, ainda não implementadas. Confirmação de email foi adiada.

## Pontos a alinhar com a documentação

- `Endereco` passou a incluir CEP obrigatório e utiliza a cidade de `Bairro`; o PDF ainda descreve `cidade` diretamente em `Endereco` e não lista `cep`.
- `VAN` está presente no código, mas não consta entre os veículos definidos no PDF. O recorte implementado está descrito acima; falta alinhar o PDF e verificar as características reais no futuro cadastro.
- O PDF exclui cancelamento posterior de compras confirmadas nesta etapa, enquanto `outputs/github-README.md` prevê cancelamento integral. O escopo precisa ser alinhado antes da implementação.
- `outputs/github-README.md` é um documento anterior e não descreve o estado atual desta pasta. Suas referências ao repositório remoto e ao workflow não foram verificadas nesta revisão local.

## Próximas etapas

1. Implementar `Usuario` e os perfis `Consumidor`, `Lojista`, `Entregador` e `Administrador`.
2. Implementar `Loja`, `Produto` e `OfertaProduto`, com relacionamentos e validações.
3. Implementar `Compra`, `Pedido`, `ItemPedido` e `OrdemEntrega`.
4. Configurar Maven, Spring Boot e persistência JDBC/MySQL; o documento prevê MySQL 8, ainda sem configuração no projeto.
5. Implementar serviços, DAOs e transações, com controle de estoque e aceite de entregas concorrentes.
6. Implementar controllers e interface HTML/CSS; Thymeleaf é a proposta de templates do documento.
7. Integrar testes automatizados ao projeto, com banco de testes separado e cobertura das regras de compra e entrega.
8. Manter a documentação alinhada às decisões e ao progresso do código.
