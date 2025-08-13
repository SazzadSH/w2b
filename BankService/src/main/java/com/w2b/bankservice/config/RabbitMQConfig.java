package com.w2b.bankservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    public static final String BANK_TRANSFER_QUEUE = "bank_transfer_queue";
    public static final String BANK_TRANSFER_EXCHANGE = "bank_transfer_exchange";
    public static final String BANK_TRANSFER_ROUTING_KEY = "bank_transfer_routing_key";

    @Bean
    public Queue queue () {
        return new Queue(BANK_TRANSFER_QUEUE, true);
    }

    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(BANK_TRANSFER_EXCHANGE);
    }

    @Bean
    public Binding binding(Queue queue, DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(BANK_TRANSFER_ROUTING_KEY);
    }
}
