package com.iceibank.agencia.services;

import java.io.IOException;
import java.util.Map;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import com.iceibank.agencia.model.MensagemConfirmacao;

@Service
public class ConsumidorConfirmacaoService {

    private final RelogioVetorial relogioVetorial;
    private final RegistroEventos registroEventos;

    public ConsumidorConfirmacaoService(
            RelogioVetorial relogioVetorial,
            RegistroEventos registroEventos
    ) {
        this.relogioVetorial = relogioVetorial;
        this.registroEventos = registroEventos;
    }

    @RabbitListener(
            queues = "#{filaAgenciaConfirmacao.name}"
    )
    public void receberConfirmacao(
            MensagemConfirmacao mensagem
    ) throws IOException {

        int[] timestampVetorial =
                relogioVetorial.aoReceber(
                        mensagem.getVetorEnvio()
                );

        registroEventos.registrar(
                "CONFIRMACAO_CREDITO_RECEBIDA",
                timestampVetorial,
                Map.of(
                        "idConta",
                        mensagem.getIdConta(),

                        "valor",
                        mensagem.getValor(),

                        "agenciaDestino",
                        mensagem.getAgenciaDestino()
                )
        );

        System.out.println(
                "[RabbitMQ] Confirmação recebida: "
                        + "crédito de R$ "
                        + mensagem.getValor()
                        + " aplicado na conta "
                        + mensagem.getIdConta()
        );
    }
}