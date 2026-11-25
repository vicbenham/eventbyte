package com.easy.eventbyte.repository;

import com.easy.eventbyte.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public interface BookingRepository extends JpaRepository<Booking, String> {

    /**
     * Places immobilisées par ticket : réservations réservées, en attente de paiement ou confirmées
     * (voir BookingStatus.HOLDING_STOCK). Les tickets sans réservation sont absents du résultat.
     */
    @Query(value = """
            SELECT bl.ticket_id AS ticketId, SUM(bl.quantity) AS held
            FROM booking_lines bl
            JOIN bookings b ON b.id = bl.booking_id
            WHERE b.status IN ('RESERVED', 'AWAITING_PAYMENT', 'CONFIRMED')
              AND bl.ticket_id IN (:ticketIds)
            GROUP BY bl.ticket_id
            """, nativeQuery = true)
    List<TicketHeldCount> countHeldByTicketIds(@Param("ticketIds") Collection<String> ticketIds);

    /** Même résultat sous forme de map ticketId -> places immobilisées. */
    default Map<String, Long> heldByTicketId(Collection<String> ticketIds) {
        if (ticketIds.isEmpty()) {
            return Map.of();
        }
        return countHeldByTicketIds(ticketIds).stream()
                .collect(Collectors.toMap(TicketHeldCount::getTicketId, TicketHeldCount::getHeld));
    }
}
