package com.easy.eventbyte.service;

import com.easy.eventbyte.TestcontainersConfiguration;
import com.easy.eventbyte.dto.BookingItemRequest;
import com.easy.eventbyte.dto.StrapiEventMessage;
import com.easy.eventbyte.dto.TicketMessage;
import com.easy.eventbyte.dto.VenueMessage;
import com.easy.eventbyte.model.BookingStatus;
import com.easy.eventbyte.model.Event;
import com.easy.eventbyte.model.Ticket;
import com.easy.eventbyte.model.Venue;
import com.easy.eventbyte.repository.BookingRepository;
import com.easy.eventbyte.repository.EventRepository;
import com.easy.eventbyte.repository.TicketRepository;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** US-13 : le stock n'est jamais dépassé, grâce au compteur atomique Redis. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TicketStockIntegrationTest {

    @Autowired
    BookingService bookingService;
    @Autowired
    TicketStockService ticketStockService;
    @Autowired
    EventIngestionService ingestion;
    @Autowired
    EventRepository eventRepository;
    @Autowired
    TicketRepository ticketRepository;
    @Autowired
    BookingRepository bookingRepository;
    @Autowired
    StringRedisTemplate redis;
    @Autowired
    TransactionTemplate transaction;

    @RepeatedTest(10)
    void deux_reservations_simultanees_du_dernier_ticket_une_seule_reussit() throws Exception {
        String ticketId = createEventWithTicket(1);
        CountDownLatch start = new CountDownLatch(1);

        List<BookingStatus> statuses;
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<CompletableFuture<String>> bookings = IntStream.range(0, 2)
                    .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                        await(start);
                        return bookingService.create("user-" + i, List.of(new BookingItemRequest(ticketId, 1)));
                    }, executor))
                    .toList();
            start.countDown();
            statuses = bookings.stream().map(CompletableFuture::join).map(this::status).toList();
        }

        assertThat(statuses).containsExactlyInAnyOrder(BookingStatus.RESERVED, BookingStatus.REJECTED);
        assertThat(stock(ticketId)).isZero();
    }

    @Test
    void cent_reservations_simultanees_ne_depassent_jamais_le_stock() {
        String ticketId = createEventWithTicket(10);

        long reserved;
        try (var executor = Executors.newFixedThreadPool(16)) {
            reserved = IntStream.range(0, 100)
                    .mapToObj(i -> CompletableFuture.supplyAsync(() ->
                            bookingService.create("user-" + i, List.of(new BookingItemRequest(ticketId, 1))), executor))
                    .toList().stream()
                    .map(CompletableFuture::join)
                    .filter(id -> status(id) == BookingStatus.RESERVED)
                    .count();
        }

        assertThat(reserved).isEqualTo(10);
        assertThat(stock(ticketId)).isZero();
        assertThat(bookingRepository.heldByTicketId(List.of(ticketId))).containsEntry(ticketId, 10L);
    }

    @Test
    void une_reservation_est_tout_ou_rien_sur_ses_lignes() {
        Event event = createEvent(Map.of("Standard", 10, "VIP", 3));
        String standard = event.getId() + "_Standard";
        String vip = event.getId() + "_VIP";

        String id = bookingService.create("alice", List.of(
                new BookingItemRequest(standard, 1), new BookingItemRequest(vip, 5)));

        assertThat(status(id)).isEqualTo(BookingStatus.REJECTED);
        assertThat(stock(standard)).isEqualTo(10);
        assertThat(stock(vip)).isEqualTo(3);
    }

    @Test
    void un_compteur_absent_est_recalcule_depuis_les_reservations_en_base() {
        String ticketId = createEventWithTicket(5);
        bookingService.create("alice", List.of(new BookingItemRequest(ticketId, 3)));

        redis.delete(TicketStockService.key(ticketId)); // ex. Redis vidé ou redémarré sans persistance

        assertThat(status(bookingService.create("bob", List.of(new BookingItemRequest(ticketId, 3)))))
                .isEqualTo(BookingStatus.REJECTED);
        assertThat(status(bookingService.create("bob", List.of(new BookingItemRequest(ticketId, 2)))))
                .isEqualTo(BookingStatus.RESERVED);
        assertThat(stock(ticketId)).isZero();
    }

    @Test
    void si_la_transaction_echoue_les_places_sont_rendues() {
        String ticketId = createEventWithTicket(5);
        List<Ticket> tickets = ticketRepository.findAllById(List.of(ticketId));

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            assertThat(ticketStockService.tryReserve(tickets, Map.of(ticketId, 2))).isTrue();
            throw new IllegalStateException("échec après la réservation Redis");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(stock(ticketId)).isEqualTo(5);
    }

    @Test
    void un_stock_augmente_dans_strapi_est_pris_en_compte() {
        String eventId = "evt_" + UUID.randomUUID();
        String ticketId = eventId + "_tkt";
        ingestion.save(strapiMessage(eventId, ticketId, 2));
        assertThat(status(bookingService.create("alice", List.of(new BookingItemRequest(ticketId, 2)))))
                .isEqualTo(BookingStatus.RESERVED);

        ingestion.save(strapiMessage(eventId, ticketId, 5)); // l'organisateur ajoute 3 places

        assertThat(status(bookingService.create("bob", List.of(new BookingItemRequest(ticketId, 3)))))
                .isEqualTo(BookingStatus.RESERVED);
        assertThat(status(bookingService.create("carol", List.of(new BookingItemRequest(ticketId, 1)))))
                .isEqualTo(BookingStatus.REJECTED);
    }

    // --- outils

    private long stock(String ticketId) {
        return Long.parseLong(redis.opsForValue().get(TicketStockService.key(ticketId)));
    }

    private BookingStatus status(String bookingId) {
        return bookingRepository.findById(bookingId).orElseThrow().getStatus();
    }

    private String createEventWithTicket(int quantity) {
        return createEvent(Map.of("Standard", quantity)).getId() + "_Standard";
    }

    private Event createEvent(Map<String, Integer> stockByTicketName) {
        Event event = new Event("evt_" + UUID.randomUUID());
        event.setTitle("Concert");
        event.setVenue(new Venue("Salle", "1 rue de la Paix", 48.85, 2.35));
        event.setStartAt(Instant.now().plus(Duration.ofDays(7)));
        event.setUpdatedAt(Instant.now());
        stockByTicketName.forEach((name, quantity) -> {
            Ticket ticket = new Ticket(event.getId() + "_" + name, event);
            ticket.setName(name);
            ticket.setQuantity(quantity);
            ticket.setPriceInCents(2500);
            event.getTickets().add(ticket);
        });
        return eventRepository.save(event);
    }

    private static StrapiEventMessage strapiMessage(String eventId, String ticketId, int quantity) {
        return new StrapiEventMessage(eventId, "Concert", "", "https://cdn/c.png",
                new VenueMessage("Salle", "1 rue de la Paix", 48.85, 2.35),
                List.of(new TicketMessage(ticketId, "Standard", quantity, new BigDecimal("25.00"))));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
