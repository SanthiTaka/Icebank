package com.iceibank.agencia.model;

public class MensagemConfirmacao {

    private Long idConta;
    private double valor;
    private int agenciaDestino;
    private int[] vetorEnvio;

    public MensagemConfirmacao() {
    }

    public MensagemConfirmacao(
            Long idConta,
            double valor,
            int agenciaDestino,
            int[] vetorEnvio
    ) {
        this.idConta = idConta;
        this.valor = valor;
        this.agenciaDestino = agenciaDestino;
        this.vetorEnvio = vetorEnvio;
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

    public int getAgenciaDestino() {
        return agenciaDestino;
    }

    public void setAgenciaDestino(int agenciaDestino) {
        this.agenciaDestino = agenciaDestino;
    }

    public int[] getVetorEnvio() {
        return vetorEnvio;
    }

    public void setVetorEnvio(int[] vetorEnvio) {
        this.vetorEnvio = vetorEnvio;
    }
}