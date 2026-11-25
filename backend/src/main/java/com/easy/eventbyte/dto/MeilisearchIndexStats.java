package com.easy.eventbyte.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Réponse de GET /indexes/events/stats. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MeilisearchIndexStats(long numberOfDocuments) {
}
