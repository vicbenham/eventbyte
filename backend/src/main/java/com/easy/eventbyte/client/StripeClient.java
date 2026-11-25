package com.easy.eventbyte.client;

import com.easy.eventbyte.dto.StripePaymentIntent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Paiements Stripe via l'API HTTP (clé secrète de test sk_test_..., STRIPE_SECRET_KEY). */
@Slf4j
@Component
public class StripeClient {

    private final RestClient restClient;
    private final boolean configured;

    public StripeClient(@Value("${stripe.api-url}") String apiUrl, @Value("${stripe.secret-key:}") String secretKey) {
        this.configured = !secretKey.isBlank();
        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                .build();
        if (!configured) {
            log.warn("STRIPE_SECRET_KEY non défini : les réservations resteront reserved, sans paiement possible");
        }
    }

    public boolean isConfigured() {
        return configured;
    }

    /**
     * Crée le PaymentIntent d'une réservation. L'id de la réservation sert de clé d'idempotence :
     * une requête rejouée renvoie le même PaymentIntent au lieu d'en créer un second.
     */
    public StripePaymentIntent createPaymentIntent(String bookingId, long amountInCents) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("amount", String.valueOf(amountInCents));
        form.add("currency", "eur");
        form.add("automatic_payment_methods[enabled]", "true");
        form.add("metadata[bookingId]", bookingId);
        try {
            StripePaymentIntent intent = restClient.post()
                    .uri("/v1/payment_intents")
                    .header("Idempotency-Key", "booking-" + bookingId)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(StripePaymentIntent.class);
            if (intent == null || intent.clientSecret() == null) {
                throw new PaymentProviderException("Réponse Stripe sans client_secret", null);
            }
            return intent;
        } catch (RestClientException e) {
            throw new PaymentProviderException("Création du paiement Stripe impossible : " + e.getMessage(), e);
        }
    }

    /** Rembourse intégralement un paiement (idempotent : un second appel ne rembourse pas deux fois). */
    public void refund(String paymentIntentId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("payment_intent", paymentIntentId);
        try {
            restClient.post()
                    .uri("/v1/refunds")
                    .header("Idempotency-Key", "refund-" + paymentIntentId)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new PaymentProviderException("Remboursement Stripe impossible : " + e.getMessage(), e);
        }
    }

    /** Annule un paiement pas encore effectué : il ne pourra plus être payé. */
    public void cancelPaymentIntent(String paymentIntentId) {
        try {
            restClient.post()
                    .uri("/v1/payment_intents/{id}/cancel", paymentIntentId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new PaymentProviderException("Annulation du paiement Stripe impossible : " + e.getMessage(), e);
        }
    }
}
