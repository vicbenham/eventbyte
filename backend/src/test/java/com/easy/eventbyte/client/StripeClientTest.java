package com.easy.eventbyte.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Le client est testé contre un faux serveur Stripe local. */
class StripeClientTest {

    HttpServer stripe;
    String baseUrl;
    final AtomicReference<String> lastBody = new AtomicReference<>();
    final AtomicReference<String> lastAuthorization = new AtomicReference<>();
    final AtomicReference<String> lastIdempotencyKey = new AtomicReference<>();
    volatile int status = 200;
    volatile String response = """
            {"id":"pi_123","object":"payment_intent","amount":5090,"client_secret":"pi_123_secret_abc","status":"requires_payment_method"}
            """;

    @BeforeEach
    void startFakeStripe() throws IOException {
        stripe = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        stripe.createContext("/v1/payment_intents", exchange -> {
            lastBody.set(URLDecoder.decode(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), StandardCharsets.UTF_8));
            lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            lastIdempotencyKey.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        stripe.start();
        baseUrl = "http://localhost:" + stripe.getAddress().getPort();
    }

    @AfterEach
    void stopFakeStripe() {
        stripe.stop(0);
    }

    @Test
    void cree_un_payment_intent_en_centimes_et_en_euros() {
        var intent = new StripeClient(baseUrl, "sk_test_123").createPaymentIntent("bkg_1", 5090);

        assertThat(intent.id()).isEqualTo("pi_123");
        assertThat(intent.clientSecret()).isEqualTo("pi_123_secret_abc");
        assertThat(lastBody.get())
                .contains("amount=5090")
                .contains("currency=eur")
                .contains("automatic_payment_methods[enabled]=true")
                .contains("metadata[bookingId]=bkg_1");
        assertThat(lastAuthorization.get()).isEqualTo("Bearer sk_test_123");
    }

    @Test
    void l_id_de_la_reservation_sert_de_cle_d_idempotence() {
        new StripeClient(baseUrl, "sk_test_123").createPaymentIntent("bkg_1", 5090);

        assertThat(lastIdempotencyKey.get()).isEqualTo("booking-bkg_1");
    }

    @Test
    void une_erreur_stripe_devient_une_PaymentProviderException() {
        status = 402;
        response = """
                {"error":{"type":"invalid_request_error","message":"Amount must be at least 50 cents"}}
                """;

        assertThatThrownBy(() -> new StripeClient(baseUrl, "sk_test_123").createPaymentIntent("bkg_1", 10))
                .isInstanceOf(PaymentProviderException.class);
    }

    @Test
    void stripe_injoignable_devient_une_PaymentProviderException() {
        stripe.stop(0);

        assertThatThrownBy(() -> new StripeClient(baseUrl, "sk_test_123").createPaymentIntent("bkg_1", 5090))
                .isInstanceOf(PaymentProviderException.class);
    }

    @Test
    void sans_cle_le_client_n_est_pas_configure() {
        assertThat(new StripeClient(baseUrl, "").isConfigured()).isFalse();
        assertThat(new StripeClient(baseUrl, "sk_test_123").isConfigured()).isTrue();
    }
}
