package com.easy.eventbyte.config;

import com.easy.eventbyte.service.BookingEventPublisher;
import com.easy.eventbyte.service.BookingStreamService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

/** Relaie les changements de statut publiés sur Redis (par n'importe quelle instance) aux flux SSE de celle-ci. */
@Slf4j
@Configuration
public class BookingEventsConfig {

    @Bean
    RedisMessageListenerContainer bookingEventsListener(RedisConnectionFactory connectionFactory,
                                                        BookingStreamService streams) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener((message, pattern) -> {
            String[] userAndBooking = new String(message.getBody(), StandardCharsets.UTF_8).split(" ", 2);
            try {
                streams.push(userAndBooking[0], userAndBooking[1]);
            } catch (RuntimeException e) {
                log.warn("Envoi SSE de la réservation {} impossible : {}", userAndBooking[1], e.getMessage());
            }
        }, new ChannelTopic(BookingEventPublisher.CHANNEL));
        return container;
    }
}
