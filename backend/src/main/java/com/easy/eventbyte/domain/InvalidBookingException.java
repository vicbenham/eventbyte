package com.easy.eventbyte.domain;

/** Demande de réservation incohérente (ticket inconnu, plusieurs événements...). L'API la traduit en 400. */
public class InvalidBookingException extends RuntimeException {

    public InvalidBookingException(String message) {
        super(message);
    }
}
