package com.easy.eventbyte.domain;

import java.time.Instant;

/** Ticket mis en vente, tel que la réservation le voit : son événement et la date de celui-ci. */
public record TicketOffer(String ticketId, String eventId, Instant eventStartAt) {
}
