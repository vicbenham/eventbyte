package com.easy.eventbyte.client;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Stripe a refusé ou n'a pas répondu : l'API répond 502 (la réservation est annulée, les places rendues). */
@ResponseStatus(HttpStatus.BAD_GATEWAY)
public class PaymentProviderException extends RuntimeException {

    public PaymentProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
