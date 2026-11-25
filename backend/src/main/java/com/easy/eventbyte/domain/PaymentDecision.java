package com.easy.eventbyte.domain;

/**
 * Que faire d'un paiement réussi, selon l'état de la réservation.
 *
 * <p>Paiement arrivé après l'expiration (décision du groupe) : la réservation reste expirée
 * — ses places ont été remises en vente et ne sont plus garanties — et le paiement est remboursé.</p>
 */
public enum PaymentDecision {
    /** La réservation attendait ce paiement : elle est confirmée. */
    CONFIRM,
    /** Déjà confirmée : le même paiement est notifié une seconde fois, rien à faire. */
    ALREADY_CONFIRMED,
    /** La réservation n'attend plus de paiement (expirée, rejetée) : on rembourse. */
    REFUND,
    /** Le montant payé ne correspond pas au total : on ne confirme pas. */
    AMOUNT_MISMATCH;

    public static PaymentDecision onPaymentSucceeded(BookingStatus status, long amountReceived, long total) {
        if (amountReceived != total) {
            return AMOUNT_MISMATCH;
        }
        if (status.isAwaitingPayment()) {
            return CONFIRM;
        }
        return status == BookingStatus.CONFIRMED ? ALREADY_CONFIRMED : REFUND;
    }
}
