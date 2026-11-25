package com.easy.eventbyte.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** L'événement demandé n'existe pas (ou plus) : l'API répond 404. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class EventNotFoundException extends RuntimeException {

    public EventNotFoundException(String eventId) {
        super("Événement introuvable : " + eventId);
    }
}
