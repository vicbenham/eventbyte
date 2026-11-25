package com.easy.eventbyte.client;

import com.easy.eventbyte.dto.EventDocument;
import com.easy.eventbyte.dto.MeilisearchHit;
import com.easy.eventbyte.dto.MeilisearchIndexStats;
import com.easy.eventbyte.dto.MeilisearchSearchResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Index de recherche des événements dans Meilisearch (API HTTP).
 * Les écritures sont asynchrones côté Meilisearch : un document est cherchable quelques millisecondes après.
 */
@Component
public class MeilisearchClient {

    static final String INDEX = "events";

    private final RestClient restClient;

    public MeilisearchClient(@Value("${meilisearch.host}") String host, @Value("${meilisearch.api-key}") String apiKey) {
        this.restClient = RestClient.builder()
                .baseUrl(host)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }

    /** Champs cherchables, filtre géographique et filtre sur la date. Crée l'index s'il n'existe pas. */
    public void configureIndex() {
        restClient.patch()
                .uri("/indexes/{index}/settings", INDEX)
                .body(Map.of(
                        "searchableAttributes", List.of("title", "description", "venueName", "venueAddress"),
                        "filterableAttributes", List.of("_geo", "startAt"),
                        "sortableAttributes", List.of("_geo")))
                .retrieve()
                .toBodilessEntity();
    }

    /** Ajoute ou remplace les documents (même id = mise à jour). */
    public void index(List<EventDocument> documents) {
        restClient.post()
                .uri("/indexes/{index}/documents?primaryKey=id", INDEX)
                .body(documents)
                .retrieve()
                .toBodilessEntity();
    }

    public void delete(String eventId) {
        restClient.delete()
                .uri("/indexes/{index}/documents/{id}", INDEX, eventId)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Ids des événements à venir dans le rayon, du plus pertinent au moins pertinent
     * (à pertinence égale, du plus proche au plus loin).
     */
    public List<String> search(String query, double lat, double lng, double radiusKm, long nowEpochSecond, int limit) {
        long radiusMeters = Math.round(radiusKm * 1000);
        MeilisearchSearchResponse response = restClient.post()
                .uri("/indexes/{index}/search", INDEX)
                .body(Map.of(
                        "q", query,
                        "filter", "_geoRadius(%s, %s, %d) AND startAt >= %d".formatted(lat, lng, radiusMeters, nowEpochSecond),
                        "sort", List.of("_geoPoint(%s, %s):asc".formatted(lat, lng)),
                        "attributesToRetrieve", List.of("id"),
                        "limit", limit))
                .retrieve()
                .body(MeilisearchSearchResponse.class);

        return response == null ? List.of() : response.hits().stream().map(MeilisearchHit::id).toList();
    }

    /** Nombre de documents indexés (0 si l'index n'existe pas encore). */
    public long countDocuments() {
        try {
            MeilisearchIndexStats stats = restClient.get()
                    .uri("/indexes/{index}/stats", INDEX)
                    .retrieve()
                    .body(MeilisearchIndexStats.class);
            return stats == null ? 0 : stats.numberOfDocuments();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return 0;
            }
            throw e;
        }
    }
}
