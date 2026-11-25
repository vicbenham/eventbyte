package com.easy.eventbyte.service;

import com.easy.eventbyte.client.StripeClient;
import com.easy.eventbyte.dto.BookingItemRequest;
import com.easy.eventbyte.dto.BookingResponse;
import com.easy.eventbyte.dto.StripePaymentIntent;
import com.easy.eventbyte.domain.BookingExpiration;
import com.easy.eventbyte.domain.BookingRules;
import com.easy.eventbyte.domain.BookingStatus;
import com.easy.eventbyte.domain.RequestedTicket;
import com.easy.eventbyte.domain.TicketOffer;
import com.easy.eventbyte.model.Booking;
import com.easy.eventbyte.model.BookingLine;
import com.easy.eventbyte.model.Ticket;
import com.easy.eventbyte.repository.BookingRepository;
import com.easy.eventbyte.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class BookingService {

    private final BookingRepository bookingRepository;
    private final TicketRepository ticketRepository;
    private final TicketStockService ticketStockService;
    private final StripeClient stripeClient;
    private final BookingEventPublisher bookingEventPublisher;

    /** Délai pour payer une réservation (aligné sur le compte à rebours du frontend). */
    @Value("${booking.expiration}")
    private Duration expiration;

    /**
     * Crée une réservation (statut requested) puis la passe en reserved si le stock suffit pour
     * toutes les lignes, en rejected sinon. Une réservation rejetée n'immobilise aucune place.
     * Une réservation reserved passe ensuite en awaiting-payment avec le clientSecret de son
     * PaymentIntent Stripe : le frontend affiche alors le formulaire de paiement.
     * Si Stripe échoue, la requête échoue (502) et les places sont rendues.
     *
     * @return l'id de la réservation
     */
    public String create(String userId, List<BookingItemRequest> items) {
        Map<String, Integer> quantities = BookingRules.mergeByTicket(items.stream()
                .map(item -> new RequestedTicket(item.id(), item.quantity()))
                .toList());

        List<Ticket> tickets = ticketRepository.findAllById(quantities.keySet());
        String eventId = BookingRules.checkBookable(quantities.size(), tickets.stream()
                .map(t -> new TicketOffer(t.getId(), t.getEvent().getId(), t.getEvent().getStartAt()))
                .toList(), Instant.now());

        List<BookingLine> lines = tickets.stream()
                .map(t -> new BookingLine(t.getId(), t.getName(), quantities.get(t.getId()), t.getPriceInCents()))
                .toList();
        Booking booking = bookingRepository.save(new Booking(userId, eventId, lines));

        // Décision atomique dans Redis : deux réservations du dernier ticket ne peuvent pas réussir toutes les deux
        boolean reserved = ticketStockService.tryReserve(tickets, quantities);
        booking.setStatus(reserved ? BookingStatus.RESERVED : BookingStatus.REJECTED);
        if (reserved) {
            booking.setExpiresAt(BookingExpiration.deadline(booking.getBookedAt(), expiration));
            requestPayment(booking);
        }
        log.info("Réservation {} de {} : {}", booking.getId(), userId, booking.getStatus());
        bookingEventPublisher.publishAfterCommit(userId, booking.getId());
        return booking.getId();
    }

    private void requestPayment(Booking booking) {
        if (!stripeClient.isConfigured()) {
            return;
        }
        StripePaymentIntent intent = stripeClient.createPaymentIntent(booking.getId(), booking.getTotal());
        booking.setPaymentIntentId(intent.id());
        booking.setClientSecret(intent.clientSecret());
        booking.setStatus(BookingStatus.AWAITING_PAYMENT);
    }

    /** Réservation de l'utilisateur ; 404 si elle n'existe pas ou appartient à quelqu'un d'autre. */
    @Transactional(readOnly = true)
    public BookingResponse getById(String userId, String bookingId) {
        return bookingRepository.findByIdAndUserId(bookingId, userId)
                .map(BookingResponse::from)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
    }

    /** Réservations de l'utilisateur, les plus récentes d'abord. */
    @Transactional(readOnly = true)
    public List<BookingResponse> getUserBookings(String userId) {
        return bookingRepository.findByUserIdOrderByBookedAtDesc(userId).stream()
                .map(BookingResponse::from)
                .toList();
    }
}
