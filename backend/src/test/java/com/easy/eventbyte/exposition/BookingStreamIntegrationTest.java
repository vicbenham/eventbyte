package com.easy.eventbyte.exposition;

import com.easy.eventbyte.TestcontainersConfiguration;
import com.easy.eventbyte.client.StripeClient;
import com.easy.eventbyte.client.StripeWebhookVerifier;
import com.easy.eventbyte.dto.BookingItemRequest;
import com.easy.eventbyte.dto.StripePaymentIntent;
import com.easy.eventbyte.model.Booking;
import com.easy.eventbyte.model.Event;
import com.easy.eventbyte.model.Ticket;
import com.easy.eventbyte.model.Venue;
import com.easy.eventbyte.repository.BookingRepository;
import com.easy.eventbyte.repository.EventRepository;
import com.easy.eventbyte.service.BookingExpirationService;
import com.easy.eventbyte.service.BookingService;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * US-18 : GET /bookings/stream, testé sur un vrai serveur HTTP comme le ferait le navigateur.
 * Les JWT sont signés par une clé de test (à la place de Keycloak) ; Stripe est simulé.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"stripe.webhook-secret=whsec_test", "booking.expiration-check-interval=PT1H"})
@Import({TestcontainersConfiguration.class, BookingStreamIntegrationTest.TestJwtConfig.class})
class BookingStreamIntegrationTest {

    static final KeyPair KEYS = generateKeys();

    @LocalServerPort
    int port;
    @Autowired
    JsonMapper jsonMapper;
    @Autowired
    BookingService bookingService;
    @Autowired
    BookingExpirationService expirationService;
    @Autowired
    BookingRepository bookingRepository;
    @Autowired
    EventRepository eventRepository;
    @Autowired
    StripeWebhookVerifier webhookVerifier;

    @MockitoBean
    StripeClient stripeClient;

    final HttpClient http = HttpClient.newHttpClient();
    final List<CompletableFuture<?>> openStreams = new ArrayList<>();

    @BeforeEach
    void stripeIsConfigured() {
        when(stripeClient.isConfigured()).thenReturn(true);
        when(stripeClient.createPaymentIntent(anyString(), anyLong()))
                .thenAnswer(call -> new StripePaymentIntent("pi_" + call.getArgument(0), "secret_" + call.getArgument(0)));
    }

    @AfterEach
    void closeStreams() {
        openStreams.forEach(stream -> stream.cancel(true));
    }

    @Test
    void je_recois_ma_reservation_a_sa_creation_au_format_du_contrat() throws Exception {
        String me = newUser();
        BlockingQueue<JsonNode> events = openStream(me);

        String id = book(me);

        JsonNode event = nextEvent(events);
        assertThat(event.get("id").asString()).isEqualTo(id);
        assertThat(event.get("status").asString()).isEqualTo("awaiting-payment");
        assertThat(event.get("clientSecret").asString()).isEqualTo("secret_" + id);
        assertThat(event.get("lines").get(0).get("quantity").asInt()).isEqualTo(2);
        assertThat(event.get("total").asLong()).isEqualTo(2 * 2500 + 2 * 30);
    }

    @Test
    void je_suis_prevenu_de_l_expiration_de_ma_reservation() throws Exception {
        String me = newUser();
        BlockingQueue<JsonNode> events = openStream(me);
        String id = book(me);
        nextEvent(events); // création

        makeOverdue(id);
        expirationService.expire(id);

        JsonNode event = nextEvent(events);
        assertThat(event.get("id").asString()).isEqualTo(id);
        assertThat(event.get("status").asString()).isEqualTo("expired");
    }

    @Test
    void je_suis_prevenu_de_la_confirmation_du_paiement() throws Exception {
        String me = newUser();
        BlockingQueue<JsonNode> events = openStream(me);
        String id = book(me);
        nextEvent(events); // création

        Booking booking = bookingRepository.findById(id).orElseThrow();
        sendPaymentSucceededWebhook(booking.getPaymentIntentId(), booking.getTotal());

        JsonNode event = nextEvent(events);
        assertThat(event.get("status").asString()).isEqualTo("confirmed");
    }

