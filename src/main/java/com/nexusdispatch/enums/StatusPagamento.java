package com.nexusdispatch.enums;
/** Estados do SQL; transicoes e novas tentativas sao coordenadas pelo service. */
// PENDENTE aguarda o simulador; AUTORIZADO confirma o resultado positivo;
// RECUSADO permite tentar novamente segundo CompraService; ESTORNADO registra
// o cancelamento integral simulado. O enum lista estados, mas nao executa transicoes.
public enum StatusPagamento { PENDENTE, AUTORIZADO, RECUSADO, ESTORNADO }
