package com.easy.eventbyte.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** {@code price} est en euros dans Strapi (champ decimal). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TicketMessage(String id, String name, int quantity, BigDecimal price) {

    public long priceInCents() {
        return toCents(price);
    }

    static long toCents(BigDecimal euros) {
        if (euros == null) return 0;
        return euros.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
