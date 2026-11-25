package com.easy.eventbyte.dto;

/**
 * Type de ticket d'un événement (contrat GET /events/:eventId/tickets).
 *
 * @param price    prix unitaire en centimes
 * @param quantity quantité encore disponible à la réservation
 */
public record TicketResponse(String id, String eventId, String name, long price, int quantity) {
}
