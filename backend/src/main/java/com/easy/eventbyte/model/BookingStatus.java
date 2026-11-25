package com.easy.eventbyte.model;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.List;

/** Statuts d'une réservation. {@link #apiValue} est la valeur exposée par l'API (contrat du README). */
public enum BookingStatus {
    REQUESTED("requested"),
    RESERVED("reserved"),
    REJECTED("rejected"),
    AWAITING_PAYMENT("awaiting-payment"),
    EXPIRED("expired"),
    CONFIRMED("confirmed");

    /** Statuts qui immobilisent des places : elles sont déduites du stock disponible. */
    public static final List<BookingStatus> HOLDING_STOCK = List.of(RESERVED, AWAITING_PAYMENT, CONFIRMED);

    private final String apiValue;

    BookingStatus(String apiValue) {
        this.apiValue = apiValue;
    }

    @JsonValue
    public String apiValue() {
        return apiValue;
    }
}
