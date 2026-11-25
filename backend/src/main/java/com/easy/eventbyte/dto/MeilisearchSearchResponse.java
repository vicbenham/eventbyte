package com.easy.eventbyte.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Réponse de POST /indexes/events/search (seuls les ids sont demandés). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MeilisearchSearchResponse(List<MeilisearchHit> hits) {
}
