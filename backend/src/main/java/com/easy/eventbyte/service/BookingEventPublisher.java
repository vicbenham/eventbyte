package com.easy.eventbyte.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Annonce le changement de statut d'une réservation, une fois la transaction validée,
 * sur le canal Redis {@value #CHANNEL} : chaque instance du backend le relaie à ses flux SSE.
 * Message : "userId bookingId".
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingEventPublisher {

    public static final String CHANNEL = "booking-updates";

    private final StringRedisTemplate redis;

    public void publishAfterCommit(String userId, String bookingId) {
        Runnable publish = () -> {
            try {
                redis.convertAndSend(CHANNEL, userId + " " + bookingId);
            } catch (RuntimeException e) {
                // Le temps réel ne doit pas faire échouer une réservation : le frontend relira la réservation
                log.warn("Notification de la réservation {} impossible : {}", bookingId, e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
        } else {
            publish.run();
        }
    }
}
