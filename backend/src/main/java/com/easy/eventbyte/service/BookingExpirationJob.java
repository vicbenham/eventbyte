package com.easy.eventbyte.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Tâche périodique qui fait expirer les réservations non payées dans le délai. */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingExpirationJob {

    private final BookingExpirationService expirationService;

    @Scheduled(fixedDelayString = "${booking.expiration-check-interval}")
    public void expireOverdueBookings() {
        // Chaque réservation est expirée dans sa propre transaction : un échec n'en bloque pas d'autres
        for (String bookingId : expirationService.findOverdue()) {
            try {
                expirationService.expire(bookingId);
            } catch (RuntimeException e) {
                log.error("Impossible d'expirer la réservation {}", bookingId, e);
            }
        }
    }
}
