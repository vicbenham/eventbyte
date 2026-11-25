package com.easy.eventbyte.service;

import com.easy.eventbyte.dto.EventResponse;
import com.easy.eventbyte.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

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

    /** Événements à venir les plus proches de la position, dans un rayon de {@value #DISCOVER_RADIUS_KM} km. */
    public List<EventResponse> discover(double lat, double lng) {
        double latDelta = DISCOVER_RADIUS_KM / KM_PER_DEGREE_OF_LATITUDE;
        double lngDelta = latDelta / Math.max(Math.cos(Math.toRadians(lat)), 0.01);

        return eventRepository.findUpcomingNear(
                        lat, lng,
                        lat - latDelta, lat + latDelta,
                        lng - lngDelta, lng + lngDelta,
                        Instant.now(),
                        DISCOVER_LIMIT)
                .stream()
                // likedCount : les likes arrivent avec l'US-8
                .map(event -> EventResponse.from(event, 0))
                .toList();
    }
}
