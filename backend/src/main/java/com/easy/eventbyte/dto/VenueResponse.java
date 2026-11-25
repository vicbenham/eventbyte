package com.easy.eventbyte.dto;

import com.easy.eventbyte.model.Venue;

/** Le contrat d'API impose {@code adresse} (et non address) et des coordonnées imbriquées. */
public record VenueResponse(String name, String adresse, CoordinatesResponse coordinates) {

    public static VenueResponse from(Venue venue) {
        return new VenueResponse(
                venue.getName(),
                venue.getAddress(),
                new CoordinatesResponse(venue.getLatitude(), venue.getLongitude()));
    }
}
