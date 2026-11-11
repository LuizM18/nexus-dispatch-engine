package com.nexusdispatch.enums;
/** Metodos aceitos pelo SQL. Todos sao simulados nesta versao. */
// O metodo registra a escolha do cliente, sem integrar banco ou operadora.
// PIX, CARTAO_CREDITO e BOLETO passam pelo mesmo simulador configurado no checkout.
public enum MetodoPagamento { PIX, CARTAO_CREDITO, BOLETO }
