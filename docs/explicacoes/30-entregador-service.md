# EntregadorService: cadastro operacional e disponibilidade

03/11/2026. Ultima entrega desta sessao; uma classe principal por vez.
Somente EntregadorService.java foi alterado entre os fontes de producao nesta
etapa. Teste local e material de estudo sao apoio. Sem mudanca de schema ou commit.

## Responsabilidade deste arquivo

EntregadorService consulta o entregador, permite corrigir CNH/veiculo/bairro e
controla se ele deseja receber trabalho. Nao cadastra usuario do zero, nao
autentica uma pessoa e nao executa coleta/conclusao de entrega.

Cadastro inicial fica em CadastroService. Nome, documento, email e atividade
do usuario ficam em UsuarioService. Aceite fica em EntregaService, que ainda
precisa completar coleta, conclusao e liberacao de forma coordenada.

Estar DISPONIVEL nao e o mesmo que estar trabalhando. INATIVO significa que nao
recebe trabalho; EM_ROTA indica operacao ativa. O menu manual nao pode apagar
uma ocupacao simplesmente trocando o estado para INATIVO ou DISPONIVEL.

## Construtores e transacao

EntregadorService() usa ConexaoBD e configuracao externa. O construtor com
FabricaConexao permite testar em outro catalogo sem alterar a configuracao real.
Fabrica null e recusada imediatamente. Nao conserva conexao aberta entre chamadas.

Usa o apoio existente TransacaoJdbc: conexao nova, autoCommit inicial exigido,
READ_COMMITTED, commit em sucesso e rollback em erro SQL/validacao/estado.
Falha de rollback fica suprimida na original e a conexao e fechada ao final.
Se commit falhar por conexao, consultar antes de repetir escrita automaticamente.

## buscar(id)

Valida INT positivo antes de conectar. Bloqueia primeiro entregador, depois
usuario, na mesma ordem do fluxo de aceite. Isso evita compor as duas fichas
com dados concorrentes sem protecao. Os bloqueios acabam ao terminar a chamada;
o objeto retornado e um snapshot, nao uma reserva permanente.

Usuario ausente retorna Optional.empty. Usuario com outro perfil e erro de
argumento. Perfil ENTREGADOR sem complemento e cadastro incompleto, informado
com erro de estado. Optional significa resultado que pode nao existir, sem
confundir ausencia com falha de conexao.

O objeto preserva numero da CNH, tipo de veiculo, bairro e estado do banco.
Nao recebe senha ficticia, categoria da CNH, PBT ou passageiros inexistentes
no SQL. Esses dados ausentes nao podem ser tratados como aptidao comprovada.

## listarDisponiveisPorBairro(bairro, limite, deslocamento)

Retorna pagina de EntregadorRegistro somente para usuario ativo, perfil
ENTREGADOR, estado DISPONIVEL e bairro correspondente no SQL. Nao e pesquisa
de proximidade e nao calcula rota/distancia; comparacao segue a collation do banco.

Remove espacos nas extremidades do bairro, exige texto com ate 100 caracteres,
limite de 1 a 100 e deslocamento nao negativo. Entrada invalida nao abre conexao.
Lista vazia significa nenhum resultado naquela pagina; a lista nao e editavel.

Exemplo: bairro Centro, limite 20, deslocamento 0 consulta a primeira pagina;
deslocamento 20 pula os 20 primeiros. Receber um registro nao o reserva para
uma entrega. O aceite ainda precisa da propria verificacao transacional.

## atualizar(dados)

Recebe EntregadorRegistro para alterar numero da CNH, tipo de veiculo e bairro.
Nao altera dados comuns do usuario, estado operacional ou uma ordem em curso.
Status enviado no record e ignorado para escrita: usa o estado atual bloqueado.

Primeiro bloqueia entregador, depois valida/bloqueia usuario. Complemento ausente
retorna false. Perfil incorreto e recusado. exigirSemTrabalho consulta tanto o
estado EM_ROTA quanto as ordens vinculadas em AGUARDANDO_COLETA/EM_TRANSITO.
Assim, um estado INATIVO inconsistente com ordem ativa nao permite alterar veiculo.

Escrita exige bairro preenchido e CNH numerica como documento informado quando
o modelo exige, sem inventar categoria. Aqui CNH numerica significa numero do
documento em String, nao uma verificacao de formato/digitos ou consulta oficial.
Validacao do contrato existente exige CNH nao vazia para veiculos motorizados;
nao comprova validade legal do documento nem categoria apropriada ao veiculo.

Dados repetidos retornam true sem novo UPDATE, mas so depois das validacoes de
perfil, trabalho e campos. Cadastro de usuario inativo pode ser regularizado;
essa edicao nao reativa o usuario. CNH duplicada e recusada pela UNIQUE do banco,
com rollback. Objetos/valores anteriores nao sao sobrescritos em falha.

