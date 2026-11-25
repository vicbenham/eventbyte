package com.easy.eventbyte.dto;

import com.easy.eventbyte.domain.Prices;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/** {@code price} est en euros dans Strapi (champ decimal). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TicketMessage(String id, String name, int quantity, BigDecimal price) {

    public long priceInCents() {
        return Prices.eurosToCents(price);
    }
}
