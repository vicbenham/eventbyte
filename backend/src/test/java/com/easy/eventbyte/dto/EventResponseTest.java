package com.easy.eventbyte.dto;

import com.easy.eventbyte.model.Event;
import com.easy.eventbyte.model.Ticket;
import com.easy.eventbyte.model.Venue;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EventResponseTest {

    @Test
    void l_evenement_est_expose_au_format_du_contrat() {
        Event event = event();
        addTicket(event, "Standard", 100, 2500);

        EventResponse response = EventResponse.from(event, 3, Map.of());

        assertThat(response.id()).isEqualTo("evt_1");
        assertThat(response.cover()).isEqualTo("https://cdn/cover.png");
        assertThat(response.likedCount()).isEqualTo(3);
        assertThat(response.startAt()).isEqualTo(Instant.parse("2027-03-26T17:00:00Z"));
        assertThat(response.venue().adresse()).isEqualTo("1 Rue du Concert, 75019 Paris");
        assertThat(response.venue().coordinates()).isEqualTo(new CoordinatesResponse(48.89, 2.39));
    }

    @Test
    void lowestPrice_est_le_prix_le_plus_bas_des_tickets() {
        Event event = event();
        addTicket(event, "VIP", 10, 9000);
        addTicket(event, "Early Bird", 50, 1500);
        addTicket(event, "Standard", 100, 2500);

        assertThat(EventResponse.from(event, 0, Map.of()).lowestPrice()).isEqualTo(1500);
    }

    @Test
    void l_evenement_est_complet_quand_aucun_ticket_n_a_de_stock() {
        Event event = event();
        addTicket(event, "Standard", 0, 2500);
        addTicket(event, "VIP", 0, 9000);

        assertThat(EventResponse.from(event, 0, Map.of()).isSoldOut()).isTrue();
    }

    @Test
    void l_evenement_n_est_pas_complet_s_il_reste_un_type_de_ticket() {
        Event event = event();
        addTicket(event, "Standard", 0, 2500);
        addTicket(event, "VIP", 1, 9000);

        assertThat(EventResponse.from(event, 0, Map.of()).isSoldOut()).isFalse();
    }

    @Test
    void les_places_deja_reservees_sont_deduites_du_stock() {
        Event event = event();
        addTicket(event, "Standard", 5, 2500);

        assertThat(EventResponse.from(event, 0, Map.of("tkt_Standard", 4L)).isSoldOut()).isFalse();
        assertThat(EventResponse.from(event, 0, Map.of("tkt_Standard", 5L)).isSoldOut()).isTrue();
    }

    @Test
    void sans_ticket_l_evenement_est_complet_et_son_prix_vaut_zero() {
        EventResponse response = EventResponse.from(event(), 0, Map.of());

        assertThat(response.isSoldOut()).isTrue();
        assertThat(response.lowestPrice()).isZero();
    }

    @Test
    void une_couverture_ou_une_description_absente_devient_une_chaine_vide() {
        Event event = event();
        event.setCoverUrl(null);
        event.setDescription(null);

        EventResponse response = EventResponse.from(event, 0, Map.of());

        // Le frontend exige des chaînes (schéma zod)
        assertThat(response.cover()).isEmpty();
        assertThat(response.description()).isEmpty();
    }

    private static Event event() {
        Event event = new Event("evt_1");
        event.setTitle("Concert");
        event.setDescription("Une soirée");
        event.setCoverUrl("https://cdn/cover.png");
        event.setStartAt(Instant.parse("2027-03-26T17:00:00Z"));
        event.setVenue(new Venue("Zénith", "1 Rue du Concert, 75019 Paris", 48.89, 2.39));
        return event;
    }

    private static void addTicket(Event event, String name, int quantity, long priceInCents) {
        Ticket ticket = new Ticket("tkt_" + name, event);
        ticket.setName(name);
        ticket.setQuantity(quantity);
        ticket.setPriceInCents(priceInCents);
        event.getTickets().add(ticket);
    }
}
