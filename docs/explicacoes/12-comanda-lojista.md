# Comanda do lojista

Arquivos incluidos: StatusComanda, ComandaLojistaRegistro e ComandaLojistaDao.

O registro valida IDs na faixa INT positiva e valores DECIMAL(10,2) sem arredondamento silencioso. Exige repasse = produtos - taxa retida, com valores nao negativos. A fabrica novaComanda inicia PAGO; o construtor preserva estados lidos do banco.

Fluxo aprovado: PAGO -> PREPARANDO -> AGUARDANDO_COLETA -> EM_TRANSITO -> CONCLUIDO. Cancelamento permitido somente em PAGO e PREPARANDO. Consultar permiteTransicaoPara nao modifica o banco.

O DAO insere e recupera ID/data, busca por ID, lista por pedido ou por lojista (paginado) e altera estado comparando o estado esperado no mesmo UPDATE. A data de status e atualizada pelo MySQL e nao preserva a data original da criacao. A listagem do lojista usa ID decrescente.

O service controla permissoes, pagamento e transacao com estoque/itens/entregas. A tabela nao possui UNIQUE para pedido/lojista: a protecao contra duplicidade ainda depende de implementar no service o bloqueio da linha do pedido mestre e a verificacao das comandas na mesma transacao. Todos os caminhos de criacao devem seguir esse protocolo. O DAO sozinho nao garante essa regra.

Verificacao: todos os fontes principais compilaram com Java 25 e -Xlint:all. Passaram 51 verificacoes em memoria, incluindo a matriz de transicoes e inconsistencias financeiras. Verificador local em target/comanda-check. Nao foi executado teste JDBC deste DAO; nenhum dado ou schema do banco foi alterado.

Proximo arquivo: ItemPedidoDao, apos confirmar a estrutura SQL de item_pedido e adaptar seu registro ao banco real.
