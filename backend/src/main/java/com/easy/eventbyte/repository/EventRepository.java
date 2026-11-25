package com.easy.eventbyte.repository;

import com.easy.eventbyte.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, String> {

    /**
     * Événements à venir dont le lieu est dans la zone [minLat, maxLat] x [minLng, maxLng],
     * du plus proche au plus éloigné de (lat, lng).
     * La distance de tri est une approximation plane, suffisante à l'échelle d'une ville ou d'une région.
     */
    @Query(value = """
            SELECT * FROM events e
            WHERE e.start_at >= :now
              AND e.venue_latitude BETWEEN :minLat AND :maxLat
              AND e.venue_longitude BETWEEN :minLng AND :maxLng
            ORDER BY power(e.venue_latitude - :lat, 2)
                   + power((e.venue_longitude - :lng) * cos(radians(:lat)), 2)
            LIMIT :limit
            """, nativeQuery = true)
    List<Event> findUpcomingNear(@Param("lat") double lat,
                                 @Param("lng") double lng,
                                 @Param("minLat") double minLat,
                                 @Param("maxLat") double maxLat,
                                 @Param("minLng") double minLng,
                                 @Param("maxLng") double maxLng,
                                 @Param("now") Instant now,
                                 @Param("limit") int limit);
}
