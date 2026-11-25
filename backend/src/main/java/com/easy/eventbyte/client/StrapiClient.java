package com.easy.eventbyte.client;

import com.easy.eventbyte.dto.StrapiEventData;
import com.easy.eventbyte.dto.StrapiEventsResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.Optional;

/**
 * Lit dans l'API REST de Strapi les champs absents du message RabbitMQ (la date de l'événement).
 * Nécessite un token API Strapi en lecture seule (STRAPI_API_TOKEN).
 */
@Slf4j
@Component
public class StrapiClient {

    private final RestClient restClient;
    private final boolean configured;

    public StrapiClient(@Value("${strapi.url}") String baseUrl, @Value("${strapi.api-token:}") String apiToken) {
        this.configured = !apiToken.isBlank();
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                .build();
        if (!configured) {
            log.warn("STRAPI_API_TOKEN non défini : la date des événements ne sera pas récupérée "
                    + "et ils n'apparaîtront pas dans /events/discover");
        }
    }

    /** Date de début de l'événement, vide si Strapi est injoignable, non configuré ou ne connaît pas l'événement. */
    public Optional<Instant> findStartAt(String eventId) {
        if (!configured) {
            return Optional.empty();
        }
        try {
            StrapiEventsResponse response = restClient.get()
                    .uri(uri -> uri.path("/api/events")
                            .queryParam("filters[uuid][$eq]", eventId)
                            .queryParam("fields[0]", "startAt")
                            .build())
                    .retrieve()
                    .body(StrapiEventsResponse.class);

            if (response == null || response.data() == null || response.data().isEmpty()) {
                return Optional.empty();
            }
            return Optional.ofNullable(response.data().getFirst()).map(StrapiEventData::startAt);
        } catch (RestClientException e) {
            log.warn("Impossible de lire la date de l'événement {} dans Strapi : {}", eventId, e.getMessage());
            return Optional.empty();
        }
    }
}
