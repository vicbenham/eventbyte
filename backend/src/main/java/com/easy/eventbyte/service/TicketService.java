package com.easy.eventbyte.service;

import com.easy.eventbyte.dto.TicketResponse;
import com.easy.eventbyte.model.Event;
import com.easy.eventbyte.model.Ticket;
import com.easy.eventbyte.repository.BookingRepository;
import com.easy.eventbyte.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TicketService {

    private final EventRepository eventRepository;
    private final BookingRepository bookingRepository;

    /** Types de tickets de l'événement, du moins cher au plus cher, avec la quantité encore réservable. */
    public List<TicketResponse> getTickets(String eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
        Map<String, Long> held = bookingRepository.heldByTicketId(
                event.getTickets().stream().map(Ticket::getId).toList());

        return event.getTickets().stream()
                .sorted(Comparator.comparingLong(Ticket::getPriceInCents).thenComparing(Ticket::getName))
                .map(ticket -> new TicketResponse(
                        ticket.getId(),
                        eventId,
                        ticket.getName(),
                        ticket.getPriceInCents(),
                        ticket.availableQuantity(held.getOrDefault(ticket.getId(), 0L))))
                .toList();
    }
}
