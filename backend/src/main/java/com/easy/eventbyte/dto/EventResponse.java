package com.easy.eventbyte.dto;

import com.easy.eventbyte.domain.TicketStock;
import com.easy.eventbyte.model.Event;
import com.easy.eventbyte.model.Ticket;

import java.time.Instant;
import java.util.Map;
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

    /**
     * @param likedCount nombre de likes de l'événement
     * @param heldByTicketId places déjà réservées par ticket (absent = aucune)
     */
    public static EventResponse from(Event event, long likedCount, Map<String, Long> heldByTicketId) {
        return new EventResponse(
                event.getId(),
                event.getTitle(),
                Objects.requireNonNullElse(event.getCoverUrl(), ""),
                likedCount,
                VenueResponse.from(event.getVenue()),
                isSoldOut(event, heldByTicketId),
                event.getStartAt(),
                lowestPrice(event),
                Objects.requireNonNullElse(event.getDescription(), "")
        );
    }

    /** Complet quand plus aucun type de ticket n'a de place réservable. */
    private static boolean isSoldOut(Event event, Map<String, Long> heldByTicketId) {
        return TicketStock.isSoldOut(event.getTickets().stream()
                .map(ticket -> ticket.availableQuantity(heldByTicketId.getOrDefault(ticket.getId(), 0L)))
                .toList());
    }

    /** Prix le plus bas en centimes parmi les tickets, 0 si l'événement n'en a pas. */
    private static long lowestPrice(Event event) {
        return event.getTickets().stream()
                .mapToLong(Ticket::getPriceInCents)
                .min()
                .orElse(0);
    }
}
