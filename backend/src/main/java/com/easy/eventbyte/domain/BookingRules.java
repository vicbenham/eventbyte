package com.easy.eventbyte.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Règles d'une demande de réservation, avant toute prise de stock. */
public final class BookingRules {

    private BookingRules() {
    }

    /** Un même ticket demandé sur plusieurs lignes ne fait qu'une ligne (quantités additionnées), ordre conservé. */
    public static Map<String, Integer> mergeByTicket(List<RequestedTicket> requested) {
        Map<String, Integer> quantities = new LinkedHashMap<>();
        requested.forEach(line -> quantities.merge(line.ticketId(), line.quantity(), Integer::sum));
        return quantities;
    }

    /**
     * Vérifie que la réservation est possible et renvoie l'id de son événement :
     * tous les tickets demandés existent, ils sont du même événement, et celui-ci n'est pas passé.
     *
     * @param requestedTicketCount nombre de tickets différents demandés
     * @param found                tickets trouvés parmi ceux demandés
     */
    public static String checkBookable(int requestedTicketCount, List<TicketOffer> found, Instant now) {
        if (found.size() != requestedTicketCount) {
            throw new InvalidBookingException("Ticket inconnu dans la réservation");
        }
        List<TicketOffer> byEvent = found.stream()
                .filter(offer -> offer.eventId().equals(found.getFirst().eventId()))
                .toList();
        if (byEvent.size() != found.size()) {
            throw new InvalidBookingException("Une réservation ne concerne qu'un seul événement");
        }
        Instant startAt = found.getFirst().eventStartAt();
        if (startAt != null && startAt.isBefore(now)) {
            throw new InvalidBookingException("L'événement est déjà passé");
        }
        return found.getFirst().eventId();
    }
}
