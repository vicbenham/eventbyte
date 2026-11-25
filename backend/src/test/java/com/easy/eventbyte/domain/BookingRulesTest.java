package com.easy.eventbyte.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingRulesTest {

    static final Instant NOW = Instant.parse("2027-01-01T12:00:00Z");
    static final Instant TOMORROW = NOW.plusSeconds(86_400);

    @Test
    void un_meme_ticket_sur_plusieurs_lignes_ne_fait_qu_une_ligne() {
        Map<String, Integer> merged = BookingRules.mergeByTicket(List.of(
                new RequestedTicket("standard", 2),
                new RequestedTicket("vip", 1),
                new RequestedTicket("standard", 3)));

        assertThat(merged).containsExactly(Map.entry("standard", 5), Map.entry("vip", 1));
    }

    @Test
    void une_reservation_valide_renvoie_son_evenement() {
        String eventId = BookingRules.checkBookable(2, List.of(
                new TicketOffer("standard", "evt_1", TOMORROW),
                new TicketOffer("vip", "evt_1", TOMORROW)), NOW);

        assertThat(eventId).isEqualTo("evt_1");
    }

    @Test
    void un_ticket_inconnu_est_refuse() {
        assertThatThrownBy(() -> BookingRules.checkBookable(2, List.of(
                new TicketOffer("standard", "evt_1", TOMORROW)), NOW))
                .isInstanceOf(InvalidBookingException.class)
                .hasMessageContaining("inconnu");
    }

    @Test
    void des_tickets_de_plusieurs_evenements_sont_refuses() {
        assertThatThrownBy(() -> BookingRules.checkBookable(2, List.of(
                new TicketOffer("standard", "evt_1", TOMORROW),
                new TicketOffer("vip", "evt_2", TOMORROW)), NOW))
                .isInstanceOf(InvalidBookingException.class)
                .hasMessageContaining("seul événement");
    }

    @Test
    void un_evenement_passe_ne_se_reserve_plus() {
        assertThatThrownBy(() -> BookingRules.checkBookable(1, List.of(
                new TicketOffer("standard", "evt_1", NOW.minusSeconds(1))), NOW))
                .isInstanceOf(InvalidBookingException.class)
                .hasMessageContaining("passé");
    }

    @Test
    void un_evenement_dont_la_date_est_inconnue_reste_reservable() {
        assertThat(BookingRules.checkBookable(1, List.of(new TicketOffer("standard", "evt_1", null)), NOW))
                .isEqualTo("evt_1");
    }
}
