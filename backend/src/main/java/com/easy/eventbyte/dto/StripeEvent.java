package com.easy.eventbyte.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Événement envoyé par Stripe au webhook (champs utiles uniquement). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StripeEvent(String id, String type, StripeEventData data) {
}
