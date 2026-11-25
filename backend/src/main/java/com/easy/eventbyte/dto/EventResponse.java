package com.easy.eventbyte.dto;

import com.easy.eventbyte.model.Event;
import com.easy.eventbyte.model.Ticket;

import java.time.Instant;
import java.util.Objects;

/** Événement tel que l'exposent les routes /events (contrat d'API du README). */
public record EventResponse(
        String id,
        String title,
        String cover,
        long likedCount,
        VenueResponse venue,
        boolean isSoldOut,
        Instant startAt,
        long lowestPrice,
        String description
) {

    public static EventResponse from(Event event, long likedCount) {
        return new EventResponse(
                event.getId(),
                event.getTitle(),
                Objects.requireNonNullElse(event.getCoverUrl(), ""),
                likedCount,
                VenueResponse.from(event.getVenue()),
                isSoldOut(event),
                event.getStartAt(),
                lowestPrice(event),
                Objects.requireNonNullElse(event.getDescription(), "")
        );
    }

    /** Complet quand plus aucun type de ticket n'a de stock (les réservations seront déduites avec l'US-13). */
    private static boolean isSoldOut(Event event) {
        return event.getTickets().stream().allMatch(ticket -> ticket.getQuantity() <= 0);
    }

    /** Prix le plus bas en centimes parmi les tickets, 0 si l'événement n'en a pas. */
    private static long lowestPrice(Event event) {
        return event.getTickets().stream()
                .mapToLong(Ticket::getPriceInCents)
                .min()
                .orElse(0);
    }
}
