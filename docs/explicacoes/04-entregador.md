# EntregadorRegistro e StatusOperacaoEntregador

Implementado em 27/10/2026 apos consulta somente de leitura com SHOW CREATE TABLE
no MySQL local. Nenhuma tabela ou cadastro real foi alterado nesta entrega.

## ID e estrutura confirmada

usuario.id_usuario e INT AUTO_INCREMENT. entregador.id_entregador e INT,
chave primaria e FK para usuario.id_usuario, com ON DELETE CASCADE. Nao possui
AUTO_INCREMENT. Primeiro o futuro service insere o usuario ENTREGADOR, recebe
seu ID gerado e utiliza esse mesmo ID para inserir o entregador.

| Coluna real | Componente Java | Regra |
|---|---|---|
| id_entregador | long id | Positivo, ate Integer.MAX_VALUE; usuario existente |
| cnh | String cnh | Numero do documento, ate 20 pontos de codigo; opcional so para bicicleta |
| tipo_veiculo | TipoVeiculo tipoVeiculo | BICICLETA, MOTO, CARRO ou VAN; obrigatorio |
| status_operacao | StatusOperacaoEntregador statusOperacao | DISPONIVEL, EM_ROTA, INATIVO ou null conforme SQL |
| bairro_operacao | String bairroOperacao | Texto de ate 100 pontos de codigo; SQL aceita null |

cnh NAO e CategoriaCnh. A tabela exige unicidade do numero por UNIQUE;
este record isolado nao consulta duplicidade nem verifica autenticidade.

## Construcao e leitura

O construtor compacto valida ID, veiculo, obrigatoriedade da CNH e comprimentos.
Remove espacos externos; texto vazio vira null. Nao trunca valores.
Aceita bairro/status ausentes para representar registros permitidos pelo SQL.

novoCadastro exige bairro-base e define INATIVO explicitamente. Isso preserva
a regra Java de inicio indisponivel. O DEFAULT do banco e DISPONIVEL: o futuro
DAO deve gravar o status do record, sem omitir a coluna. Null nunca autoriza
aceite de entregas. Transicoes e autorizacao ficam para services posteriores.

O record fornece acessores como id(), cnh() e statusOperacao(). Nao ha setters.
Nao herda Usuario e nao duplica nome, e-mail ou senha. Nao executa SQL.

## Diferencas em relacao a proposta inicial

A proposta do chat usava idBairroBase, CategoriaCnh, pesoBrutoTotalKg,
capacidadePassageiros e boolean disponivel. O SQL consultado nao tem esses campos.
Por isso o registro final mapeia a estrutura real, com bairro em texto e estado
operacional em enum. Nao foi criada tabela de bairros nem aplicada migracao.

Entregador.java permanece com suas regras de CNH e van, sem alteracoes.
Categoria, PBT e capacidade ainda nao podem ser reconstruidos apenas pela linha
SQL. Antes de um cadastro completo integrado, decidir e implementar a migracao
correspondente, com o grupo. Nao simular esses dados nem descartá-los em silencio.
Nenhuma regra de veiculo foi duplicada: o registro confere somente o que a tabela
atual consegue representar, incluindo CNH obrigatoria para van.

A selecao por bairro-base/proximidade ainda nao foi implementada. O texto atual
nao garante existencia de bairro nem proximidade; depende de evolucao do esquema
ou de um catalogo controlado no service. Bairro-base e bairro_operacao neste recorte.

## Validacao executada

- Compilacao de todos os fontes principais com Java 25, UTF-8 e -Xlint:all.
- EntregadorRegistroCheck: 32 verificacoes em memoria, incluindo quatro veiculos,
  CNH ausente, limites INT/Unicode, estados SQL, null e inicio INATIVO.
- RunChecks.ps1: verificacoes anteriores passaram, mantendo diagnosticos PENDENTE.
- CheckSourceHygiene.ps1: passou na copia sem Git; repetir na branch real.

O novo teste fica em tests/local, ignorado pelo Git, seguindo a organizacao atual.
Nao houve teste de persistencia de entregador: EntregadorDao ainda sera implementado.

## Proximo arquivo

EntregadorDao.java: receber a Connection do chamador, vincular parametros SQL,
mapear status sem confundir INATIVO/EM_ROTA/DISPONIVEL, tratar NULL explicitamente,
e nao gerar outro ID. Cadastro de usuario e entregador devera ser transacional
no futuro service. O DAO nao deve fechar a Connection nem executar commit proprio.
