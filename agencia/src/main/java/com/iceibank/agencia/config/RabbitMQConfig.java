package com.iceibank.agencia.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
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

    // =====================================================
    // FILA DE CRÉDITOS
    // =====================================================

    @Bean
    public Queue filaAgenciaCredito(
            @Value("${server.port:4093}") int porta
    ) {

        int idAgencia = porta - AgenciaConfig.PORTA_BASE;

        return QueueBuilder
                .durable(
                        "fila-agencia-" + idAgencia
                )
                .build();
    }

    @Bean
    public Binding bindingCredito(
            @Qualifier("filaAgenciaCredito") Queue fila,
            TopicExchange exchangeIceibank,
            @Value("${server.port:4093}") int porta
    ) {

        int idAgencia = porta - AgenciaConfig.PORTA_BASE;

        String routingKey =
                "agencia."
                        + idAgencia
                        + ".creditar";

        return BindingBuilder
                .bind(fila)
                .to(exchangeIceibank)
                .with(routingKey);
    }

    // =====================================================
    // FILA DE CONFIRMAÇÕES
    // =====================================================

    @Bean
    public Queue filaAgenciaConfirmacao(
            @Value("${server.port:4093}") int porta
    ) {

        int idAgencia = porta - AgenciaConfig.PORTA_BASE;

        return QueueBuilder
                .durable(
                        "fila-confirmacao-agencia-"
                                + idAgencia
                )
                .build();
    }

    @Bean
    public Binding bindingConfirmacao(
            @Qualifier("filaAgenciaConfirmacao") Queue fila,
            TopicExchange exchangeIceibank,
            @Value("${server.port:4093}") int porta
    ) {

        int idAgencia = porta - AgenciaConfig.PORTA_BASE;

        String routingKey =
                "agencia."
                        + idAgencia
                        + ".confirmacao";

        return BindingBuilder
                .bind(fila)
                .to(exchangeIceibank)
                .with(routingKey);
    }

    // =====================================================
    // CONVERSOR JSON
    // =====================================================

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}