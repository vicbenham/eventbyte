package com.easy.eventbyte.dto;

import com.easy.eventbyte.model.BookingLine;

/**
 * Ligne d'une réservation (contrat du README).
 *
 * @param id    id du ticket
 * @param price prix unitaire en centimes
 */
public record BookingLineResponse(String id, String name, int quantity, long price) {

    public static BookingLineResponse from(BookingLine line) {
        return new BookingLineResponse(line.getTicketId(), line.getName(), line.getQuantity(), line.getUnitPrice());
    }
}
