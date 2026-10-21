CREATE DATABASE IF NOT EXISTS nexus_db 
USE nexus_db;

CREATE TABLE usuario (
    id_usuario INT AUTO_INCREMENT,
    nome_razao_social VARCHAR(120) NOT NULL,
    documento_fiscal VARCHAR(18) NOT NULL,
    email VARCHAR(100) NOT NULL,
    senha_hash CHAR(60) NOT NULL,
    tipo_perfil ENUM('CLIENTE', 'LOJISTA', 'ENTREGADOR', 'ADMIN') NOT NULL,
    data_cadastro TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status_ativo BOOLEAN DEFAULT TRUE,
    CONSTRAINT pk_usuario PRIMARY KEY (id_usuario),
    CONSTRAINT uk_usuario_doc UNIQUE (documento_fiscal),
    CONSTRAINT uk_usuario_email UNIQUE (email)
) ENGINE=InnoDB;

CREATE TABLE lojista (
    id_lojista INT,
    nome_fantasia VARCHAR(120) NOT NULL,
    taxa_comissao_pct DECIMAL(5,2) NOT NULL,
    bairro_operacao VARCHAR(100) NOT NULL,
    endereco_origem VARCHAR(255) NOT NULL,
    CONSTRAINT pk_lojista PRIMARY KEY (id_lojista),
    CONSTRAINT fk_lojista_usuario FOREIGN KEY (id_lojista) REFERENCES usuario(id_usuario) ON DELETE CASCADE,
    CONSTRAINT chk_comissao CHECK (taxa_comissao_pct BETWEEN 0.00 AND 100.00)
) ENGINE=InnoDB;

CREATE TABLE entregador (
    id_entregador INT,
    cnh VARCHAR(20) NOT NULL,
    tipo_veiculo ENUM('BICICLETA', 'MOTO', 'CARRO', 'VAN') NOT NULL,
    status_operacao ENUM('DISPONIVEL', 'EM_ROTA', 'INATIVO') DEFAULT 'DISPONIVEL',
    bairro_operacao VARCHAR(100) NULL,
    CONSTRAINT pk_entregador PRIMARY KEY (id_entregador),
    CONSTRAINT fk_entregador_usuario FOREIGN KEY (id_entregador) REFERENCES usuario(id_usuario) ON DELETE CASCADE,
    CONSTRAINT uk_entregador_cnh UNIQUE (cnh)
) ENGINE=InnoDB;

CREATE TABLE produto (
    id_produto INT AUTO_INCREMENT,
    id_lojista INT NOT NULL,
    nome VARCHAR(120) NOT NULL,
    descricao TEXT,
    preco_unitario DECIMAL(10,2) NOT NULL,
    peso_kg DECIMAL(6,3) NOT NULL,
    estoque_disponivel INT NOT NULL DEFAULT 0,
    status_ativo BOOLEAN DEFAULT TRUE,
    CONSTRAINT pk_produto PRIMARY KEY (id_produto),
    CONSTRAINT fk_produto_lojista FOREIGN KEY (id_lojista) REFERENCES lojista(id_lojista) ON DELETE RESTRICT,
    CONSTRAINT chk_preco CHECK (preco_unitario > 0.00),
    CONSTRAINT chk_peso CHECK (peso_kg > 0.000),
    CONSTRAINT chk_estoque CHECK (estoque_disponivel >= 0)
) ENGINE=InnoDB;

