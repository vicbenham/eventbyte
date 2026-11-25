package com.easy.eventbyte.service;

import com.easy.eventbyte.client.StripeClient;
import com.easy.eventbyte.client.StripeWebhookVerifier;
import com.easy.eventbyte.dto.StripeEvent;
import com.easy.eventbyte.dto.StripeEventPaymentIntent;
import com.easy.eventbyte.model.Booking;
import com.easy.eventbyte.domain.BookingStatus;
import com.easy.eventbyte.domain.PaymentDecision;
import com.easy.eventbyte.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Résultat des paiements, reçu de Stripe par webhook.
 *
 * <p>La règle (confirmer, ignorer un doublon, rembourser un paiement tardif) est dans {@link PaymentDecision}.
 * À l'expiration, le PaymentIntent est aussi annulé chez Stripe (BookingExpirationService), ce qui empêche
 * normalement de payer une réservation expirée.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    static final String PAYMENT_SUCCEEDED = "payment_intent.succeeded";

    private final StripeWebhookVerifier webhookVerifier;
    private final StripeClient stripeClient;
    private final BookingRepository bookingRepository;
    private final JsonMapper jsonMapper;
    private final BookingEventPublisher bookingEventPublisher;

    /** Traite un webhook Stripe. Idempotent : Stripe peut livrer plusieurs fois le même événement. */
    @Transactional
    public void handleWebhook(String payload, String signature) {
        if (!webhookVerifier.isValid(payload, signature)) {
            throw new InvalidWebhookException("Signature Stripe invalide");
        }
        StripeEvent event = parse(payload);
        if (PAYMENT_SUCCEEDED.equals(event.type())) {
            onPaymentSucceeded(event.data().object());
        } else {
            log.debug("Webhook Stripe {} ignoré", event.type());
        }
    }

    private void onPaymentSucceeded(StripeEventPaymentIntent intent) {
        Booking booking = bookingRepository.findByPaymentIntentId(intent.id()).orElse(null);
        if (booking == null) {
            log.warn("Paiement {} sans réservation associée, ignoré", intent.id());
            return;
        }
        PaymentDecision decision = PaymentDecision.onPaymentSucceeded(
                booking.getStatus(), intent.amountReceived(), booking.getTotal());

        if (decision == PaymentDecision.CONFIRM) {
            // Conditionnel : si la réservation vient d'expirer, rien ne change et on rembourse
            int confirmed = bookingRepository.confirm(booking.getId(), BookingStatus.CONFIRMED,
                    BookingStatus.AWAITING_PAYMENT_STATUSES);
            if (confirmed == 1) {
                log.info("Réservation {} confirmée", booking.getId());
                bookingEventPublisher.publishAfterCommit(booking.getUserId(), booking.getId());
                return;
            }
            decision = PaymentDecision.REFUND;
        }

        switch (decision) {
            case ALREADY_CONFIRMED -> log.info("Réservation {} déjà confirmée (webhook rejoué)", booking.getId());
            case AMOUNT_MISMATCH -> log.error("Paiement {} de {} centimes pour la réservation {} de {} centimes : non confirmée",
                    intent.id(), intent.amountReceived(), booking.getId(), booking.getTotal());
            case REFUND -> {
                log.warn("Paiement {} reçu pour la réservation {} qui n'attend plus de paiement : remboursement",
                        intent.id(), booking.getId());
                stripeClient.refund(intent.id());
            }
            case CONFIRM -> throw new IllegalStateException("déjà traité");
        }
    }

    private StripeEvent parse(String payload) {
        try {
            StripeEvent event = jsonMapper.readValue(payload, StripeEvent.class);
            if (event.type() == null || event.data() == null || event.data().object() == null) {
                throw new InvalidWebhookException("Événement Stripe incomplet");
            }
            return event;
        } catch (JacksonException e) {
            throw new InvalidWebhookException("Événement Stripe illisible");
        }
    }
}
