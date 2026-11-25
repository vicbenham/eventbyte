package com.easy.eventbyte.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static com.easy.eventbyte.domain.PaymentDecision.ALREADY_CONFIRMED;
import static com.easy.eventbyte.domain.PaymentDecision.AMOUNT_MISMATCH;
import static com.easy.eventbyte.domain.PaymentDecision.CONFIRM;
import static com.easy.eventbyte.domain.PaymentDecision.REFUND;
import static org.assertj.core.api.Assertions.assertThat;

/** Que faire d'un paiement réussi, y compris arrivé après l'expiration ou notifié deux fois. */
class PaymentDecisionTest {

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"RESERVED", "AWAITING_PAYMENT"})
    void une_reservation_qui_attend_son_paiement_est_confirmee(BookingStatus status) {
        assertThat(PaymentDecision.onPaymentSucceeded(status, 5090, 5090)).isEqualTo(CONFIRM);
    }

    @Test
    void un_paiement_notifie_deux_fois_ne_fait_rien_la_seconde_fois() {
        assertThat(PaymentDecision.onPaymentSucceeded(BookingStatus.CONFIRMED, 5090, 5090)).isEqualTo(ALREADY_CONFIRMED);
    }

    @Test
    void un_paiement_arrive_apres_l_expiration_est_rembourse() {
        assertThat(PaymentDecision.onPaymentSucceeded(BookingStatus.EXPIRED, 5090, 5090)).isEqualTo(REFUND);
    }

    @Test
    void un_paiement_pour_une_reservation_rejetee_est_rembourse() {
        assertThat(PaymentDecision.onPaymentSucceeded(BookingStatus.REJECTED, 5090, 5090)).isEqualTo(REFUND);
    }

    @ParameterizedTest
    @EnumSource(BookingStatus.class)
    void un_montant_different_du_total_n_est_jamais_confirme(BookingStatus status) {
        assertThat(PaymentDecision.onPaymentSucceeded(status, 5089, 5090)).isEqualTo(AMOUNT_MISMATCH);
    }
}
