package com.easy.eventbyte.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;


@JsonIgnoreProperties(ignoreUnknown = true)
public record StrapiEventMessage(
        String id,
        String title,
        String description,
        String cover,
        VenueMessage venue,
        List<TicketMessage> tickets
) {
}
