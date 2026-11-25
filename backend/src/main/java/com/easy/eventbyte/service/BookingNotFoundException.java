package com.easy.eventbyte.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Réservation introuvable pour l'utilisateur : inexistante ou appartenant à quelqu'un d'autre.
 * Les deux cas répondent 404, pour ne pas révéler l'existence de la réservation d'un autre.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class BookingNotFoundException extends RuntimeException {

    public BookingNotFoundException(String bookingId) {
        super("Réservation introuvable : " + bookingId);
    }
}
