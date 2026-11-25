package com.easy.eventbyte.exposition;

import com.easy.eventbyte.TestcontainersConfiguration;
import com.easy.eventbyte.dto.BookingItemRequest;
import com.easy.eventbyte.model.Booking;
import com.easy.eventbyte.model.BookingStatus;
import com.easy.eventbyte.model.Event;
import com.easy.eventbyte.model.Ticket;
import com.easy.eventbyte.model.Venue;
import com.easy.eventbyte.repository.BookingRepository;
import com.easy.eventbyte.repository.EventRepository;
import com.easy.eventbyte.service.BookingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US-12 et US-15 : réserver, consulter une réservation ou toutes les siennes. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BookingIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    JsonMapper jsonMapper;
    @Autowired
    EventRepository eventRepository;
    @Autowired
    BookingRepository bookingRepository;
    @Autowired
    BookingService bookingService;
    @Autowired
    TransactionTemplate transaction;

    @Test
    void une_reservation_avec_assez_de_stock_est_reservee_et_renvoie_son_id() throws Exception {
        Event event = createEvent(ticket("Standard", 100, 2500), ticket("VIP", 10, 9000));

        String id = book("alice", item(event, "Standard", 2), item(event, "VIP", 1));

        Booking booking = load(id);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.RESERVED);
        assertThat(booking.getUserId()).isEqualTo("alice");
        assertThat(booking.getEventId()).isEqualTo(event.getId());
        assertThat(booking.getTotal()).isEqualTo(2 * 2500 + 9000);
        assertThat(booking.getLines()).extracting("ticketId", "name", "quantity", "unitPrice").containsExactlyInAnyOrder(
                tuple(ticketId(event, "Standard"), "Standard", 2, 2500L),
                tuple(ticketId(event, "VIP"), "VIP", 1, 9000L));
    }

    @Test
    void une_reservation_sans_assez_de_stock_est_rejetee() throws Exception {
        Event event = createEvent(ticket("Standard", 100, 2500), ticket("VIP", 2, 9000));

        // Une seule ligne en manque suffit à rejeter toute la réservation
        String id = book("alice", item(event, "Standard", 1), item(event, "VIP", 3));

        assertThat(load(id).getStatus()).isEqualTo(BookingStatus.REJECTED);
    }

    @Test
    void les_places_reservees_sont_deduites_du_stock_disponible() throws Exception {
        Event event = createEvent(ticket("Standard", 5, 2500));

        book("alice", item(event, "Standard", 3));

        mvc.perform(get("/events/{id}/tickets", event.getId()))
                .andExpect(jsonPath("$[0].quantity").value(2));
        assertThat(load(book("bob", item(event, "Standard", 3))).getStatus()).isEqualTo(BookingStatus.REJECTED);
        assertThat(load(book("bob", item(event, "Standard", 2))).getStatus()).isEqualTo(BookingStatus.RESERVED);
        mvc.perform(get("/events/{id}", event.getId()))
                .andExpect(jsonPath("$.isSoldOut").value(true));
    }

    @Test
    void une_reservation_rejetee_n_immobilise_aucune_place() throws Exception {
        Event event = createEvent(ticket("Standard", 2, 2500));

        book("alice", item(event, "Standard", 5));

        mvc.perform(get("/events/{id}/tickets", event.getId()))
                .andExpect(jsonPath("$[0].quantity").value(2));
    }

    @Test
    void un_meme_ticket_sur_deux_lignes_compte_pour_une_seule_ligne() throws Exception {
        Event event = createEvent(ticket("Standard", 3, 2500));

        String id = book("alice", item(event, "Standard", 2), item(event, "Standard", 2));

        Booking booking = load(id);
        assertThat(booking.getLines()).singleElement().extracting("quantity").isEqualTo(4);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.REJECTED);
    }

    @Test
    void des_reservations_simultanees_ne_depassent_jamais_le_stock() {
        Event event = createEvent(ticket("Standard", 5, 2500));
        String ticketId = ticketId(event, "Standard");

        List<String> ids;
        try (var executor = Executors.newFixedThreadPool(10)) {
            List<CompletableFuture<String>> futures = IntStream.range(0, 20)
                    .mapToObj(i -> CompletableFuture.supplyAsync(() ->
                            bookingService.create("user-" + i, List.of(new BookingItemRequest(ticketId, 1))), executor))
                    .toList();
            ids = futures.stream().map(CompletableFuture::join).toList();
        }

        long reserved = ids.stream().map(this::load).filter(b -> b.getStatus() == BookingStatus.RESERVED).count();
        assertThat(reserved).isEqualTo(5);
    }

    @Test
    void une_demande_invalide_repond_400() throws Exception {
        Event event = createEvent(ticket("Standard", 10, 2500));
        Event other = createEvent(ticket("Standard", 10, 2500));
        Event past = createEvent(Instant.now().minus(Duration.ofDays(1)), ticket("Standard", 10, 2500));

        postBooking("alice", "{\"items\":[]}").andExpect(status().isBadRequest());
        postBooking("alice", body(item(event, "Standard", 0))).andExpect(status().isBadRequest());
        postBooking("alice", "{\"items\":[{\"id\":\"tkt_inconnu\",\"quantity\":1}]}").andExpect(status().isBadRequest());
        postBooking("alice", body(item(event, "Standard", 1), item(other, "Standard", 1))).andExpect(status().isBadRequest());
        postBooking("alice", body(item(past, "Standard", 1))).andExpect(status().isBadRequest());
    }

    @Test
    void reserver_sans_etre_connecte_repond_401() throws Exception {
        Event event = createEvent(ticket("Standard", 10, 2500));

        mvc.perform(post("/bookings").contentType(MediaType.APPLICATION_JSON).content(body(item(event, "Standard", 1))))
                .andExpect(status().isUnauthorized());
    }

    // --- US-15 : GET /bookings/:id et GET /bookings/user

    @Test
    void je_consulte_ma_reservation_au_format_du_contrat() throws Exception {
        Event event = createEvent(ticket("Standard", 100, 2500), ticket("VIP", 10, 9000));
        String id = book("alice", item(event, "Standard", 2), item(event, "VIP", 1));

        mvc.perform(get("/bookings/{id}", id).with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.eventId").value(event.getId()))
                .andExpect(jsonPath("$.bookedAt").isString())
                .andExpect(jsonPath("$.status").value("reserved"))
                .andExpect(jsonPath("$.total").value(2 * 2500 + 9000))
                .andExpect(jsonPath("$.fees").value(0))
                .andExpect(jsonPath("$.clientSecret").doesNotExist())
                .andExpect(jsonPath("$.lines", hasSize(2)))
                .andExpect(jsonPath("$.lines[?(@.name == 'VIP')].id").value(ticketId(event, "VIP")))
                .andExpect(jsonPath("$.lines[?(@.name == 'VIP')].quantity").value(1))
                .andExpect(jsonPath("$.lines[?(@.name == 'VIP')].price").value(9000));
    }

    @Test
    void les_statuts_sont_exposes_avec_les_valeurs_du_contrat() throws Exception {
        Event event = createEvent(ticket("Standard", 1, 2500));
        String rejected = book("alice", item(event, "Standard", 5));
        String awaiting = book("alice", item(event, "Standard", 1));
        setStatus(awaiting, BookingStatus.AWAITING_PAYMENT);

        mvc.perform(get("/bookings/{id}", rejected).with(user("alice"))).andExpect(jsonPath("$.status").value("rejected"));
        mvc.perform(get("/bookings/{id}", awaiting).with(user("alice"))).andExpect(jsonPath("$.status").value("awaiting-payment"));
    }

    @Test
    void une_reservation_inexistante_repond_404() throws Exception {
        mvc.perform(get("/bookings/{id}", "bkg_inconnue").with(user("alice")))
                .andExpect(status().isNotFound());
    }

    @Test
    void je_ne_vois_jamais_la_reservation_d_un_autre_utilisateur() throws Exception {
        Event event = createEvent(ticket("Standard", 10, 2500));
        String aliceBooking = book("alice", item(event, "Standard", 1));

        mvc.perform(get("/bookings/{id}", aliceBooking).with(user("mallory")))
                .andExpect(status().isNotFound());
    }

    @Test
    void je_liste_uniquement_mes_reservations_des_plus_recentes_aux_plus_anciennes() throws Exception {
        String me = "user-" + UUID.randomUUID();
        Event event = createEvent(ticket("Standard", 10, 2500));
        String first = book(me, item(event, "Standard", 1));
        String second = book(me, item(event, "Standard", 2));
        book("someone-else", item(event, "Standard", 1));

        mvc.perform(get("/bookings/user").with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(second, first)))
                .andExpect(jsonPath("$[0].lines[0].quantity").value(2));
    }

    @Test
    void sans_reservation_la_liste_est_vide() throws Exception {
        mvc.perform(get("/bookings/user").with(user("user-" + UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void consulter_ses_reservations_sans_etre_connecte_repond_401() throws Exception {
        mvc.perform(get("/bookings/user")).andExpect(status().isUnauthorized());
        mvc.perform(get("/bookings/{id}", "bkg_x")).andExpect(status().isUnauthorized());
    }

    // --- outils

    private void setStatus(String id, BookingStatus status) {
        Booking booking = bookingRepository.findById(id).orElseThrow();
        booking.setStatus(status);
        bookingRepository.save(booking);
    }

    private String book(String user, BookingItemRequest... items) throws Exception {
        String json = postBooking(user, body(items))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isString())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(json).get("id").asString();
    }

    private ResultActions postBooking(String user, String json) throws Exception {
        return mvc.perform(post("/bookings").with(user(user)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String body(BookingItemRequest... items) {
        return jsonMapper.writeValueAsString(Map.of("items", List.of(items)));
    }

    private Booking load(String id) {
        return transaction.execute(status -> {
            Booking booking = bookingRepository.findById(id).orElseThrow();
            booking.getLines().size(); // charge les lignes dans la transaction
            return booking;
        });
    }

    private static JwtRequestPostProcessor user(String subject) {
        return jwt().jwt(token -> token.subject(subject));
    }

    private static BookingItemRequest item(Event event, String ticketName, int quantity) {
        return new BookingItemRequest(ticketId(event, ticketName), quantity);
    }

    private static String ticketId(Event event, String ticketName) {
        return event.getId() + "_" + ticketName;
    }

    private record TicketSpec(String name, int quantity, long priceInCents) {
    }

    private static TicketSpec ticket(String name, int quantity, long priceInCents) {
        return new TicketSpec(name, quantity, priceInCents);
    }

    private Event createEvent(TicketSpec... tickets) {
        return createEvent(Instant.now().plus(Duration.ofDays(7)), tickets);
    }

    private Event createEvent(Instant startAt, TicketSpec... tickets) {
        Event event = new Event("evt_" + UUID.randomUUID());
        event.setTitle("Concert");
        event.setVenue(new Venue("Salle", "1 rue de la Paix", 48.85, 2.35));
        event.setStartAt(startAt);
        event.setUpdatedAt(Instant.now());
        for (TicketSpec spec : tickets) {
            Ticket ticket = new Ticket(ticketId(event, spec.name()), event);
            ticket.setName(spec.name());
            ticket.setQuantity(spec.quantity());
            ticket.setPriceInCents(spec.priceInCents());
            event.getTickets().add(ticket);
        }
        return eventRepository.save(event);
    }
}
