package com.easy.eventbyte.dto;

/** Format attendu par Meilisearch dans le champ {@code _geo}. */
public record GeoPoint(double lat, double lng) {
}
