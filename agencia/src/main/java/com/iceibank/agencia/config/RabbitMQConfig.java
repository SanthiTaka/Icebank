package com.iceibank.agencia.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "iceibank.eventos";

    @Bean
    public TopicExchange exchangeIceibank() {
        return new TopicExchange(
                EXCHANGE,
                true,
                false
        );
    }

    @Bean
    public Queue filaAgencia(
            @Value("${server.port:4093}") int porta
    ) {

        int idAgencia =
                porta - AgenciaConfig.PORTA_BASE;

        return QueueBuilder
                .durable(
                        "fila-agencia-" + idAgencia
                )
                .build();
    }

    @Bean
    public Binding bindingAgencia(
            Queue filaAgencia,
            TopicExchange exchangeIceibank,
            @Value("${server.port:4093}") int porta
    ) {

        int idAgencia =
                porta - AgenciaConfig.PORTA_BASE;

        String routingKey =
                "agencia."
                + idAgencia
                + ".creditar";

        return BindingBuilder
                .bind(filaAgencia)
                .to(exchangeIceibank)
                .with(routingKey);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}