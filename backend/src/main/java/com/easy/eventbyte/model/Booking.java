package com.easy.eventbyte.model;

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

    /** Montant total en centimes. */
    private long total;

    /** Frais en centimes (calculés avec le paiement, US-16). */
    private long fees;

    private String clientSecret;

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
        this.total = lines.stream().mapToLong(BookingLine::subtotal).sum();
        this.fees = 0;
    }
}
