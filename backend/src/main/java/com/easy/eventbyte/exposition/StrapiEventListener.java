package com.easy.eventbyte.exposition;

import com.easy.eventbyte.config.RabbitMQConfig;
import com.easy.eventbyte.dto.StrapiEventMessage;
import com.easy.eventbyte.service.EventIngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;


@Component
@RequiredArgsConstructor
public class StrapiEventListener {

    private final JsonMapper jsonMapper;
    private final EventIngestionService eventIngestionService;

    @RabbitListener(queues = RabbitMQConfig.QUEUE)
    public void onMessage(Message message) {

        StrapiEventMessage event = jsonMapper.readValue(message.getBody(), StrapiEventMessage.class);
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();

        switch (routingKey) {
            case "event.created", "event.updated" -> eventIngestionService.save(event);
            case "event.deleted" -> eventIngestionService.delete(event.id());
            case null -> {}
            default -> throw new AmqpRejectAndDontRequeueException("Routing key non gérée : " + routingKey);
        }
    }
}
