package com.easy.eventbyte.service;

import com.easy.eventbyte.client.MeilisearchClient;
import com.easy.eventbyte.client.StrapiClient;
import com.easy.eventbyte.domain.TicketSync;
import com.easy.eventbyte.dto.EventDocument;
import com.easy.eventbyte.dto.StrapiEventMessage;
import com.easy.eventbyte.dto.TicketMessage;
import com.easy.eventbyte.model.DeletedEvent;
import com.easy.eventbyte.model.Event;
import com.easy.eventbyte.model.Ticket;
import com.easy.eventbyte.model.Venue;
import com.easy.eventbyte.repository.DeletedEventRepository;
import com.easy.eventbyte.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Répercute en base les événements créés, modifiés ou supprimés dans Strapi.
 * Toutes les opérations sont idempotentes : un message reçu deux fois ne crée pas de doublon.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class EventIngestionService {

    private final EventRepository eventRepository;
    private final DeletedEventRepository deletedEventRepository;
    private final StrapiClient strapiClient;
    private final MeilisearchClient meilisearchClient;
    private final TicketStockService ticketStockService;


    public void save(StrapiEventMessage message) {
        if (!TicketSync.shouldApply(deletedEventRepository.existsById(message.id()))) {
            log.info("Événement {} déjà supprimé, message ignoré", message.id());
            return;
        }

        Event event = eventRepository.findById(message.id()).orElseGet(() -> new Event(message.id()));
        event.setTitle(message.title());
        event.setDescription(message.description());
        event.setCoverUrl(message.cover());
        // startAt n'est pas dans le message : on le lit dans Strapi (on garde l'ancienne valeur en cas d'échec)
        strapiClient.findStartAt(message.id()).ifPresent(event::setStartAt);
        event.setVenue(new Venue(
                message.venue().name(),
                message.venue().address(),
                message.venue().latitude(),
                message.venue().longitude()));
        event.setUpdatedAt(Instant.now());
        updateTickets(event, message);

        eventRepository.save(event);
        // Dans la transaction : si Meilisearch échoue, rien n'est enregistré et le message est rejoué
        meilisearchClient.index(List.of(EventDocument.from(event)));
        // Le stock a pu changer dans Strapi : les compteurs Redis seront recalculés depuis la base
        ticketStockService.invalidateAfterCommit(event.getTickets().stream().map(Ticket::getId).toList());
        log.info("Événement {} enregistré avec {} ticket(s)", event.getId(), event.getTickets().size());
    }

    public void delete(String eventId) {
        eventRepository.findById(eventId).ifPresent(event -> ticketStockService.invalidateAfterCommit(
                event.getTickets().stream().map(Ticket::getId).toList()));
        eventRepository.deleteById(eventId);
        meilisearchClient.delete(eventId);
        if (!deletedEventRepository.existsById(eventId)) {
            deletedEventRepository.save(new DeletedEvent(eventId, Instant.now()));
        }
        log.info("Événement {} supprimé", eventId);
    }


    private void updateTickets(Event event, StrapiEventMessage message) {
        List<TicketMessage> tickets = message.tickets() == null ? List.of() : message.tickets();
        TicketSync sync = TicketSync.between(
                event.getTickets().stream().map(Ticket::getId).toList(),
                tickets.stream().map(TicketMessage::id).toList());

        event.getTickets().removeIf(ticket -> sync.removed().contains(ticket.getId()));
        sync.added().forEach(id -> event.getTickets().add(new Ticket(id, event)));

        Map<String, Ticket> byId = event.getTickets().stream()
                .collect(Collectors.toMap(Ticket::getId, Function.identity()));
        for (TicketMessage ticketMessage : tickets) {
            Ticket ticket = byId.get(ticketMessage.id());
            ticket.setName(ticketMessage.name());
            ticket.setQuantity(ticketMessage.quantity());
            ticket.setPriceInCents(ticketMessage.priceInCents());
        }
    }
}
