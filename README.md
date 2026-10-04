# Nexus Dispatch

Estrutura inicial do projeto Nexus dispatch, todos os arquivos, pastas e documentações em geral para desenvolvimento do sistema de intermermedio e catalago entre pequeno comerciante ao cliente.

## Estado atual

Este projeto contém apenas a estrutura de desenvolvimento e arquivos iniciais. As classes não possuem implementação, os enums ainda não possuem valores e as telas possuem somente a estrutura HTML. O Spring Boot, as dependências, a versão do Java e o banco ainda precisam ser configurados no pom.xml. A aplicação ainda não pode ser iniciada como servidor web. Os arquivos de teste são espaços reservados e não executam verificações.

## Organização

- `src/main/java/com/nexusdispatch/model`: entidades do domínio.
- `src/main/java/com/nexusdispatch/enums`: valores enumerados.
- `src/main/java/com/nexusdispatch/service`: regras de negócio e transações.
- `src/main/java/com/nexusdispatch/dao`: acesso aos dados.
- `src/main/java/com/nexusdispatch/controller`: rotas e integração com as telas.
- `src/main/java/com/nexusdispatch/database`: configuração de acesso ao banco.
- `src/test`: testes e configurações de teste.
- `docs`: documentação do projeto.

## Regras já definidas

- Uma compra pode incluir produtos de várias lojas.
- Cada pedido pertence a uma única loja e gera sua própria ordem de entrega.
- Produtos da mesma loja podem ser entregues juntos; produtos de lojas diferentes têm entregas separadas.
- A confirmação da compra é atômica: em caso de falha, nenhum pedido, alteração de estoque ou ordem de entrega dessa compra deve persistir.
- O pagamento será simulado.

## Próximas etapas

1. Definir versões do Java/Spring Boot e o banco; configurar o Maven.
2. Validar enums, entidades e relacionamentos.
3. Implementar persistência e confirmação transacional da compra.
4. Implementar controllers, telas e testes das regras de negócio.