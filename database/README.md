# Scripts do banco

O grupo mantém o [DDL base](../nexus-db_ddl_schema.sql) e a [atualização V6.1](../V2__patch_v6_1) na raiz. O código Java usa as tabelas da V6.1, incluindo `pedido_mestre` e `comanda_lojista`. Confira a versão do catálogo antes de aplicar migrações. Para configurar o Java, use o `.env.example` da raiz como referência.

`001-pagamento-estornado.sql` atualiza um banco existente para aceitar `ESTORNADO` em `pagamento.status_pagamento`. Execute no catálogo do projeto antes de usar o cancelamento de compras pagas. A migração preserva os estados anteriores e o valor padrão `PENDENTE`.

O esquema dos catálogos descartáveis da suíte Maven fica em [src/test/resources/esquema-mysql-teste.sql](../src/test/resources/esquema-mysql-teste.sql).
