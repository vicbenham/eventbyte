package com.easy.eventbyte.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** PaymentIntent Stripe (champs utiles de la réponse de POST /v1/payment_intents). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StripePaymentIntent(String id, @JsonProperty("client_secret") String clientSecret) {
}
