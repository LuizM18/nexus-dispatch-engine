-- Estrutura MySQL 8.4 para os testes de integração do Nexus Dispatch.

-- Identidade comum dos perfis. UNIQUE em documento/email e garantia do banco;
-- o perfil sozinho nao cria os complementos de lojista ou entregador.
CREATE TABLE `usuario` (
  `id_usuario` int NOT NULL AUTO_INCREMENT,
  `nome_razao_social` varchar(120) NOT NULL,
  `documento_fiscal` varchar(18) NOT NULL,
  `email` varchar(100) NOT NULL,
  `senha_hash` char(60) NOT NULL,
  `tipo_perfil` enum('CLIENTE','LOJISTA','ENTREGADOR','ADMIN') NOT NULL,
  `data_cadastro` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `status_ativo` tinyint(1) DEFAULT '1',
  PRIMARY KEY (`id_usuario`),
  UNIQUE KEY `uk_usuario_doc` (`documento_fiscal`),
  UNIQUE KEY `uk_usuario_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Vinculo de autoria preserva a identidade de quem participou de um evento.
-- A exclusao protegida de usuario considera esse historico, mesmo sem compras.
CREATE TABLE `auditoria_sistema` (
  `id_auditoria` int NOT NULL AUTO_INCREMENT,
  `tabela_afetada` varchar(50) NOT NULL,
  `id_registro_afetado` int NOT NULL,
  `id_usuario_ator` int DEFAULT NULL,
  `acao` enum('INSERT','UPDATE','SOFT_DELETE') NOT NULL,
  `dados_anteriores` json DEFAULT NULL,
  `dados_novos` json DEFAULT NULL,
  `ip_origem` varchar(45) DEFAULT '127.0.0.1',
  `data_hora_acao` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_auditoria`),
  KEY `fk_auditoria_usuario` (`id_usuario_ator`),
  CONSTRAINT `fk_auditoria_usuario` FOREIGN KEY (`id_usuario_ator`) REFERENCES `usuario` (`id_usuario`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Chave compartilhada com usuario: nao ha AUTO_INCREMENT neste complemento.
-- Comissao e percentual; os valores monetarios de cada venda ficam na comanda.
CREATE TABLE `lojista` (
  `id_lojista` int NOT NULL,
  `nome_fantasia` varchar(120) NOT NULL,
  `taxa_comissao_pct` decimal(5,2) NOT NULL,
  `bairro_operacao` varchar(100) NOT NULL,
  `endereco_origem` varchar(255) NOT NULL,
  PRIMARY KEY (`id_lojista`),
  CONSTRAINT `fk_lojista_usuario` FOREIGN KEY (`id_lojista`) REFERENCES `usuario` (`id_usuario`) ON DELETE CASCADE,
  CONSTRAINT `chk_comissao` CHECK ((`taxa_comissao_pct` between 0.00 and 100.00))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Cadastro comercial e saldo atual por lojista. CHECKs protegem limites locais;
-- a baixa concorrente tambem depende do UPDATE condicional executado pelo DAO.
CREATE TABLE `produto` (
  `id_produto` int NOT NULL AUTO_INCREMENT,
  `id_lojista` int NOT NULL,
  `nome` varchar(120) NOT NULL,
  `categoria` varchar(50) NOT NULL DEFAULT 'OUTROS',
  `descricao` text,
  `preco_unitario` decimal(10,2) NOT NULL,
  `peso_kg` decimal(6,3) NOT NULL,
  `estoque_disponivel` int NOT NULL DEFAULT '0',
  `status_ativo` tinyint(1) DEFAULT '1',
  PRIMARY KEY (`id_produto`),
  KEY `fk_produto_lojista` (`id_lojista`),
  CONSTRAINT `fk_produto_lojista` FOREIGN KEY (`id_lojista`) REFERENCES `lojista` (`id_lojista`) ON DELETE RESTRICT,
  CONSTRAINT `chk_estoque` CHECK ((`estoque_disponivel` >= 0)),
  CONSTRAINT `chk_peso` CHECK ((`peso_kg` > 0.000)),
  CONSTRAINT `chk_preco` CHECK ((`preco_unitario` > 0.00))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Compra inteira: cliente e totais agregados. A taxa e a diferenca entre total
-- e produtos; as partes por loja ficam em comanda_lojista.
CREATE TABLE `pedido_mestre` (
  `id_pedido` int NOT NULL AUTO_INCREMENT,
  `id_cliente` int NOT NULL,
  `data_criacao` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `valor_produtos` decimal(10,2) NOT NULL,
  `valor_total` decimal(10,2) NOT NULL,
  `status_pedido` enum('CRIADO','PAGO','CONCLUIDO','CANCELADO') NOT NULL DEFAULT 'CRIADO',
  PRIMARY KEY (`id_pedido`),
  KEY `fk_pedido_cliente` (`id_cliente`),
  CONSTRAINT `fk_pedido_cliente` FOREIGN KEY (`id_cliente`) REFERENCES `usuario` (`id_usuario`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Parte da compra por loja, com comissao e repasse historicos. A data muda com
-- o registro por ON UPDATE; nao representa necessariamente a data de criacao.
CREATE TABLE `comanda_lojista` (
  `id_comanda` int NOT NULL AUTO_INCREMENT,
  `id_pedido` int NOT NULL,
  `id_lojista` int NOT NULL,
  `valor_produtos` decimal(10,2) NOT NULL,
  `taxa_venda_retida` decimal(10,2) NOT NULL,
  `repasse_lojista` decimal(10,2) NOT NULL,
  `status_comanda` enum('PAGO','PREPARANDO','AGUARDANDO_COLETA','EM_TRANSITO','CONCLUIDO','CANCELADO') NOT NULL DEFAULT 'PAGO',
  `data_hora_status` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_comanda`),
  KEY `fk_comanda_pedido_mestre` (`id_pedido`),
  KEY `fk_comanda_lojista` (`id_lojista`),
  CONSTRAINT `fk_comanda_lojista` FOREIGN KEY (`id_lojista`) REFERENCES `lojista` (`id_lojista`),
  CONSTRAINT `fk_comanda_pedido_mestre` FOREIGN KEY (`id_pedido`) REFERENCES `pedido_mestre` (`id_pedido`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Guarda quantidade e preco aplicado, sem copiar nome/descricao historicos.
-- O default textual da observacao e distinto do NULL enviado explicitamente pelo DAO.
CREATE TABLE `item_pedido` (
  `id_item` int NOT NULL AUTO_INCREMENT,
  `id_comanda` int NOT NULL,
  `id_produto` int NOT NULL,
  `quantidade` int NOT NULL,
  `preco_unitario_aplicado` decimal(10,2) NOT NULL,
  `observacao` varchar(255) DEFAULT 'Nenhuma',
  PRIMARY KEY (`id_item`),
  KEY `fk_item_produto` (`id_produto`),
  KEY `fk_item_comanda` (`id_comanda`),
  CONSTRAINT `fk_item_comanda` FOREIGN KEY (`id_comanda`) REFERENCES `comanda_lojista` (`id_comanda`) ON DELETE CASCADE,
  CONSTRAINT `fk_item_produto` FOREIGN KEY (`id_produto`) REFERENCES `produto` (`id_produto`) ON DELETE RESTRICT,
  CONSTRAINT `chk_item_qtd` CHECK ((`quantidade` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- UNIQUE por pedido permite uma unica linha, reutilizada nas novas tentativas.
-- ESTORNADO registra o estorno simulado; a tabela nao representa uma integracao bancaria.
CREATE TABLE `pagamento` (
  `id_pagamento` int NOT NULL AUTO_INCREMENT,
  `id_pedido` int NOT NULL,
  `metodo_pagamento` enum('PIX','CARTAO_CREDITO','BOLETO') NOT NULL,
  `valor_processado` decimal(10,2) NOT NULL,
  `codigo_autorizacao` varchar(100) NOT NULL,
  `data_processamento` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `status_pagamento` enum('PENDENTE','AUTORIZADO','RECUSADO','ESTORNADO') DEFAULT 'PENDENTE',
  PRIMARY KEY (`id_pagamento`),
  UNIQUE KEY `uk_pagamento_pedido` (`id_pedido`),
  CONSTRAINT `fk_pagamento_pedido_mestre` FOREIGN KEY (`id_pedido`) REFERENCES `pedido_mestre` (`id_pedido`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Complemento com numero da CNH, veiculo e estado operacional. O default SQL
-- DISPONIVEL e preservado neste esquema; CadastroService/DAO enviam INATIVO
-- explicitamente para cumprir a regra de novos cadastros.
CREATE TABLE `entregador` (
  `id_entregador` int NOT NULL,
  `cnh` varchar(20) DEFAULT NULL,
  `tipo_veiculo` enum('BICICLETA','MOTO','CARRO','VAN') NOT NULL,
  `status_operacao` enum('DISPONIVEL','EM_ROTA','INATIVO') DEFAULT 'DISPONIVEL',
  `bairro_operacao` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id_entregador`),
  UNIQUE KEY `uk_entregador_cnh` (`cnh`),
  CONSTRAINT `fk_entregador_usuario` FOREIGN KEY (`id_entregador`) REFERENCES `usuario` (`id_usuario`) ON DELETE CASCADE,
  CONSTRAINT `chk_entregador_cnh_obrigatoria` CHECK (((`tipo_veiculo` = _utf8mb4'BICICLETA') or ((`cnh` is not null) and (char_length(trim(`cnh`)) > 0))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Vincula entrega a comanda e, apos aceite, a um entregador. SET NULL permite
-- preservar a ordem sem esse vinculo. Sem UNIQUE em id_comanda, a unicidade
-- por comanda depende tambem do bloqueio e verificacao feitos pelo DAO.
CREATE TABLE `ordem_entrega` (
  `id_entrega` int NOT NULL AUTO_INCREMENT,
  `id_comanda` int NOT NULL,
  `id_entregador` int DEFAULT NULL,
  `modalidade_frete` varchar(30) NOT NULL,
  `distancia_km` decimal(6,2) NOT NULL,
  `valor_repasse_entregador` decimal(10,2) NOT NULL,
  `status_entrega` enum('AGUARDANDO_COLETA','EM_TRANSITO','ENTREGUE','DEVOLVIDO','CANCELADO') NOT NULL DEFAULT 'AGUARDANDO_COLETA',
  `taxa_frete_retida` decimal(10,2) NOT NULL,
  `data_hora_coleta` timestamp NULL DEFAULT NULL,
  `data_hora_entrega` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id_entrega`),
  KEY `fk_entrega_entregador` (`id_entregador`),
  KEY `fk_ordem_comanda` (`id_comanda`),
  CONSTRAINT `fk_entrega_entregador` FOREIGN KEY (`id_entregador`) REFERENCES `entregador` (`id_entregador`) ON DELETE SET NULL,
  CONSTRAINT `fk_ordem_comanda` FOREIGN KEY (`id_comanda`) REFERENCES `comanda_lojista` (`id_comanda`) ON DELETE CASCADE,
  CONSTRAINT `chk_distancia` CHECK ((`distancia_km` > 0.00))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
