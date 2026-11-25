package com.easy.eventbyte.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Webhook non signé par Stripe (ou illisible) : l'API répond 400 et l'ignore. */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidWebhookException extends RuntimeException {

    public InvalidWebhookException(String message) {
        super(message);
    }
}
