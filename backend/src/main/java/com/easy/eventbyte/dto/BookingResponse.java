package com.easy.eventbyte.dto;

import com.easy.eventbyte.model.Booking;
import com.easy.eventbyte.model.BookingStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Réservation telle que l'exposent les routes /bookings (contrat du README).
 *
 * @param status       valeur de l'API : requested, reserved, rejected, awaiting-payment, expired ou confirmed
 * @param total        total en centimes
 * @param fees         frais en centimes
 * @param clientSecret secret Stripe, absent du JSON tant qu'il n'y a pas de paiement (le frontend refuse null)
 */
public record BookingResponse(
        String id,
        String eventId,
        Instant bookedAt,
        List<BookingLineResponse> lines,
        BookingStatus status,
        long total,
        long fees,
        @JsonInclude(JsonInclude.Include.NON_NULL) String clientSecret
) {

    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getEventId(),
                booking.getBookedAt(),
                booking.getLines().stream().map(BookingLineResponse::from).toList(),
                booking.getStatus(),
                booking.getTotal(),
                booking.getFees(),
                booking.getClientSecret());
    }
}
