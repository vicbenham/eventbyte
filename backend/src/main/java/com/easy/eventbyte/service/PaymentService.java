package com.easy.eventbyte.service;

import com.easy.eventbyte.client.StripeClient;
import com.easy.eventbyte.client.StripeWebhookVerifier;
import com.easy.eventbyte.dto.StripeEvent;
import com.easy.eventbyte.dto.StripeEventPaymentIntent;
import com.easy.eventbyte.model.Booking;
import com.easy.eventbyte.model.BookingStatus;
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
 * <p>Paiement arrivé après l'expiration de la réservation (US-17, décision du groupe) :
 * à l'expiration, le PaymentIntent est annulé chez Stripe, ce qui empêche normalement de payer.
 * Si un paiement aboutit quand même (course entre l'expiration et le paiement), la réservation
 * reste expired — ses places ont déjà été remises en vente — et le paiement est remboursé intégralement.</p>
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
        if (intent.amountReceived() != booking.getTotal()) {
            log.error("Paiement {} de {} centimes pour la réservation {} de {} centimes : non confirmée",
                    intent.id(), intent.amountReceived(), booking.getId(), booking.getTotal());
            return;
        }

        int confirmed = bookingRepository.confirm(booking.getId(), BookingStatus.CONFIRMED,
                BookingStatus.AWAITING_PAYMENT_STATUSES);
        if (confirmed == 1) {
            log.info("Réservation {} confirmée", booking.getId());
        } else if (booking.getStatus() == BookingStatus.CONFIRMED) {
            log.info("Réservation {} déjà confirmée (webhook rejoué)", booking.getId());
        } else {
            // Expirée (ou rejetée) : les places ne sont plus garanties, on rembourse
            log.warn("Paiement {} reçu pour la réservation {} {} : remboursement", intent.id(), booking.getId(), booking.getStatus());
            stripeClient.refund(intent.id());
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