## definirDisponivel(id, disponivel)

true solicita DISPONIVEL; false solicita INATIVO. Nao existe parametro manual
para pedir EM_ROTA: esse estado e coordenado pelo aceite/fluxo de entrega.
Bloqueia entregador antes de usuario e consulta trabalho ativo antes de trocar.

Usuario precisa estar ativo para disponibilizar. Perfil inativo/NULL nao pode
receber entregas; pode desligar disponibilidade sem trabalho em andamento.
Bairro precisa estar preenchido para disponibilizar.

Estado SQL NULL e preservado na leitura, sem vira-lo automaticamente DISPONIVEL.
Para regularizar, primeiro solicitar INATIVO explicitamente; depois disponibilizar
se os demais requisitos estiverem validos. Repetir o mesmo alvo e idempotente,
mas ainda verifica perfil e ordens, sem declarar sucesso apenas pelo estado igual.

Estado EM_ROTA ou ordem ativa bloqueia tanto inativacao quanto disponibilizacao.
Nao libera entregador ocupado artificialmente. A conclusao/liberacao pertence
ao fluxo de entrega, que sera concluido em uma proxima etapa.

## Metodos privados

validarPerfil le usuario com bloqueio depois do entregador. Confere ENTREGADOR
e, quando solicitado, atividade TRUE. Usuario ausente e inconsistencia, nao
motivo para criar um usuario ficticio.

exigirSemTrabalho recusa EM_ROTA ou qualquer ordem ativa vinculada. Verificar
so boolean disponivel seria insuficiente para proteger veiculo/operacao. As
ordens tambem sao bloqueadas pela consulta existente do OrdemEntregaDao.

## Exemplo para entender os retornos

Imagine um entregador livre que nao deseja receber trabalho agora. Solicitar
definirDisponivel(id, false) salva INATIVO. Mais tarde, true pode disponibiliza-lo
novamente se usuario/bairro/estado estiverem validos. Quando ele aceita trabalho,
a mesma chamada false deve falhar ate que o fluxo ativo seja encerrado.

buscar devolve Optional de Entregador. listarDisponiveisPorBairro devolve lista
de registros. atualizar/definirDisponivel devolvem boolean: false indica que nao
houve o resultado solicitado, por exemplo ID ausente; violacoes de regras sao
excecoes explicativas. ID por si so continua sem comprovar autenticacao.

## Verificacao focada

Compilacao completa UTF-8 -Xlint:all -Werror aprovada. Higiene e diff conferidos.
EntregadorServiceIntegrationCheck: 65 verificacoes aprovadas, incluindo cinco
disputas com duas conexoes entre aceite e inativacao. Cada disputa confirmou
um vencedor e coerencia do vinculo/estado apos commit.

Cobertura: entradas invalidas sem conectar, consulta, perfil/complemento, dados
ausentes nao inventados, ID ausente, atualizacao, status recebido ignorado,
repeticao, CNH duplicada, usuario inativo, paginacao/bairro, estado NULL, bairro
ausente, EM_ROTA e ordens ativas mesmo com estado inconsistente. Falha SQL
induzida validou rollback e operacao posterior sem bloqueio abandonado; todas
as conexoes registradas foram conferidas como fechadas.

Na primeira execucao, o fixture tentou criar ordem depois de preparar a comanda;
foi corrigido para a sequencia exigida pelo DAO: criar ordem enquanto PAGO e
depois avancar preparo/coleta. A execucao integral seguinte passou. Nenhuma
mudanca no DAO foi feita para contornar a regra.

Banco UUID descartavel com DDL/FKs reais, removido no finally. Nenhum cadastro
original alterado. Triggers originais nao sao copiados automaticamente. Cinco
disputas nao comprovam todos os cenarios concorrentes. Nao houve teste completo
das telas, de troca de veiculo concorrente ao aceite ou de autorizacao de sessao.
Apoio em tests/local/EntregadorServiceIntegrationCheck.java, ignorado pelo Git.

## Fechamento desta sessao

UsuarioService, LojistaService e EntregadorService receberam entregas separadas.
Nao significa projeto encerrado: ainda faltam fluxo completo de entrega,
cancelamento, consultas/menus de compra, demais CRUDs e revisao final/UML.
Distribuicao da taxa entre entregas ainda requer decisao. Nenhuma tarefa extra
sera iniciada nesta sessao. Sem novo commit/push.

## Para estudar

1. Por que EM_ROTA nao pode virar INATIVO por um comando manual simples?
2. Por que precisamos consultar ordens mesmo quando o cadastro diz INATIVO?
3. Por que listar entregador disponivel nao equivale a reserva-lo para uma entrega?

PDF local: 30-EntregadorService.pdf.
