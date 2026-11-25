package com.easy.eventbyte.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Corps de POST /bookings. */
public record CreateBookingRequest(@NotEmpty List<@Valid BookingItemRequest> items) {
}
