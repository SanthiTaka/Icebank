package com.iceibank.agencia.services;

import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import com.iceibank.agencia.config.RabbitMQConfig;
import com.iceibank.agencia.model.MensagemConfirmacao;
import com.iceibank.agencia.model.MensagemCredito;

@Service
public class MensageriaService {

    private final RabbitTemplate rabbitTemplate;

    public MensageriaService(
            RabbitTemplate rabbitTemplate
    ) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publicarCredito(
            int agenciaDestino,
            MensagemCredito mensagem
    ) {

        String routingKey =
                "agencia."
                        + agenciaDestino
                        + ".creditar";

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                routingKey,
                mensagem,
                message -> {
                    message.getMessageProperties()
                            .setDeliveryMode(
                                    MessageDeliveryMode.PERSISTENT
                            );

                    return message;
                }
        );

        System.out.println(
                "[RabbitMQ] Crédito publicado para "
                        + routingKey
        );
    }

    public void publicarConfirmacao(
            int agenciaOrigem,
            MensagemConfirmacao mensagem
    ) {

        String routingKey =
                "agencia."
                        + agenciaOrigem
                        + ".confirmacao";

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                routingKey,
                mensagem,
                message -> {
                    message.getMessageProperties()
                            .setDeliveryMode(
                                    MessageDeliveryMode.PERSISTENT
                            );

                    return message;
                }
        );

        System.out.println(
                "[RabbitMQ] Confirmação publicada para "
                        + routingKey
        );
    }
}