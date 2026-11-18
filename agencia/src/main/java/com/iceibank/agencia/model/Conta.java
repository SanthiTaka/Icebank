package com.iceibank.agencia.model;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class Conta {

    private Long id;
    private String titular;
    private double saldo;
    private final List<Map<String, Object>> transacoes = new CopyOnWriteArrayList<>();

    public Conta() {
    }

    public Conta(Long id, String titular, double saldo) {
        this.id = id;
        this.titular = titular;
        this.saldo = saldo;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitular() {
        return titular;
    }

    public void setTitular(String titular) {
        this.titular = titular;
    }

    public double getSaldo() {
        return saldo;
    }

    public void setSaldo(double saldo) {
        this.saldo = saldo;
    }

    public List<Map<String, Object>> getTransacoes() {
        return transacoes;
    }

    public void adicionarTransacao(Map<String, Object> transacao) {
        this.transacoes.add(transacao);
    }
}
