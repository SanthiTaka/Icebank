package com.iceibank.agencia.model;

public class MensagemCredito {

    private Long idConta;
    private double valor;
    private int[] vetorEnvio;
    private int origemAgencia;

    public MensagemCredito() {
    }

    public MensagemCredito(
            Long idConta,
            double valor,
            int[] vetorEnvio,
            int origemAgencia
    ) {
        this.idConta = idConta;
        this.valor = valor;
        this.vetorEnvio = vetorEnvio;
        this.origemAgencia = origemAgencia;
    }

    public Long getIdConta() {
        return idConta;
    }

    public void setIdConta(Long idConta) {
        this.idConta = idConta;
    }

    public double getValor() {
        return valor;
    }

    public void setValor(double valor) {
        this.valor = valor;
    }

    public int[] getVetorEnvio() {
        return vetorEnvio;
    }

    public void setVetorEnvio(int[] vetorEnvio) {
        this.vetorEnvio = vetorEnvio;
    }

    public int getOrigemAgencia() {
        return origemAgencia;
    }

    public void setOrigemAgencia(int origemAgencia) {
        this.origemAgencia = origemAgencia;
    }
}