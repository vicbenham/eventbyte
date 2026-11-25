package com.easy.eventbyte.repository;

import com.easy.eventbyte.model.EventLike;
import com.easy.eventbyte.model.EventLikeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface EventLikeRepository extends JpaRepository<EventLike, EventLikeId> {

    /** Like idempotent, y compris pour deux requêtes simultanées : un like existant est laissé tel quel. */
    @Modifying
    @Query(value = """
            INSERT INTO event_likes (user_id, event_id, liked_at)
            VALUES (:userId, :eventId, now())
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    void like(@Param("userId") String userId, @Param("eventId") String eventId);

    /** Retire le like s'il existe, sans erreur sinon. */
    @Modifying
    @Query("DELETE FROM EventLike l WHERE l.userId = :userId AND l.eventId = :eventId")
    void unlike(@Param("userId") String userId, @Param("eventId") String eventId);

    long countByEventId(String eventId);

    /** Likes par événement, en une requête (les événements sans like sont absents du résultat). */
    @Query("""
            SELECT l.eventId AS eventId, COUNT(l) AS count
            FROM EventLike l
            WHERE l.eventId IN :eventIds
            GROUP BY l.eventId
            """)
    List<EventLikeCount> countByEventIds(@Param("eventIds") Collection<String> eventIds);
}
