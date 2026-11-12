# UsuarioService: entrega focada

03/11/2026. Por orientacao do usuario, uma classe principal por entrega.
LojistaService/EntregadorService iniciados na mesma sessao sao rascunhos:
nao receberam revisao/testes de entrega e ficam para etapas separadas.
Menu novo de gestao tambem nao e declarado validado integralmente nesta entrega.

## Para que serve

UsuarioService coordena dados comuns do usuario: buscar, listar, atualizar,
ativar/desativar e remover quando seguro. Cadastro continua no CadastroService.
Nao precisa repetir ali a geracao de senha ou inserir o mesmo perfil novamente.
O service usa os DAOs, que executam SQL. O resultado nunca inclui senha_hash.

## Metodos publicos

Construtor sem argumentos usa ConexaoBD. Construtor com FabricaConexao permite
testar a mesma classe em outro catalogo sem gravar no banco original.

buscar(id): Optional com a ficha do usuario ou vazio se nao existe.
listar(limite, deslocamento): consulta uma pagina, respeitando limites do DAO.
Exemplo: limite 20, deslocamento 0 mostra o inicio; deslocamento 20 a proxima.

atualizar(id, DadosUsuario): muda nome, documento fiscal e email. Nao muda
perfil, senha, comissao ou CNH. Bloqueia a linha antes da escrita; erro de
validacao ou email duplicado nao altera o estado anterior. ID inexistente
retorna false, e nao sucesso. ADMIN nao pode ser editado pelo modo livre de
demonstracao sem uma politica propria de autorizacao.

definirAtivo(id, ativo): controla atividade do usuario, sem apagar compras.
Desativar CLIENTE nao cancela nem estorna seus pedidos automaticamente.
Desativar LOJISTA exige que ele nao tenha comandas em andamento. O complemento
comercial e bloqueado antes dessa verificacao. Reativar exige complemento valido.

No ENTREGADOR, a ordem dos bloqueios e entregador, depois usuario, como no
aceite. Nao e permitido desativar enquanto EM_ROTA ou com ordem ativa, mesmo
se o estado cadastral estiver inconsistente com as ordens. Desativacao permitida
coloca o estado operacional em INATIVO e o usuario inativo na mesma transacao.
Reativar o usuario nao o torna DISPONIVEL automaticamente. Estado antigo NULL
pode ser explicitamente regularizado para INATIVO ao desativar sem trabalho.
Repetir o mesmo alvo de atividade e seguro e retorna sucesso se ja atingido.

excluir(id): DELETE real apenas de CLIENTE explicitamente inativo e sem
compras, complementos ou autoria em auditoria_sistema. Usuario ja usado deve
ser preservado; demais perfis usam desativacao nesta etapa. Usuario ausente
retorna false. Nao existe exclusao em cascata de compras pelo service.

## Apoios alterados e por que

TransacaoJdbc e infraestrutura compartilhada: abre conexao dedicada, exige
autoCommit inicial, usa READ_COMMITTED e confirma com commit ou desfaz com
rollback. A falha de rollback fica suprimida na original. A conexao fecha no
try-with-resources. validarId restringe a chave a INT positivo antes de conectar.
Isso nao substitui transacao de um fluxo maior: cada chamada e independente.

UsuarioDao ganhou leitura bloqueada e excluirClienteSemVinculos. LojistaDao
ganhou bloqueio do complemento e verificacao de comandas em andamento.
EntregadorDao ganhou leitura bloqueada e inativarSemOrdem, que recusa EM_ROTA
e vinculo de ordem ativa. Essas sao dependencias identificadas desta classe;
nao significa que os demais services ja foram revisados.

## Exemplo simples

UsuarioService service = new UsuarioService();
service.buscar(id);
service.atualizar(id, new CadastroService.DadosUsuario(nome, documento, email));
service.definirAtivo(id, false);

Cada chamada confirma sua propria alteracao. Se for cliente inativo sem nenhum
vinculo, service.excluir(id) pode remover. Nao usar IDs de exemplo sem consultar
o cadastro real. Escolher um ID no terminal ainda nao autentica um usuario.

## Verificacao focada

Compilacao completa UTF-8 -Xlint:all -Werror aprovada. UsuarioServiceIntegrationCheck:
39 verificacoes aprovadas em banco UUID descartavel com DDL/FKs reais.
Cobre consulta/listagem, validacao, email duplicado, perfil preservado, ADMIN
bloqueado, cliente ativo nao removido, cliente vazio inativo removido, compras
e autoria de auditoria preservadas, lojista com comanda aberto bloqueado,
entregador EM_ROTA ou com ordem ativa bloqueado, desativacao conjunta e rollback
por falha na segunda escrita. Reativacao nao disponibiliza entregador.

Banco de teste removido no finally; cadastros originais nao alterados. Triggers
originais nao foram copiados automaticamente. Nao houve teste concorrente novo
do cruzamento entre aceite/desativacao, nem teste de todas as novas telas.
O apoio esta em tests/local/UsuarioServiceIntegrationCheck.java, ignorado pelo Git.
Nao declarar LojistaService/EntregadorService entregues apenas porque compilaram.

## Limites

Nao implementa login, autorizacao de sessao, troca de senha/perfil, cancelamento
de compra ou remocao fisica geral de lojistas/entregadores. Nao resolve todas as
pendencias do sistema. Falha no commit pode ter resultado incerto: consultar
antes de repetir automaticamente uma escrita. Sem novo commit/push.

## Para estudar

1. Por que desativar usuario e diferente de cancelar uma compra?
2. Por que reativar entregador nao deve coloca-lo automaticamente DISPONIVEL?
3. Qual problema o rollback evita quando a segunda escrita falha?

Proxima entrega separada: LojistaService, depois EntregadorService.
PDF local: 28-UsuarioService.pdf.
