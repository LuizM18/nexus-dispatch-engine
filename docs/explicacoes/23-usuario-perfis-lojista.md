# Usuario, perfis e lojista: primeira revisao

03/11/2026. Guia da alteracao Java, sem alteracao do banco ou dos DAOs.

## O que mudou e por que

Usuario passa a carregar ID, documento fiscal, data de cadastro e atividade de
UsuarioRegistro. A consulta continua sem senha_hash. Administrador e Consumidor
ganham construtores de reconstituicao; Lojista reune UsuarioRegistro e
LojistaRegistro, exigindo o mesmo ID e perfil LOJISTA. O documento fiscal vem
de usuario, sem supor uma tabela loja ou um segundo CNPJ.

Construtores antigos continuam disponiveis para compatibilidade com o dominio
anterior e seus testes. Esses objetos nao possuem identidade persistida e nao
devem ser apresentados como cadastros completos no banco. Telefone continua
apenas local, pois nao ha coluna correspondente.

## Usuario: metodos e contratos

- Usuario(UsuarioRegistro, PerfilUsuario): construtor protegido usado pelas
  subclasses. Confere ID INT positivo, perfil esperado, nome/email e documento
  de ate 18 caracteres. Preserva ID, data e atividade do banco. Nao chama metodo
  sobrescrito durante a construcao e nao inventa hash, telefone ou data atual.
- Usuario(nome, email, telefone, senhaHash): construtor protegido anterior para
  modelos locais. Mantem validacoes e hash recebido, sem autenticar o usuario.
- getPerfil(): metodo abstrato. Cada subclasse define seu PerfilUsuario.
- getDocumentoFiscal(): devolve documento persistido; null no modelo antigo.
- getDataCadastro(): devolve data persistida; NULL do banco permanece null.
- getAtivo(): Boolean que distingue TRUE, FALSE e NULL.
- isAtivo(): somente TRUE explicito e considerado ativo; nao grava no banco.
- toRegistro(): produz projecao sem credenciais usando getPerfil sobrescrito.
  Recusa objetos antigos sem ID/documento em vez de inventar identidade.
- getId/getNome/getEmail: consultam dados comuns, sem SQL.
- getTelefone/setTelefone: mantem dado somente local; nao ha persistencia.
- getSenhaHash(): preservado por compatibilidade; null na reconstituicao publica.
  Login futuro deve consultar/verificar credencial em fluxo separado.
- setNome(): valida antes de alterar; nao executa UPDATE automaticamente.
- validarNome/validarEmail: regras anteriores acrescidas dos limites SQL de
  120/100 caracteres. validarSenhaHash e normalizarTelefone seguem como antes.

Nao ha setter de perfil, ID ou documento: identidade e tipo do objeto nao devem
ser trocados arbitrariamente. Alteracoes persistentes continuam pelos DAOs/services.

## Consumidor e Administrador

Consumidor(UsuarioRegistro) exige CLIENTE; Administrador(UsuarioRegistro) exige
ADMIN. Ambos herdam os dados persistidos sem exigir senha ficticia.
getPerfil() e sobrescrito para CLIENTE/ADMIN, respectivamente.

Consumidor.getEndereco() pode retornar null quando reconstruido do banco, pois
o schema nao guarda esse endereco. O construtor antigo continua exigindo endereco;
setEndereco continua validando valor nao nulo. O fluxo que precisar de destino
deve solicita-lo e valida-lo explicitamente. Ausencia nao equivale a endereco vazio.

Administrador nao ganha permissoes por existir como objeto. Autorizar uma acao
continua sendo responsabilidade do service e da futura entrada autenticada.

## Lojista

Lojista(UsuarioRegistro, LojistaRegistro) compoe dados das duas tabelas. Recusa
perfil diferente de LOJISTA e IDs divergentes; complemento null e erro de entrada.
Consultas dos dois registros devem compartilhar uma transacao quando a operacao
exigir consistencia diante de alteracoes concorrentes.

getDadosLojista() retorna Optional do complemento imutavel: nome fantasia,
comissao, bairro e endereco de origem ficam acessiveis sem duplicar campos.
No construtor antigo, esse Optional e vazio; nao se fabrica taxa zero.
getPerfil() retorna LOJISTA e participa das operacoes comuns da superclasse.

Exemplo de reconstituicao (mesma Connection fornecida aos DAOs):

UsuarioRegistro usuario = usuarioDao.buscarPorId(id).orElseThrow();
LojistaRegistro dados = lojistaDao.buscarPorId(id).orElseThrow();
Lojista lojista = new Lojista(usuario, dados);
Usuario perfil = lojista;
PerfilUsuario tipo = perfil.getPerfil();
UsuarioRegistro consulta = perfil.toRegistro();

O despacho polimorfico escolhe Lojista.getPerfil(), mesmo com referencia Usuario.
toRegistro tambem usa essa sobrescrita; nao e apenas um enum solto sem uso.

## Entregador e limites desta etapa

Entregador recebe getPerfil() sobrescrito retornando ENTREGADOR. A reconstituicao
de seus dados especificos ainda nao foi implementada: SQL nao guarda categoria
da CNH, PBT e capacidade de passageiros, e disponibilidade booleana ainda difere
do estado operacional SQL. Nao criamos valores ficticios para fechar essa lacuna.

Loja permanece inalterada e requer revisao posterior. Nenhuma classe foi removida.
O checkout JDBC atual continua usando registros; nao foi migrado integralmente
para a hierarquia. Ainda falta integrar a apresentacao/menu a esses objetos.

## Validacao

RunChecks passou, incluindo UsuarioCheck com 180 verificacoes anteriores e
UsuarioPersistidoCheck com 34 novas verificacoes. Testados: round-trip da projecao,
perfil incorreto, ID divergente/invalido, documento vazio, ausencia de credenciais,
NULL de data/atividade, complemento do lojista, rejeicao de nome longo sem alterar
o estado anterior, compatibilidade do construtor antigo e despacho polimorfico.

Compilacao completa com UTF-8 -Xlint:all -Werror e higiene de fontes aprovadas.
CompraServiceIntegrationCheck ampliado: 44 verificacoes JDBC aprovadas em banco
UUID descartavel com DDL/FKs reais, incluindo reconstituicao de cliente e lojista
com os DAOs atuais. Banco descartavel removido ao final. Sem commit/push.

## Questoes para estudo

1. Por que um objeto consultado sem hash nao deve receber uma senha ficticia?
2. Como superclasse abstrata, sobrescrita e toRegistro demonstram polimorfismo?
3. Por que IDs iguais sao necessarios para reunir usuario e complemento lojista?

PDF de apoio: 23-Usuario-Perfis-Lojista.pdf. UML sera conferido quando disponivel.
