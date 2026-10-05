package com.iceibank.agencia.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.iceibank.agencia.config.AgenciaConfig;

@Component
public class RelogioVetorial {

    private final int idAgencia;
    private final int[] vetor;

    public RelogioVetorial(
            @Value("${server.port:4093}") int porta
    ) {
        this.idAgencia = porta - AgenciaConfig.PORTA_BASE;
        this.vetor = new int[AgenciaConfig.NUMERO_AGENCIAS];
    }

    public synchronized int[] eventoLocal() {
        vetor[idAgencia] += 1;
        return vetor.clone();
    }

    public synchronized int[] aoEnviar() {
        vetor[idAgencia] += 1;
        return vetor.clone();
    }

    public synchronized int[] aoReceber(int[] vetorRecebido) {

        for (int i = 0; i < vetor.length; i++) {
            vetor[i] = Math.max(
                    vetor[i],
                    vetorRecebido[i]
            );
        }

        vetor[idAgencia] += 1;

        return vetor.clone();
    }

    public synchronized int[] getVetorAtual() {
        return vetor.clone();
    }
}