# PedidoMestreDao

Implementa o acesso a pedido_mestre conforme o SHOW CREATE TABLE fornecido pelo usuario.

- inserir recebe um registro novo (ID/data nulos, estado CRIADO) e retorna outro registro com ID e data gerados pelo banco.
- buscarPorId retorna Optional vazio quando nao encontra a compra.
- listarPorCliente usa paginacao de 1 a 100 resultados, ordenando por data e ID decrescentes.
- atualizarStatus compara o estado esperado no proprio UPDATE. Retorno false indica ID ausente ou estado divergente; erros SQL continuam sendo excecoes.

O service deve autorizar o acesso, verificar o perfil CLIENTE, calcular valores e validar transicoes. A chave estrangeira confere existencia do usuario, nao seu perfil.

O chamador controla a Connection e commit/rollback. A insercao e sua leitura posterior devem participar da transacao de compra para permitir reversao em falha. Marcar PAGO nao processa um pagamento.

PreparedStatement separa SQL e valores. O mapeamento preserva data nula e converte valores monetarios com BigDecimal. A aplicacao ainda precisa padronizar o fuso das conexoes para interpretar TIMESTAMP de forma consistente.

Verificacao: todos os fontes principais compilados com Java 25 e -Xlint:all. Nao foi executado teste JDBC deste DAO nem alterado o banco. Proximo arquivo: ComandaLojistaDao, mediante confirmacao de sua estrutura SQL.
