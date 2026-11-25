package com.easy.eventbyte.repository;

import com.easy.eventbyte.model.Booking;
import com.easy.eventbyte.domain.BookingStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public interface BookingRepository extends JpaRepository<Booking, String> {

    /** Réservation de l'utilisateur (vide si elle n'existe pas ou appartient à quelqu'un d'autre). */
    @EntityGraph(attributePaths = "lines")
    Optional<Booking> findByIdAndUserId(String id, String userId);

    /** Réservations de l'utilisateur, les plus récentes d'abord, lignes comprises (une seule requête). */
    @EntityGraph(attributePaths = "lines")
    List<Booking> findByUserIdOrderByBookedAtDesc(String userId);

    /**
     * Places immobilisées par ticket, par les réservations dont le statut est dans {@code statuses}.
     * Les tickets sans réservation sont absents du résultat.
     */
    @Query(value = """
            SELECT bl.ticket_id AS ticketId, SUM(bl.quantity) AS held
            FROM booking_lines bl
            JOIN bookings b ON b.id = bl.booking_id
            WHERE b.status IN (:statuses)
              AND bl.ticket_id IN (:ticketIds)
            GROUP BY bl.ticket_id
            """, nativeQuery = true)
    List<TicketHeldCount> countHeldByTicketIds(@Param("ticketIds") Collection<String> ticketIds,
                                               @Param("statuses") Collection<String> statuses);

    /** Réservations non payées dont l'échéance est dépassée, les plus anciennes d'abord. */
    @Query("SELECT b.id FROM Booking b WHERE b.status IN :statuses AND b.expiresAt < :now ORDER BY b.expiresAt")
    List<String> findOverdueIds(@Param("statuses") Collection<BookingStatus> statuses,
                                @Param("now") Instant now,
                                Limit limit);

    /**
     * Passe la réservation en expired si elle est toujours non payée et échue.
     * Conditionnel : si une autre instance (ou un paiement) l'a déjà traitée, rien ne change et la méthode renvoie 0.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Booking b SET b.status = :expired
            WHERE b.id = :id AND b.status IN :statuses AND b.expiresAt < :now
            """)
    int expire(@Param("id") String id,
               @Param("expired") BookingStatus expired,
               @Param("statuses") Collection<BookingStatus> statuses,
               @Param("now") Instant now);

    Optional<Booking> findByPaymentIntentId(String paymentIntentId);

    /**
     * Passe la réservation en confirmed si elle attend encore son paiement.
     * Conditionnel : une réservation expirée entre-temps (ou déjà confirmée) n'est pas modifiée et la méthode renvoie 0.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Booking b SET b.status = :confirmed
            WHERE b.id = :id AND b.status IN :statuses
            """)
    int confirm(@Param("id") String id,
                @Param("confirmed") BookingStatus confirmed,
                @Param("statuses") Collection<BookingStatus> statuses);

    /** Même résultat sous forme de map ticketId -> places immobilisées. */
    default Map<String, Long> heldByTicketId(Collection<String> ticketIds) {
        if (ticketIds.isEmpty()) {
            return Map.of();
        }
        // Statuts qui immobilisent des places : règle du domaine (BookingStatus.HOLDING_STOCK)
        List<String> holding = BookingStatus.HOLDING_STOCK.stream().map(Enum::name).toList();
        return countHeldByTicketIds(ticketIds, holding).stream()
                .collect(Collectors.toMap(TicketHeldCount::getTicketId, TicketHeldCount::getHeld));
    }
}
