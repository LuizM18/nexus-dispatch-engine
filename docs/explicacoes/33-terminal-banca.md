# Nexus Dispatch no terminal

Roteiro de apresentação · 07/11/2026

## Preparar a apresentação

Na raiz do projeto, abra o terminal com uma taxa percentual explícita. O comando abaixo usa 7% como exemplo de ensaio. Escolha a taxa da apresentação antes de iniciar.

```powershell
./scripts/RunConsole.ps1 -TaxaCompraPct 7
```

Em **Entrar**, informe o e-mail e a senha do cadastro ativo. O terminal abre o menu do perfil da conta. Para consultar um catálogo por loja, digite `?` no campo de ID da loja para ver as lojas ativas. Se o banco ainda não tiver administrador, execute antes o comando de cadastro inicial e informe os dados e a senha no próprio terminal.

```powershell
./scripts/RunConsole.ps1 -CriarAdminInicial
```

Quando já existe um administrador, o comando recusa a criação de outro. Para usar a conta criada, abra o terminal e escolha **Entrar** com o e-mail e a senha cadastrados. A opção **Criar perfil** cadastra cliente, lojista ou entregador. Nessa opção, a senha e a confirmação ficam visíveis enquanto são digitadas. Nas áreas de perfil, **0. Sair da conta** retorna à tela inicial.

## Roteiro para a banca

1. Crie um lojista e um cliente pelo menu Criar perfil ou escolha registros de teste existentes.
2. Entre com o e-mail e a senha do lojista. Cadastre dois produtos na própria loja, liste o catálogo, busque um deles pelo ID e edite o preço. Saia da conta.
3. Entre como cliente. Adicione ambos ao carrinho, altere a quantidade de um e retire o outro. O estoque só muda quando o pedido é confirmado.
4. Revise produtos, taxa e total. Escolha a forma de pagamento simulada e confirme. Anote o ID que aparece no terminal. Consulte o pedido em Meus pedidos.
5. Volte ao lojista e remova o produto que saiu do carrinho. Busque o mesmo ID para demonstrar a exclusão física. A proteção de histórico bloqueia a exclusão do produto já vendido.
6. Crie um entregador e entre com seu e-mail e sua senha. Coloque-o disponível e depois inativo. O status operacional aparece após cada alteração.
7. Entre como administrador. Liste os usuários para mostrar os novos perfis. Se quiser demonstrar remoção de usuário, cadastre um cliente de teste sem vínculos, desative-o e confirme a remoção. Consulte o pedido criado pelo ID anotado.

## Como o código atende o roteiro

`ConsoleNexus` recebe as entradas, apresenta os menus e chama os serviços. Lojista e administrador reutilizam as mesmas ações de produto. O cabeçalho dos perfis chama `getPerfil()` em uma referência do tipo `Usuario`, mostrando o comportamento polimórfico.

O carrinho guarda os produtos e quantidades durante a sessão do cliente. A cotação verifica o saldo e calcula a taxa com os preços do MySQL. A confirmação revalida o total e grava o pedido, o pagamento simulado, as comandas, os itens e a baixa de estoque. O histórico e o cancelamento são consultados em Meus pedidos. Para usar estorno, aplique `database/001-pagamento-estornado.sql` ao banco da apresentação.

O menu do entregador trata a disponibilidade para receber entregas. O cadastro ativo do usuário é controlado separadamente pela administração. O serviço bloqueia a mudança manual enquanto houver trabalho em rota.

O administrador autenticado consulta pedidos e cadastra, consulta, altera e desativa os perfis existentes conforme as regras dos serviços. A exclusão física de usuário está restrita a cliente inativo e sem vínculos.

O checkout da banca usa pagamento simulado e termina no pedido pago. A configuração da malha e a operação das entregas no terminal ficam para a etapa seguinte.

## Verificar

`mvn -Pintegracao verify` executa o roteiro de terminal junto aos testes de domínio e de serviços no MySQL descartável. A seção [Testes com Maven](../../README.md#testes-com-maven) descreve a configuração.
