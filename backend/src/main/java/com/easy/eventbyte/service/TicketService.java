package com.easy.eventbyte.service;

import com.easy.eventbyte.dto.TicketResponse;
import com.easy.eventbyte.model.Event;
import com.easy.eventbyte.model.Ticket;
import com.easy.eventbyte.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TicketService {

    private final EventRepository eventRepository;

    /** Types de tickets de l'événement, du moins cher au plus cher. */
    public List<TicketResponse> getTickets(String eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));

        return event.getTickets().stream()
                .sorted(Comparator.comparingLong(Ticket::getPriceInCents).thenComparing(Ticket::getName))
                .map(ticket -> new TicketResponse(
                        ticket.getId(),
                        eventId,
                        ticket.getName(),
                        ticket.getPriceInCents(),
                        availableQuantity(ticket)))
                .toList();
    }

    /**
     * Quantité encore réservable : stock défini dans Strapi moins les places des réservations en cours.
     * Les réservations arrivent avec l'US-12/13 : c'est ici qu'elles seront déduites.
     */
    public static int availableQuantity(Ticket ticket) {
        return Math.max(ticket.getQuantity(), 0);
    }
}
