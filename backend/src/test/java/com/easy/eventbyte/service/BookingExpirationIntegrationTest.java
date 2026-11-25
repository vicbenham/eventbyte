package com.easy.eventbyte.service;

import com.easy.eventbyte.TestcontainersConfiguration;
import com.easy.eventbyte.dto.BookingItemRequest;
import com.easy.eventbyte.model.Booking;
import com.easy.eventbyte.domain.BookingStatus;
import com.easy.eventbyte.model.Event;
import com.easy.eventbyte.model.Ticket;
import com.easy.eventbyte.model.Venue;
import com.easy.eventbyte.repository.BookingRepository;
import com.easy.eventbyte.repository.EventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * US-14 : les réservations non payées expirent et leurs places retournent en vente.
 * La tâche planifiée est désactivée (intervalle d'une heure) : les tests la déclenchent eux-mêmes.
 */
@SpringBootTest(properties = "booking.expiration-check-interval=PT1H")
@Import(TestcontainersConfiguration.class)
class BookingExpirationIntegrationTest {

    @Autowired
    BookingService bookingService;
    @Autowired
    BookingExpirationJob expirationJob;
    @Autowired
    BookingExpirationService expirationService;
    @Autowired
    BookingRepository bookingRepository;
    @Autowired
    EventRepository eventRepository;
    @Autowired
    StringRedisTemplate redis;

    @Test
    void une_reservation_reservee_a_une_echeance_de_10_minutes() {
        String ticketId = createEventWithTicket(5);

        Booking booking = load(book(ticketId, 2));

        assertThat(booking.getExpiresAt())
                .isCloseTo(booking.getBookedAt().plus(Duration.ofMinutes(10)), within(Duration.ofSeconds(1)));
    }

    @Test
    void une_reservation_rejetee_n_a_pas_d_echeance() {
        String ticketId = createEventWithTicket(1);

        assertThat(load(book(ticketId, 2)).getExpiresAt()).isNull();
    }

    @Test
    void une_reservation_echue_expire_et_ses_places_retournent_en_vente() {
        String ticketId = createEventWithTicket(5);
        String id = book(ticketId, 5);
        assertThat(load(book(ticketId, 1)).getStatus()).isEqualTo(BookingStatus.REJECTED); // complet

        makeOverdue(id);
        expirationJob.expireOverdueBookings();

        assertThat(load(id).getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(stock(ticketId)).isEqualTo(5);
        assertThat(load(book(ticketId, 5)).getStatus()).isEqualTo(BookingStatus.RESERVED);
    }

    @Test
    void une_reservation_en_attente_de_paiement_expire_aussi() {
        String ticketId = createEventWithTicket(5);
        String id = book(ticketId, 2);
        setStatus(id, BookingStatus.AWAITING_PAYMENT);

        makeOverdue(id);
        expirationJob.expireOverdueBookings();

        assertThat(load(id).getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(stock(ticketId)).isEqualTo(5);
    }

    @Test
    void une_reservation_dans_le_delai_n_expire_pas() {
        String ticketId = createEventWithTicket(5);
        String id = book(ticketId, 2);

        expirationJob.expireOverdueBookings();

        assertThat(load(id).getStatus()).isEqualTo(BookingStatus.RESERVED);
        assertThat(stock(ticketId)).isEqualTo(3);
    }

    @Test
    void une_reservation_confirmee_n_expire_jamais() {
        String ticketId = createEventWithTicket(5);
        String id = book(ticketId, 2);
        setStatus(id, BookingStatus.CONFIRMED);

        makeOverdue(id);
        expirationJob.expireOverdueBookings();

        assertThat(load(id).getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(stock(ticketId)).isEqualTo(3);
    }

    @Test
    void deux_instances_qui_expirent_la_meme_reservation_ne_rendent_les_places_qu_une_fois() {
        String ticketId = createEventWithTicket(5);
        String id = book(ticketId, 2);
        makeOverdue(id);

        List<Boolean> results;
        try (var executor = Executors.newFixedThreadPool(2)) {
            results = List.of(
                            CompletableFuture.supplyAsync(() -> expirationService.expire(id), executor),
                            CompletableFuture.supplyAsync(() -> expirationService.expire(id), executor))
                    .stream().map(CompletableFuture::join).toList();
        }

        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(stock(ticketId)).isEqualTo(5);
    }

    // --- outils

    private String book(String ticketId, int quantity) {
        return bookingService.create("user-" + UUID.randomUUID(), List.of(new BookingItemRequest(ticketId, quantity)));
    }

    private Booking load(String id) {
        return bookingRepository.findById(id).orElseThrow();
    }

    private void makeOverdue(String id) {
        Booking booking = load(id);
        booking.setExpiresAt(Instant.now().minusSeconds(1));
        bookingRepository.save(booking);
    }

    private void setStatus(String id, BookingStatus status) {
        Booking booking = load(id);
        booking.setStatus(status);
        bookingRepository.save(booking);
    }

    private long stock(String ticketId) {
        return Long.parseLong(redis.opsForValue().get(TicketStockService.key(ticketId)));
    }

    private String createEventWithTicket(int quantity) {
        Event event = new Event("evt_" + UUID.randomUUID());
        event.setTitle("Concert");
        event.setVenue(new Venue("Salle", "1 rue de la Paix", 48.85, 2.35));
        event.setStartAt(Instant.now().plus(Duration.ofDays(7)));
        event.setUpdatedAt(Instant.now());
        Ticket ticket = new Ticket(event.getId() + "_Standard", event);
        ticket.setName("Standard");
        ticket.setQuantity(quantity);
        ticket.setPriceInCents(2500);
        event.getTickets().add(ticket);
        return eventRepository.save(event).getTickets().getFirst().getId();
    }
}
