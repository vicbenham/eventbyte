package com.easy.eventbyte.dto;

import com.easy.eventbyte.model.Event;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Événement tel qu'il est indexé dans Meilisearch : uniquement ce qui sert à la recherche.
 * {@code startAt} est un timestamp en secondes pour pouvoir filtrer les événements à venir.
 */
public record EventDocument(
        String id,
        String title,
        String description,
        String venueName,
        String venueAddress,
        Long startAt,
        @JsonProperty("_geo") GeoPoint geo
) {

    public static EventDocument from(Event event) {
        return new EventDocument(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getVenue().getName(),
                event.getVenue().getAddress(),
                event.getStartAt() == null ? null : event.getStartAt().getEpochSecond(),
                new GeoPoint(event.getVenue().getLatitude(), event.getVenue().getLongitude()));
    }
}