    @Test
    void je_ne_recois_que_mes_propres_reservations() throws Exception {
        String me = newUser();
        String other = newUser();
        BlockingQueue<JsonNode> myEvents = openStream(me);
        BlockingQueue<JsonNode> otherEvents = openStream(other);

        String myBooking = book(me);

        assertThat(nextEvent(myEvents).get("id").asString()).isEqualTo(myBooking);
        assertThat(otherEvents.poll(1, TimeUnit.SECONDS)).isNull();
    }

    @Test
    void tous_mes_onglets_ouverts_sont_prevenus() throws Exception {
        String me = newUser();
        BlockingQueue<JsonNode> firstTab = openStream(me);
        BlockingQueue<JsonNode> secondTab = openStream(me);

        String id = book(me);

        assertThat(nextEvent(firstTab).get("id").asString()).isEqualTo(id);
        assertThat(nextEvent(secondTab).get("id").asString()).isEqualTo(id);
    }

    @Test
    void le_flux_sans_token_repond_401() throws Exception {
        HttpResponse<Void> response = http.send(
                HttpRequest.newBuilder(URI.create(url("/bookings/stream"))).build(),
                HttpResponse.BodyHandlers.discarding());

        assertThat(response.statusCode()).isEqualTo(401);
    }

    // --- outils

    /** Ouvre le flux comme le frontend et range chaque événement booking_updated (JSON) dans une file. */
    private BlockingQueue<JsonNode> openStream(String userId) throws Exception {
        BlockingQueue<JsonNode> events = new LinkedBlockingQueue<>();
        CompletableFuture<Void> connected = new CompletableFuture<>();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url("/bookings/stream")))
                .header("Authorization", "Bearer " + token(userId))
                .header("Accept", "text/event-stream")
                .build();
        openStreams.add(http.sendAsync(request, HttpResponse.BodyHandlers.ofLines()).thenAccept(response -> {
            assertThat(response.statusCode()).isEqualTo(200);
            connected.complete(null);
            String[] currentEvent = {null};
            response.body().forEach(line -> {
                if (line.startsWith("event:")) {
                    currentEvent[0] = line.substring("event:".length()).trim();
                } else if (line.startsWith("data:") && "booking_updated".equals(currentEvent[0])) {
                    events.add(jsonMapper.readTree(line.substring("data:".length())));
                }
            });
        }));
        connected.get(5, TimeUnit.SECONDS);
        Thread.sleep(200); // laisse le serveur enregistrer le flux
        return events;
    }

    private static JsonNode nextEvent(BlockingQueue<JsonNode> events) throws InterruptedException {
        JsonNode event = events.poll(5, TimeUnit.SECONDS);
        assertThat(event).as("événement booking_updated reçu").isNotNull();
        return event;
    }

    private void sendPaymentSucceededWebhook(String paymentIntentId, long amount) throws Exception {
        String payload = """
                {"id":"evt_1","type":"payment_intent.succeeded","data":{"object":{"id":"%s","amount_received":%d}}}
                """.formatted(paymentIntentId, amount);
        long t = Instant.now().getEpochSecond();
        HttpResponse<Void> response = http.send(HttpRequest.newBuilder(URI.create(url("/payments/stripe/webhook")))
                        .header("Content-Type", "application/json")
                        .header("Stripe-Signature", "t=" + t + ",v1=" + webhookVerifier.sign(t + "." + payload))
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build(),
                HttpResponse.BodyHandlers.discarding());
        assertThat(response.statusCode()).isEqualTo(200);
    }

    private String book(String userId) {
        return bookingService.create(userId, List.of(new BookingItemRequest(createEventWithTicket(), 2)));
    }

    private void makeOverdue(String id) {
        Booking booking = bookingRepository.findById(id).orElseThrow();
        booking.setExpiresAt(Instant.now().minusSeconds(1));
        bookingRepository.save(booking);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private static String newUser() {
        return "user-" + UUID.randomUUID();
    }

    private static String token(String subject) {
        RSAKey jwk = new RSAKey.Builder((RSAPublicKey) KEYS.getPublic()).privateKey((RSAPrivateKey) KEYS.getPrivate()).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("http://localhost:8180/realms/eventbyte")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plus(Duration.ofMinutes(5)))
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)))
                .encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
                .getTokenValue();
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

    private static KeyPair generateKeys() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Remplace Keycloak : les JWT des tests sont signés par la clé générée ci-dessus. */
    @TestConfiguration
    static class TestJwtConfig {

        @Bean
        JwtDecoder jwtDecoder() {
            return NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEYS.getPublic()).build();
        }
    }
}
