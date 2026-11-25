package com.easy.eventbyte.client;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class StripeWebhookVerifierTest {

    static final String PAYLOAD = "{\"id\":\"evt_1\",\"type\":\"payment_intent.succeeded\"}";

    final StripeWebhookVerifier verifier = new StripeWebhookVerifier("whsec_test");

    @Test
    void une_signature_stripe_valide_est_acceptee() {
        assertThat(verifier.isValid(PAYLOAD, header(verifier, Instant.now(), PAYLOAD))).isTrue();
    }

    @Test
    void une_signature_parmi_plusieurs_suffit() {
        long t = Instant.now().getEpochSecond();
        String header = "t=" + t + ",v1=" + "0".repeat(64) + ",v1=" + verifier.sign(t + "." + PAYLOAD);

        assertThat(verifier.isValid(PAYLOAD, header)).isTrue();
    }

    @Test
    void un_corps_modifie_est_refuse() {
        String header = header(verifier, Instant.now(), PAYLOAD);

        assertThat(verifier.isValid(PAYLOAD.replace("evt_1", "evt_2"), header)).isFalse();
    }

    @Test
    void une_signature_faite_avec_un_autre_secret_est_refusee() {
        StripeWebhookVerifier other = new StripeWebhookVerifier("whsec_autre");

        assertThat(verifier.isValid(PAYLOAD, header(other, Instant.now(), PAYLOAD))).isFalse();
    }

    @Test
    void une_signature_trop_ancienne_est_refusee() {
        Instant tenMinutesAgo = Instant.now().minusSeconds(600);

        assertThat(verifier.isValid(PAYLOAD, header(verifier, tenMinutesAgo, PAYLOAD))).isFalse();
    }

    @Test
    void un_header_absent_ou_mal_forme_est_refuse() {
        assertThat(verifier.isValid(PAYLOAD, null)).isFalse();
        assertThat(verifier.isValid(PAYLOAD, "n'importe quoi")).isFalse();
        assertThat(verifier.isValid(PAYLOAD, "t=abc,v1=def")).isFalse();
        assertThat(verifier.isValid(PAYLOAD, "v1=" + verifier.sign("x"))).isFalse();
    }

    @Test
    void sans_secret_configure_tout_est_refuse() {
        StripeWebhookVerifier unconfigured = new StripeWebhookVerifier("");

        assertThat(unconfigured.isValid(PAYLOAD, header(verifier, Instant.now(), PAYLOAD))).isFalse();
    }

    private static String header(StripeWebhookVerifier signer, Instant at, String payload) {
        long t = at.getEpochSecond();
        return "t=" + t + ",v1=" + signer.sign(t + "." + payload);
    }
}
