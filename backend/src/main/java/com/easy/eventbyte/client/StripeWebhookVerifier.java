package com.easy.eventbyte.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Vérifie qu'un webhook vient bien de Stripe (https://docs.stripe.com/webhooks#verify-manually).
 * Header Stripe-Signature : "t=timestamp,v1=signature[,v1=...]" ; signature = HMAC-SHA256("{t}.{corps}", secret whsec_...).
 * Les signatures de plus de 5 minutes sont refusées pour empêcher le rejeu d'un ancien webhook.
 */
@Slf4j
@Component
public class StripeWebhookVerifier {

    static final Duration TOLERANCE = Duration.ofMinutes(5);

    private final String secret;

    public StripeWebhookVerifier(@Value("${stripe.webhook-secret:}") String secret) {
        this.secret = secret;
        if (secret.isBlank()) {
            log.warn("STRIPE_WEBHOOK_SECRET non défini : les webhooks Stripe seront refusés");
        }
    }

    public boolean isValid(String payload, String signatureHeader) {
        if (secret.isBlank() || signatureHeader == null) {
            return false;
        }
        String timestamp = null;
        List<String> signatures = new ArrayList<>();
        for (String part : signatureHeader.split(",")) {
            String[] keyValue = part.trim().split("=", 2);
            if (keyValue.length == 2 && keyValue[0].equals("t")) {
                timestamp = keyValue[1];
            } else if (keyValue.length == 2 && keyValue[0].equals("v1")) {
                signatures.add(keyValue[1]);
            }
        }
        if (timestamp == null || !isRecent(timestamp)) {
            return false;
        }
        byte[] expected = sign(timestamp + "." + payload).getBytes(StandardCharsets.UTF_8);
        // Comparaison en temps constant
        return signatures.stream().anyMatch(s -> MessageDigest.isEqual(expected, s.getBytes(StandardCharsets.UTF_8)));
    }

    /** Signature au format Stripe (utilisée aussi par les tests pour simuler Stripe). */
    public String sign(String signedPayload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean isRecent(String timestamp) {
        try {
            Instant signedAt = Instant.ofEpochSecond(Long.parseLong(timestamp));
            return Duration.between(signedAt, Instant.now()).abs().compareTo(TOLERANCE) <= 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
