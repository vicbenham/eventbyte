package com.easy.eventbyte.exposition;

import com.easy.eventbyte.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class StrapiEventListener {

    @RabbitListener(queues = RabbitMQConfig.QUEUE)
    public void onMessage(String body, @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        log.info("body: {}, routingKey: {}", body, routingKey);
    }
}
