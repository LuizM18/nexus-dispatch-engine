-- Executar no catalogo Nexus Dispatch. Mantem valores existentes, NULL e DEFAULT.
-- Acrescenta o estado usado pelo cancelamento integral simulado. Mantem os
-- valores anteriores do ENUM na mesma ordem e preserva NULL/default. A migracao
-- altera o contrato da coluna; nao estorna nem modifica o estado dos pagamentos existentes.
ALTER TABLE pagamento
    MODIFY COLUMN status_pagamento
        ENUM('PENDENTE','AUTORIZADO','RECUSADO','ESTORNADO') NULL DEFAULT 'PENDENTE';
