package com.easy.eventbyte.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Cas limites du stock. */
class TicketStockTest {

    @Test
    void les_places_reservees_sont_deduites_du_stock() {
        assertThat(TicketStock.available(100, 30)).isEqualTo(70);
    }

    @Test
    void le_dernier_ticket_est_encore_disponible() {
        assertThat(TicketStock.available(5, 4)).isEqualTo(1);
    }

    @Test
    void tout_reserve_il_ne_reste_aucune_place() {
        assertThat(TicketStock.available(5, 5)).isZero();
    }

    @Test
    void un_stock_reduit_sous_les_places_vendues_ne_devient_pas_negatif() {
        // l'organisateur baisse le stock dans Strapi alors que des places sont déjà réservées
        assertThat(TicketStock.available(3, 5)).isZero();
    }

    @Test
    void un_evenement_est_complet_quand_plus_aucun_type_de_ticket_n_a_de_place() {
        assertThat(TicketStock.isSoldOut(List.of(0, 0))).isTrue();
        assertThat(TicketStock.isSoldOut(List.of(0, 1))).isFalse();
    }

    @Test
    void un_evenement_sans_ticket_est_complet() {
        assertThat(TicketStock.isSoldOut(List.of())).isTrue();
    }

    @Test
    void seuls_les_statuts_reserves_en_attente_ou_confirmes_immobilisent_des_places() {
        assertThat(BookingStatus.HOLDING_STOCK).containsExactlyInAnyOrder(
                BookingStatus.RESERVED, BookingStatus.AWAITING_PAYMENT, BookingStatus.CONFIRMED);
    }
}
