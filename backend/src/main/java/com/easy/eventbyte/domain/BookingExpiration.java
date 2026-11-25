package com.easy.eventbyte.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * Délai de paiement d'une réservation : passée son échéance, une réservation non payée
 * ({@link BookingStatus#AWAITING_PAYMENT_STATUSES}) expire.
 */
public final class BookingExpiration {

    private BookingExpiration() {
    }

    public static Instant deadline(Instant bookedAt, Duration delayToPay) {
        return bookedAt.plus(delayToPay);
    }
}
