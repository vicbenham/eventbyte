package com.easy.eventbyte.model;

import com.easy.eventbyte.domain.BookingPrice;
import com.easy.eventbyte.domain.BookingStatus;
import com.easy.eventbyte.domain.PricedQuantity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Réservation de tickets d'un événement par un utilisateur (sub du JWT). */
@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
public class Booking {

    @Id
    private String id;

    private String userId;

    private String eventId;

    private Instant bookedAt;

    @Enumerated(EnumType.STRING)
    private BookingStatus status;

    /** Montant à payer en centimes : prix des tickets + frais. */
    private long total;

    /** Frais en centimes, inclus dans le total. */
    private long fees;

    /** Secret du PaymentIntent Stripe : le frontend s'en sert pour payer. */
    private String clientSecret;

    private String paymentIntentId;

    /** Échéance de paiement d'une réservation reserved : passé ce délai, elle expire. */
    private Instant expiresAt;

    @ElementCollection
    @CollectionTable(name = "booking_lines", joinColumns = @JoinColumn(name = "booking_id"))
    private List<BookingLine> lines = new ArrayList<>();

    public Booking(String userId, String eventId, List<BookingLine> lines) {
        this.id = "bkg_" + UUID.randomUUID();
        this.userId = userId;
        this.eventId = eventId;
        this.bookedAt = Instant.now();
        this.status = BookingStatus.REQUESTED;
        this.lines = new ArrayList<>(lines);
        BookingPrice price = BookingPrice.of(lines.stream()
                .map(line -> new PricedQuantity(line.getQuantity(), line.getUnitPrice()))
                .toList());
        this.fees = price.fees();
        this.total = price.total();
    }
}
