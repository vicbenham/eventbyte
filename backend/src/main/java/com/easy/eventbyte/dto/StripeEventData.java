package com.easy.eventbyte.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Contenu d'un événement Stripe : pour payment_intent.*, l'objet est le PaymentIntent. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StripeEventData(StripeEventPaymentIntent object) {
}
