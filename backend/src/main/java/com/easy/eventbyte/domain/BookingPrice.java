package com.easy.eventbyte.domain;

import java.util.List;

/**
 * Prix d'une réservation, en centimes : prix des tickets, frais de service et total à payer.
 * Les frais sont de {@value #FEES_PER_TICKET_IN_CENTS} centimes par ticket (même règle que le mode démo du frontend).
 */
public record BookingPrice(long subtotal, long fees, long total) {

    public static final long FEES_PER_TICKET_IN_CENTS = 30;

    public static BookingPrice of(List<PricedQuantity> lines) {
        long subtotal = lines.stream().mapToLong(line -> line.unitPrice() * line.quantity()).sum();
        long fees = FEES_PER_TICKET_IN_CENTS * lines.stream().mapToLong(PricedQuantity::quantity).sum();
        return new BookingPrice(subtotal, fees, subtotal + fees);
    }
}
