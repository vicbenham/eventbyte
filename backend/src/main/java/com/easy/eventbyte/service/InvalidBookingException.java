package com.easy.eventbyte.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Demande de réservation incohérente (ticket inconnu, plusieurs événements...) : l'API répond 400. */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidBookingException extends RuntimeException {

    public InvalidBookingException(String message) {
        super(message);
    }
}
