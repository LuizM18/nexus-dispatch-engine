# Nexus Dispatch

Projeto de marketplace local para aproximar consumidores e pequenos comerciantes, com intermediação das entregas.

## Estado atual

O repositório está na preparação inicial. Além deste README, estão publicados o workflow de integração contínua em `.github/workflows/ci.yml` e o modelo de pull request em `.github/pull_request_template.md`.

Ainda não há `pom.xml`, código da aplicação ou testes publicados neste repositório. Portanto, esta versão não pode ser compilada ou iniciada como aplicação. As tecnologias e regras abaixo orientam o desenvolvimento; não representam funcionalidades já implementadas.

## Tecnologias definidas

- **Java 25** para o código da aplicação.
- **Maven** para dependências, compilação e execução de testes.
- **MySQL 8** para persistência dos dados.
- **HTML e CSS** para a interface.

A configuração do projeto Maven, das dependências web e da conexão com o banco ainda precisa ser publicada. O workflow já seleciona Java 25.

## Regras de negócio definidas

- Uma compra pode reunir produtos de várias lojas.
- Cada pedido pertence a uma única loja.
- Cada pedido gera sua própria ordem de entrega: produtos da mesma loja podem ser entregues juntos; produtos de lojas diferentes têm entregas separadas.
- A confirmação da compra deve ocorrer em uma única transação. Se houver falha, nenhum pedido, alteração de estoque ou ordem de entrega dessa compra deve persistir.
- O cancelamento terá escopo da **compra inteira**, sem cancelamento parcial por item ou por loja nesta etapa. As condições e os efeitos do cancelamento ainda precisam ser detalhados e implementados.
- O pagamento será simulado.

## Organização planejada

- `model`: entidades e relacionamentos do domínio.
- `enums`: opções e estados utilizados pelo sistema.
- `controller`: entrada das requisições e integração com a interface.
- `service`: regras de negócio e controle das transações.
- `dao`: acesso aos dados.
- `database`: configuração do banco.
- `src/test`: testes automatizados.

## Compilação e testes

Ainda não existe um comando de execução funcional para este checkout, pois falta o projeto Maven.

O workflow atual é acionado por pull requests direcionados a `main` e `develop` e contém:

```sh
mvn clean verify -DskipTests
```

A opção `-DskipTests` desativa a execução dos testes. Além disso, sem `pom.xml`, o workflow não consegue construir o projeto. Um commit direto na branch principal não aciona esse workflow.

Após publicar e configurar o projeto Maven e seus testes, a verificação deverá executar, sem desativar os testes:

```sh
mvn clean verify
```

Os primeiros testes de negócio deverão verificar o agrupamento dos pedidos por loja, as entregas separadas, o estoque insuficiente, o rollback da compra inteira em caso de falha e o cancelamento integral.

## Próximas etapas

1. Publicar a estrutura Java e o `pom.xml`, configurando dependências e plugins compatíveis com Java 25.
2. Configurar MySQL 8 e versionar o esquema do banco, incluindo relacionamentos e restrições de integridade.
3. Implementar as entidades e regras de negócio, incluindo confirmação transacional da compra e entregas por loja.
4. Implementar a interface e sua integração com a aplicação.
5. Publicar testes efetivos e ajustar a integração contínua para executá-los.
