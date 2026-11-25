package com.easy.eventbyte.service;

import com.easy.eventbyte.model.Booking;
import com.easy.eventbyte.model.BookingLine;
import com.easy.eventbyte.model.BookingStatus;
import com.easy.eventbyte.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Expiration des réservations non payées : statut expired et places rendues au stock. */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingExpirationService {

    private static final int BATCH_SIZE = 500;

    private final BookingRepository bookingRepository;
    private final TicketStockService ticketStockService;

    /** Réservations non payées dont l'échéance est dépassée. */
    @Transactional(readOnly = true)
    public List<String> findOverdue() {
        return bookingRepository.findOverdueIds(BookingStatus.AWAITING_PAYMENT_STATUSES, Instant.now(), Limit.of(BATCH_SIZE));
    }

    /**
     * Expire la réservation si elle est toujours non payée et échue, puis rend ses places au stock.
     *
     * @return true si cet appel l'a expirée (false si déjà payée, expirée ou traitée par une autre instance)
     */
    @Transactional
    public boolean expire(String bookingId) {
        int updated = bookingRepository.expire(bookingId, BookingStatus.EXPIRED,
                BookingStatus.AWAITING_PAYMENT_STATUSES, Instant.now());
        if (updated == 0) {
            return false;
        }
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        Map<String, Integer> places = booking.getLines().stream()
                .collect(Collectors.toMap(BookingLine::getTicketId, BookingLine::getQuantity));
        ticketStockService.releaseAfterCommit(places);
        log.info("Réservation {} expirée, places rendues : {}", bookingId, places);
        return true;
    }
}
