package com.easy.eventbyte.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "events";
    public static final String QUEUE = "eventbyte.backend.events";

    @Bean
    TopicExchange eventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    Queue eventsQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange("")
                .deadLetterRoutingKey(QUEUE + ".dlq")
                .build();
    }

    @Bean
    Queue eventsDeadLetterQueue() {
        return QueueBuilder.durable(QUEUE + ".dlq").build();
    }

    @Bean
    Binding eventsBinding(Queue eventsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(eventsQueue).to(eventsExchange).with("event.*");
    }
}
