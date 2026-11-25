package com.easy.eventbyte.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Cas limites de l'expiration. */
class BookingExpirationTest {

    @Test
    void l_echeance_est_la_date_de_reservation_plus_le_delai_de_paiement() {
        Instant bookedAt = Instant.parse("2027-01-01T12:00:00Z");

        assertThat(BookingExpiration.deadline(bookedAt, Duration.ofMinutes(10)))
                .isEqualTo(Instant.parse("2027-01-01T12:10:00Z"));
    }

    @Test
    void seules_les_reservations_pas_encore_payees_peuvent_expirer() {
        assertThat(BookingStatus.AWAITING_PAYMENT_STATUSES)
                .containsExactlyInAnyOrder(BookingStatus.RESERVED, BookingStatus.AWAITING_PAYMENT);
        assertThat(BookingStatus.CONFIRMED.isAwaitingPayment()).isFalse();
        assertThat(BookingStatus.EXPIRED.isAwaitingPayment()).isFalse();
        assertThat(BookingStatus.REJECTED.isAwaitingPayment()).isFalse();
    }

    @Test
    void les_statuts_sont_exposes_avec_les_valeurs_du_contrat() {
        assertThat(BookingStatus.AWAITING_PAYMENT.apiValue()).isEqualTo("awaiting-payment");
        assertThat(BookingStatus.EXPIRED.apiValue()).isEqualTo("expired");
    }
}
