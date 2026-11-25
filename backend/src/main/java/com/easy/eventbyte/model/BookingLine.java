package com.easy.eventbyte.model;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Ligne de réservation : nom et prix unitaire (centimes) figés au moment de la réservation. */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BookingLine {

    private String ticketId;
    private String name;
    private int quantity;
    private long unitPrice;

    public long subtotal() {
        return unitPrice * quantity;
    }
}
