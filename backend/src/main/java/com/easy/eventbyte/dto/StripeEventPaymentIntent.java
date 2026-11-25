package com.easy.eventbyte.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** PaymentIntent tel qu'il figure dans un événement payment_intent.* (montant payé en centimes). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StripeEventPaymentIntent(String id, @JsonProperty("amount_received") long amountReceived) {
}
