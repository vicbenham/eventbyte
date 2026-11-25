package com.easy.eventbyte.exposition;

import com.easy.eventbyte.TestcontainersConfiguration;
import com.easy.eventbyte.client.StripeClient;
import com.easy.eventbyte.client.StripeWebhookVerifier;
import com.easy.eventbyte.dto.BookingItemRequest;
import com.easy.eventbyte.dto.StripePaymentIntent;
import com.easy.eventbyte.model.Booking;
import com.easy.eventbyte.model.BookingStatus;
import com.easy.eventbyte.model.Event;
import com.easy.eventbyte.model.Ticket;
import com.easy.eventbyte.model.Venue;
import com.easy.eventbyte.repository.BookingRepository;
import com.easy.eventbyte.repository.EventRepository;
import com.easy.eventbyte.service.BookingExpirationService;
import com.easy.eventbyte.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-17 : Stripe prévient le backend du paiement par webhook, la réservation passe en confirmed.
 * Les appels à Stripe sont simulés ; les webhooks sont signés comme le fait Stripe.
 */
@SpringBootTest(properties = {"stripe.webhook-secret=whsec_test", "booking.expiration-check-interval=PT1H"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PaymentWebhookIntegrationTest {

    static final String WEBHOOK = "/payments/stripe/webhook";

    @Autowired
    MockMvc mvc;
    @Autowired
    StripeWebhookVerifier verifier;
    @Autowired
    BookingService bookingService;
    @Autowired
    BookingExpirationService expirationService;
    @Autowired
    BookingRepository bookingRepository;
    @Autowired
    EventRepository eventRepository;

    @MockitoBean
    StripeClient stripeClient;

    @BeforeEach
    void stripeIsConfigured() {
        when(stripeClient.isConfigured()).thenReturn(true);
        when(stripeClient.createPaymentIntent(anyString(), anyLong()))
                .thenAnswer(call -> new StripePaymentIntent("pi_" + call.getArgument(0), "secret_" + call.getArgument(0)));
    }

    @Test
    void un_paiement_reussi_confirme_la_reservation() throws Exception {
        Booking booking = awaitingPayment();

        sendWebhook(succeeded(booking)).andExpect(status().isOk());

        assertThat(statusOf(booking)).isEqualTo(BookingStatus.CONFIRMED);
        mvc.perform(get("/bookings/{id}", booking.getId())
                        .with(jwt().jwt(t -> t.subject(booking.getUserId()))))
                .andExpect(jsonPath("$.status").value("confirmed"));
    }

    @Test
    void une_reservation_confirmee_garde_ses_places_et_n_expire_plus() throws Exception {
        Booking booking = awaitingPayment();
        sendWebhook(succeeded(booking)).andExpect(status().isOk());

        makeOverdue(booking);

        assertThat(expirationService.expire(booking.getId())).isFalse();
        assertThat(statusOf(booking)).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void un_webhook_rejoue_est_sans_effet() throws Exception {
        Booking booking = awaitingPayment();
        String event = succeeded(booking);

        sendWebhook(event).andExpect(status().isOk());
        sendWebhook(event).andExpect(status().isOk());

        assertThat(statusOf(booking)).isEqualTo(BookingStatus.CONFIRMED);
        verify(stripeClient, never()).refund(anyString());
    }

    @Test
    void un_paiement_apres_expiration_est_rembourse_et_la_reservation_reste_expiree() throws Exception {
        Booking booking = awaitingPayment();
        makeOverdue(booking);
        assertThat(expirationService.expire(booking.getId())).isTrue();

        sendWebhook(succeeded(booking)).andExpect(status().isOk());

        assertThat(statusOf(booking)).isEqualTo(BookingStatus.EXPIRED);
        verify(stripeClient).refund(booking.getPaymentIntentId());
    }

    @Test
    void a_l_expiration_le_paiement_est_annule_chez_stripe() {
        Booking booking = awaitingPayment();
        makeOverdue(booking);

        expirationService.expire(booking.getId());

        verify(stripeClient).cancelPaymentIntent(booking.getPaymentIntentId());
    }

    @Test
    void un_montant_different_du_total_ne_confirme_pas_la_reservation() throws Exception {
        Booking booking = awaitingPayment();

        sendWebhook(event("payment_intent.succeeded", booking.getPaymentIntentId(), booking.getTotal() - 1))
                .andExpect(status().isOk());

        assertThat(statusOf(booking)).isEqualTo(BookingStatus.AWAITING_PAYMENT);
    }

    @Test
    void un_webhook_mal_signe_est_refuse_et_ne_change_rien() throws Exception {
        Booking booking = awaitingPayment();
        String event = succeeded(booking);

        mvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(event)
                        .header("Stripe-Signature", "t=" + Instant.now().getEpochSecond() + ",v1=" + "0".repeat(64)))
                .andExpect(status().isBadRequest());
        mvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(event))
                .andExpect(status().isBadRequest());

        assertThat(statusOf(booking)).isEqualTo(BookingStatus.AWAITING_PAYMENT);
    }

    @Test
    void les_autres_evenements_et_paiements_inconnus_sont_ignores() throws Exception {
        Booking booking = awaitingPayment();

        sendWebhook(event("payment_intent.payment_failed", booking.getPaymentIntentId(), 0)).andExpect(status().isOk());
        sendWebhook(event("payment_intent.succeeded", "pi_inconnu", 1000)).andExpect(status().isOk());

        assertThat(statusOf(booking)).isEqualTo(BookingStatus.AWAITING_PAYMENT);
    }

    // --- outils

    private ResultActions sendWebhook(String payload) throws Exception {
        long t = Instant.now().getEpochSecond();
        return mvc.perform(post(WEBHOOK)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .header("Stripe-Signature", "t=" + t + ",v1=" + verifier.sign(t + "." + payload)));
    }

    private static String succeeded(Booking booking) {
        return event("payment_intent.succeeded", booking.getPaymentIntentId(), booking.getTotal());
    }

    /** Événement au format Stripe (champs inutiles au backend inclus, comme dans un vrai webhook). */
    private static String event(String type, String paymentIntentId, long amountReceived) {
        return """
                {"id":"evt_%s","object":"event","type":"%s","livemode":false,
                 "data":{"object":{"id":"%s","object":"payment_intent","amount_received":%d,"currency":"eur","status":"succeeded"}}}
                """.formatted(UUID.randomUUID(), type, paymentIntentId, amountReceived);
    }

    private Booking awaitingPayment() {
        String ticketId = createEventWithTicket();
        String id = bookingService.create("user-" + UUID.randomUUID(), List.of(new BookingItemRequest(ticketId, 2)));
        Booking booking = bookingRepository.findById(id).orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.AWAITING_PAYMENT);
        return booking;
    }

    private BookingStatus statusOf(Booking booking) {
        return bookingRepository.findById(booking.getId()).orElseThrow().getStatus();
    }

    private void makeOverdue(Booking booking) {
        Booking current = bookingRepository.findById(booking.getId()).orElseThrow();
        current.setExpiresAt(Instant.now().minusSeconds(1));
        bookingRepository.save(current);
    }

    private String createEventWithTicket() {
        Event event = new Event("evt_" + UUID.randomUUID());
        event.setTitle("Concert");
        event.setVenue(new Venue("Salle", "1 rue de la Paix", 48.85, 2.35));
        event.setStartAt(Instant.now().plus(Duration.ofDays(7)));
        event.setUpdatedAt(Instant.now());
        Ticket ticket = new Ticket(event.getId() + "_Standard", event);
        ticket.setName("Standard");
        ticket.setQuantity(10);
        ticket.setPriceInCents(2500);
        event.getTickets().add(ticket);
        return eventRepository.save(event).getTickets().getFirst().getId();
    }
}
