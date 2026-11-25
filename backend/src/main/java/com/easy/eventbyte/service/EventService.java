package com.easy.eventbyte.service;

import com.easy.eventbyte.client.MeilisearchClient;
import com.easy.eventbyte.dto.EventResponse;
import com.easy.eventbyte.model.Event;
import com.easy.eventbyte.repository.EventLikeCount;
import com.easy.eventbyte.repository.EventLikeRepository;
import com.easy.eventbyte.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Consultation des événements par le public. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {

    /** Rayon de recherche autour de l'utilisateur. */
    static final double DISCOVER_RADIUS_KM = 100;
    /** Nombre maximum d'événements renvoyés. */
    static final int DISCOVER_LIMIT = 50;
    private static final double KM_PER_DEGREE_OF_LATITUDE = 111.32;

    private final EventRepository eventRepository;
    private final MeilisearchClient meilisearchClient;
    private final EventLikeRepository eventLikeRepository;

    /** Événements à venir les plus proches de la position, dans un rayon de {@value #DISCOVER_RADIUS_KM} km. */
    public List<EventResponse> discover(double lat, double lng) {
        double latDelta = DISCOVER_RADIUS_KM / KM_PER_DEGREE_OF_LATITUDE;
        double lngDelta = latDelta / Math.max(Math.cos(Math.toRadians(lat)), 0.01);

        return toResponses(eventRepository.findUpcomingNear(
                lat, lng,
                lat - latDelta, lat + latDelta,
                lng - lngDelta, lng + lngDelta,
                Instant.now(),
                DISCOVER_LIMIT));
    }

    /**
     * Événements à venir dans le rayon (km) qui correspondent au mot-clé (titre, description, lieu),
     * du plus pertinent au moins pertinent puis du plus proche au plus loin. Sans mot-clé : tous ceux du rayon.
     */
    public List<EventResponse> search(String query, double lat, double lng, double radiusKm) {
        List<String> ids = meilisearchClient.search(query.trim(), lat, lng, radiusKm,
                Instant.now().getEpochSecond(), DISCOVER_LIMIT);
        return getByIds(ids);
    }

    /**
     * Événements correspondant aux ids, dans l'ordre demandé (favoris, réservations).
     * Les ids inconnus, supprimés ou sans date sont ignorés, les doublons ne sont renvoyés qu'une fois.
     */
    public List<EventResponse> getByIds(List<String> ids) {
        List<String> distinctIds = ids.stream().map(String::trim).filter(id -> !id.isEmpty()).distinct().toList();
        Map<String, Event> eventsById = eventRepository.findAllById(distinctIds).stream()
                .collect(Collectors.toMap(Event::getId, Function.identity()));

        return toResponses(distinctIds.stream()
                .map(eventsById::get)
                .filter(event -> event != null && event.getStartAt() != null)
                .toList());
    }

    /**
     * Détail d'un événement, y compris passé (favoris, réservations).
     * Un événement sans date n'est pas exposé : le frontend ne saurait pas l'afficher.
     */
    public EventResponse getById(String id) {
        return eventRepository.findById(id)
                .filter(event -> event.getStartAt() != null)
                .map(event -> EventResponse.from(event, eventLikeRepository.countByEventId(id)))
                .orElseThrow(() -> new EventNotFoundException(id));
    }

    /** Convertit une liste d'événements en chargeant leurs likes en une seule requête. */
    private List<EventResponse> toResponses(List<Event> events) {
        if (events.isEmpty()) {
            return List.of();
        }
        Map<String, Long> likes = eventLikeRepository.countByEventIds(events.stream().map(Event::getId).toList())
                .stream()
                .collect(Collectors.toMap(EventLikeCount::getEventId, EventLikeCount::getCount));

        return events.stream()
                .map(event -> EventResponse.from(event, likes.getOrDefault(event.getId(), 0L)))
                .toList();
    }
}
