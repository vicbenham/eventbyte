package com.easy.eventbyte.exposition;

import com.easy.eventbyte.TestcontainersConfiguration;
import com.easy.eventbyte.model.Event;
import com.easy.eventbyte.model.Ticket;
import com.easy.eventbyte.model.Venue;
import com.easy.eventbyte.repository.EventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-4 : GET /events/discover?lat&lng.
 * Chaque test se place à un endroit différent (au milieu de l'océan) pour ne pas voir les événements des autres tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EventControllerIntegrationTest {

    static final Instant IN_ONE_WEEK = Instant.now().plus(Duration.ofDays(7));

    @Autowired
    MockMvc mvc;
    @Autowired
    EventRepository eventRepository;

    @Test
    void renvoie_les_evenements_proches_au_format_du_contrat_sans_authentification() throws Exception {
        double lat = -40, lng = -140;
        Event event = event("Concert", lat + 0.01, lng, IN_ONE_WEEK);
        addTicket(event, "VIP", 10, 9000);
        addTicket(event, "Standard", 100, 2500);
        eventRepository.save(event);

        mvc.perform(get("/events/discover").param("lat", "" + lat).param("lng", "" + lng))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(event.getId()))
                .andExpect(jsonPath("$[0].title").value("Concert"))
                .andExpect(jsonPath("$[0].cover").value("https://cdn/cover.png"))
                .andExpect(jsonPath("$[0].description").value("Une soirée"))
                .andExpect(jsonPath("$[0].likedCount").value(0))
                .andExpect(jsonPath("$[0].isSoldOut").value(false))
                .andExpect(jsonPath("$[0].lowestPrice").value(2500))
                .andExpect(jsonPath("$[0].startAt").value(IN_ONE_WEEK.toString()))
                .andExpect(jsonPath("$[0].venue.name").value("Salle"))
                .andExpect(jsonPath("$[0].venue.adresse").value("1 rue de la Paix"))
                .andExpect(jsonPath("$[0].venue.coordinates.latitude").value(lat + 0.01))
                .andExpect(jsonPath("$[0].venue.coordinates.longitude").value(lng));
    }

    @Test
    void les_evenements_sont_tries_du_plus_proche_au_plus_loin() throws Exception {
        double lat = -41, lng = -141;
        Event loin = eventRepository.save(event("Loin", lat + 0.5, lng, IN_ONE_WEEK));
        Event proche = eventRepository.save(event("Proche", lat + 0.01, lng, IN_ONE_WEEK));
        Event moyen = eventRepository.save(event("Moyen", lat, lng + 0.2, IN_ONE_WEEK));

        mvc.perform(get("/events/discover").param("lat", "" + lat).param("lng", "" + lng))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(proche.getId(), moyen.getId(), loin.getId())));
    }

    @Test
    void les_evenements_passes_trop_loin_ou_sans_date_sont_exclus() throws Exception {
        double lat = -42, lng = -142;
        Event aVenir = eventRepository.save(event("À venir", lat, lng, IN_ONE_WEEK));
        eventRepository.save(event("Passé", lat, lng, Instant.now().minus(Duration.ofDays(1))));
        eventRepository.save(event("Sans date", lat, lng, null));
        eventRepository.save(event("À 300 km", lat + 2.7, lng, IN_ONE_WEEK));

        mvc.perform(get("/events/discover").param("lat", "" + lat).param("lng", "" + lng))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(aVenir.getId())));
    }

    @Test
    void sans_evenement_autour_la_liste_est_vide() throws Exception {
        mvc.perform(get("/events/discover").param("lat", "-43").param("lng", "-143"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void sans_position_la_requete_est_refusee() throws Exception {
        mvc.perform(get("/events/discover").param("lat", "48.85"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/events/discover").param("lat", "abc").param("lng", "2.35"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void le_detail_d_un_evenement_est_renvoye_au_format_du_contrat_sans_authentification() throws Exception {
        Event event = event("Concert", 48.85, 2.35, IN_ONE_WEEK);
        addTicket(event, "Standard", 0, 2500);
        addTicket(event, "VIP", 0, 9000);
        eventRepository.save(event);

        mvc.perform(get("/events/{id}", event.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(event.getId()))
                .andExpect(jsonPath("$.title").value("Concert"))
                .andExpect(jsonPath("$.cover").value("https://cdn/cover.png"))
                .andExpect(jsonPath("$.description").value("Une soirée"))
                .andExpect(jsonPath("$.likedCount").value(0))
                .andExpect(jsonPath("$.isSoldOut").value(true))
                .andExpect(jsonPath("$.lowestPrice").value(2500))
                .andExpect(jsonPath("$.startAt").value(IN_ONE_WEEK.toString()))
                .andExpect(jsonPath("$.venue.adresse").value("1 rue de la Paix"))
                .andExpect(jsonPath("$.venue.coordinates.latitude").value(48.85));
    }

    @Test
    void le_detail_d_un_evenement_passe_reste_consultable() throws Exception {
        Event past = eventRepository.save(event("Passé", 48.85, 2.35, Instant.now().minus(Duration.ofDays(30))));

        mvc.perform(get("/events/{id}", past.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Passé"));
    }

    @Test
    void un_evenement_inexistant_repond_404() throws Exception {
        mvc.perform(get("/events/{id}", "evt_inconnu"))
                .andExpect(status().isNotFound());
    }

    @Test
    void un_evenement_sans_date_repond_404() throws Exception {
        Event withoutDate = eventRepository.save(event("Sans date", 48.85, 2.35, null));

        mvc.perform(get("/events/{id}", withoutDate.getId()))
                .andExpect(status().isNotFound());
    }

    private static Event event(String title, double latitude, double longitude, Instant startAt) {
        Event event = new Event("evt_" + UUID.randomUUID());
        event.setTitle(title);
        event.setDescription("Une soirée");
        event.setCoverUrl("https://cdn/cover.png");
        event.setStartAt(startAt);
        event.setVenue(new Venue("Salle", "1 rue de la Paix", latitude, longitude));
        event.setUpdatedAt(Instant.now());
        return event;
    }

    private static void addTicket(Event event, String name, int quantity, long priceInCents) {
        Ticket ticket = new Ticket(event.getId() + "_" + name, event);
        ticket.setName(name);
        ticket.setQuantity(quantity);
        ticket.setPriceInCents(priceInCents);
        event.getTickets().add(ticket);
    }
}
