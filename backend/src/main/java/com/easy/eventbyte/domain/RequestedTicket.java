package com.easy.eventbyte.domain;

/** Ligne d'une demande de réservation : un type de ticket et la quantité voulue. */
public record RequestedTicket(String ticketId, int quantity) {
}
