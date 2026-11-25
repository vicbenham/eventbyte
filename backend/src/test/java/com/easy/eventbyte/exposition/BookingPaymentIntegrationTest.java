package com.easy.eventbyte.exposition;

import com.easy.eventbyte.TestcontainersConfiguration;
import com.easy.eventbyte.client.PaymentProviderException;
import com.easy.eventbyte.client.StripeClient;
import com.easy.eventbyte.dto.StripePaymentIntent;
import com.easy.eventbyte.model.Event;
import com.easy.eventbyte.model.Ticket;
import com.easy.eventbyte.model.Venue;
import com.easy.eventbyte.repository.BookingRepository;
import com.easy.eventbyte.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US-16 : une réservation acceptée attend son paiement Stripe (Stripe est simulé). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BookingPaymentIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    JsonMapper jsonMapper;
    @Autowired
    EventRepository eventRepository;
    @Autowired
    BookingRepository bookingRepository;

    @MockitoBean
    StripeClient stripeClient;

    @BeforeEach
    void stripeIsConfigured() {
        when(stripeClient.isConfigured()).thenReturn(true);
        when(stripeClient.createPaymentIntent(anyString(), anyLong()))
                .thenAnswer(call -> new StripePaymentIntent("pi_" + call.getArgument(0), "secret_" + call.getArgument(0)));
    }

    @Test
    void une_reservation_acceptee_attend_son_paiement_avec_un_clientSecret() throws Exception {
        String ticketId = createEventWithTicket(10, 2500);

        String id = book(ticketId, 2);

        mvc.perform(get("/bookings/{id}", id).with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("awaiting-payment"))
                .andExpect(jsonPath("$.clientSecret").value("secret_" + id))
                .andExpect(jsonPath("$.fees").value(2 * 30))
                .andExpect(jsonPath("$.total").value(2 * 2500 + 2 * 30));
        assertThat(bookingRepository.findById(id).orElseThrow().getPaymentIntentId()).isEqualTo("pi_" + id);
    }

    @Test
    void le_montant_demande_a_stripe_est_le_total_en_centimes_frais_compris() throws Exception {
        String ticketId = createEventWithTicket(10, 2500);

        String id = book(ticketId, 3);

        verify(stripeClient).createPaymentIntent(eq(id), eq(3 * 2500L + 3 * 30));
    }

    @Test
    void une_reservation_rejetee_ne_demande_pas_de_paiement() throws Exception {
        String ticketId = createEventWithTicket(1, 2500);

        String id = book(ticketId, 5);

        mvc.perform(get("/bookings/{id}", id).with(user()))
                .andExpect(jsonPath("$.status").value("rejected"))
                .andExpect(jsonPath("$.clientSecret").doesNotExist());
    }

    @Test
    void si_stripe_echoue_la_reservation_est_annulee_et_les_places_rendues() throws Exception {
        String ticketId = createEventWithTicket(2, 2500);
        when(stripeClient.createPaymentIntent(anyString(), anyLong()))
                .thenThrow(new PaymentProviderException("Stripe indisponible", null));

        postBooking(ticketId, 2).andExpect(status().isBadGateway());

        mvc.perform(get("/bookings/user").with(user())).andExpect(jsonPath("$[?(@.lines[0].id == '" + ticketId + "')]").isEmpty());
        mvc.perform(get("/events/{id}/tickets", ticketId.replace("_Standard", "")))
                .andExpect(jsonPath("$[0].quantity").value(2));
    }

    // --- outils

    private final String userId = "user-" + UUID.randomUUID();

    private JwtRequestPostProcessor user() {
        return jwt().jwt(token -> token.subject(userId));
    }

    private String book(String ticketId, int quantity) throws Exception {
        String json = postBooking(ticketId, quantity).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(json).get("id").asString();
    }

    private ResultActions postBooking(String ticketId, int quantity) throws Exception {
        return mvc.perform(post("/bookings").with(user())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"id\":\"%s\",\"quantity\":%d}]}".formatted(ticketId, quantity)));
    }

    private String createEventWithTicket(int quantity, long priceInCents) {
        Event event = new Event("evt_" + UUID.randomUUID());
        event.setTitle("Concert");
        event.setVenue(new Venue("Salle", "1 rue de la Paix", 48.85, 2.35));
        event.setStartAt(Instant.now().plus(Duration.ofDays(7)));
        event.setUpdatedAt(Instant.now());
        Ticket ticket = new Ticket(event.getId() + "_Standard", event);
        ticket.setName("Standard");
        ticket.setQuantity(quantity);
        ticket.setPriceInCents(priceInCents);
        event.getTickets().add(ticket);
        return eventRepository.save(event).getTickets().getFirst().getId();
    }
}
