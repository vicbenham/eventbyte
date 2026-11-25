package com.easy.eventbyte.service;

import com.easy.eventbyte.dto.BookingItemRequest;
import com.easy.eventbyte.model.Booking;
import com.easy.eventbyte.model.BookingLine;
import com.easy.eventbyte.model.BookingStatus;
import com.easy.eventbyte.model.Event;
import com.easy.eventbyte.model.Ticket;
import com.easy.eventbyte.repository.BookingRepository;
import com.easy.eventbyte.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class BookingService {

    private final BookingRepository bookingRepository;
    private final TicketRepository ticketRepository;
    private final TicketStockService ticketStockService;

    /** Délai pour payer une réservation (aligné sur le compte à rebours du frontend). */
    @Value("${booking.expiration}")
    private Duration expiration;

    /**
     * Crée une réservation (statut requested) puis la passe en reserved si le stock suffit pour
     * toutes les lignes, en rejected sinon. Une réservation rejetée n'immobilise aucune place.
     *
     * @return l'id de la réservation
     */
    public String create(String userId, List<BookingItemRequest> items) {
        Map<String, Integer> quantities = mergeQuantitiesByTicket(items);

        List<Ticket> tickets = ticketRepository.findAllById(quantities.keySet());
        Event event = checkSameUpcomingEvent(tickets, quantities.size());

        List<BookingLine> lines = tickets.stream()
                .map(t -> new BookingLine(t.getId(), t.getName(), quantities.get(t.getId()), t.getPriceInCents()))
                .toList();
        Booking booking = bookingRepository.save(new Booking(userId, event.getId(), lines));

        // Décision atomique dans Redis : deux réservations du dernier ticket ne peuvent pas réussir toutes les deux
        boolean reserved = ticketStockService.tryReserve(tickets, quantities);
        booking.setStatus(reserved ? BookingStatus.RESERVED : BookingStatus.REJECTED);
        if (reserved) {
            booking.setExpiresAt(booking.getBookedAt().plus(expiration));
        }
        log.info("Réservation {} de {} : {}", booking.getId(), userId, booking.getStatus());
        return booking.getId();
    }

    /** Un même ticket demandé sur deux lignes compte pour une seule ligne. */
    private static Map<String, Integer> mergeQuantitiesByTicket(List<BookingItemRequest> items) {
        Map<String, Integer> quantities = new LinkedHashMap<>();
        items.forEach(item -> quantities.merge(item.id(), item.quantity(), Integer::sum));
        return quantities;
    }

    private static Event checkSameUpcomingEvent(List<Ticket> tickets, int requestedTicketCount) {
        if (tickets.size() != requestedTicketCount) {
            throw new InvalidBookingException("Ticket inconnu dans la réservation");
        }
        List<Event> events = tickets.stream().map(Ticket::getEvent).distinct().toList();
        if (events.size() != 1) {
            throw new InvalidBookingException("Une réservation ne concerne qu'un seul événement");
        }
        Event event = events.getFirst();
        if (event.getStartAt() != null && event.getStartAt().isBefore(Instant.now())) {
            throw new InvalidBookingException("L'événement est déjà passé");
        }
        return event;
    }
}
