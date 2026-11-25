package com.easy.eventbyte.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Cas limites de l'ingestion Strapi, dont les messages reçus en double. */
class TicketSyncTest {

    @Test
    void a_la_creation_tous_les_tickets_sont_ajoutes() {
        TicketSync sync = TicketSync.between(Set.of(), List.of("standard", "vip"));

        assertThat(sync.added()).containsExactly("standard", "vip");
        assertThat(sync.removed()).isEmpty();
        assertThat(sync.kept()).isEmpty();
    }

    @Test
    void un_message_recu_deux_fois_ne_cree_ni_ne_supprime_rien() {
        // Après le premier message, l'événement a les tickets standard et vip ; le même message arrive à nouveau
        TicketSync sync = TicketSync.between(Set.of("standard", "vip"), List.of("standard", "vip"));

        assertThat(sync.added()).isEmpty();
        assertThat(sync.removed()).isEmpty();
        assertThat(sync.kept()).containsExactly("standard", "vip");
    }

    @Test
    void une_modification_ajoute_supprime_et_met_a_jour_les_bons_tickets() {
        TicketSync sync = TicketSync.between(Set.of("standard", "early-bird"), List.of("standard", "vip"));

        assertThat(sync.added()).containsExactly("vip");
        assertThat(sync.removed()).containsExactly("early-bird");
        assertThat(sync.kept()).containsExactly("standard");
    }

    @Test
    void un_ticket_en_double_dans_le_message_n_est_ajoute_qu_une_fois() {
        assertThat(TicketSync.between(Set.of(), List.of("standard", "standard")).added()).containsExactly("standard");
    }

    @Test
    void un_message_pour_un_evenement_supprime_est_ignore() {
        // event.created rejoué, ou arrivé après event.deleted (ordre non garanti par l'outbox de Strapi)
        assertThat(TicketSync.shouldApply(true)).isFalse();
        assertThat(TicketSync.shouldApply(false)).isTrue();
    }
}
