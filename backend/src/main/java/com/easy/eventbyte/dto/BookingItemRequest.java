package com.easy.eventbyte.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** Ticket demandé : {@code id} est l'id du ticket. */
public record BookingItemRequest(@NotBlank String id, @Min(1) int quantity) {
}