CREATE TABLE pedido (
    id_pedido INT AUTO_INCREMENT,
    id_cliente INT NOT NULL,
    data_criacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    valor_produtos DECIMAL(10,2) NOT NULL,
    valor_frete DECIMAL(10,2) NOT NULL,
    taxa_plataforma DECIMAL(10,2) NOT NULL,
    valor_total DECIMAL(10,2) NOT NULL,
    status_pedido ENUM('CRIADO', 'PAGO', 'PREPARANDO', 'DESPACHADO', 'ENTREGUE', 'CANCELADO') DEFAULT 'CRIADO',
    CONSTRAINT pk_pedido PRIMARY KEY (id_pedido),
    CONSTRAINT fk_pedido_cliente FOREIGN KEY (id_cliente) REFERENCES usuario(id_usuario) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE item_pedido (
    id_item INT AUTO_INCREMENT,
    id_pedido INT NOT NULL,
    id_produto INT NOT NULL,
    quantidade INT NOT NULL,
    preco_unitario_aplicado DECIMAL(10,2) NOT NULL,
    observacao VARCHAR(255) NULL,
    CONSTRAINT pk_item PRIMARY KEY (id_item),
    CONSTRAINT fk_item_pedido FOREIGN KEY (id_pedido) REFERENCES pedido(id_pedido) ON DELETE CASCADE,
    CONSTRAINT fk_item_produto FOREIGN KEY (id_produto) REFERENCES produto(id_produto) ON DELETE RESTRICT,
    CONSTRAINT chk_item_qtd CHECK (quantidade > 0)
) ENGINE=InnoDB;

CREATE TABLE ordem_entrega (
    id_entrega INT AUTO_INCREMENT,
    id_pedido INT NOT NULL,
    id_entregador INT NULL,
    modalidade_frete VARCHAR(30) NOT NULL,
    distancia_km DECIMAL(6,2) NOT NULL,
    valor_repasse_entregador DECIMAL(10,2) NOT NULL,
    status_entrega ENUM('AGUARDANDO_COLETA', 'COLETADO', 'EM_TRANSITO', 'CONCLUIDO', 'FALHA') DEFAULT 'AGUARDANDO_COLETA',
    CONSTRAINT pk_entrega PRIMARY KEY (id_entrega),
    CONSTRAINT uk_entrega_pedido UNIQUE (id_pedido),
    CONSTRAINT fk_entrega_pedido FOREIGN KEY (id_pedido) REFERENCES pedido(id_pedido) ON DELETE CASCADE,
    CONSTRAINT fk_entrega_entregador FOREIGN KEY (id_entregador) REFERENCES entregador(id_entregador) ON DELETE SET NULL,
    CONSTRAINT chk_distancia CHECK (distancia_km > 0.00)
) ENGINE=InnoDB;

CREATE TABLE pagamento (
    id_pagamento INT AUTO_INCREMENT,
    id_pedido INT NOT NULL,
    metodo_pagamento ENUM('PIX', 'CARTAO_CREDITO', 'BOLETO') NOT NULL,
    valor_processado DECIMAL(10,2) NOT NULL,
    codigo_autorizacao VARCHAR(100) NOT NULL,
    data_processamento TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status_pagamento ENUM('PENDENTE', 'AUTORIZADO', 'RECUSADO') DEFAULT 'PENDENTE',
    CONSTRAINT pk_pagamento PRIMARY KEY (id_pagamento),
    CONSTRAINT uk_pagamento_pedido UNIQUE (id_pedido),
    CONSTRAINT fk_pagamento_pedido FOREIGN KEY (id_pedido) REFERENCES pedido(id_pedido) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE auditoria_pedido (
    id_auditoria INT AUTO_INCREMENT PRIMARY KEY,
    id_pedido INT NOT NULL,
    status_anterior VARCHAR(20),
    status_novo VARCHAR(20),
    data_alteracao TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    usuario_db VARCHAR(50)
) ENGINE=InnoDB;

DELIMITER $$
CREATE TRIGGER trg_audita_status_pedido
AFTER UPDATE ON pedido
FOR EACH ROW
BEGIN
    IF OLD.status_pedido <> NEW.status_pedido THEN
        INSERT INTO auditoria_pedido (id_pedido, status_anterior, status_novo, usuario_db)
        VALUES (OLD.id_pedido, OLD.status_pedido, NEW.status_pedido, USER());
    END IF;
END$$
DELIMITER ;
