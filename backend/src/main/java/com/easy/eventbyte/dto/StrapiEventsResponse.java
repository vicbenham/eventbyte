package com.easy.eventbyte.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Réponse de l'API REST Strapi GET /api/events. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StrapiEventsResponse(List<StrapiEventData> data) {
}
