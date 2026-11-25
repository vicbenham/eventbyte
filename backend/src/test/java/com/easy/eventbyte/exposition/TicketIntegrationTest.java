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

/** US-11 : GET /events/:eventId/tickets. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TicketIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    EventRepository eventRepository;

    @Test
    void les_tickets_sont_renvoyes_au_format_du_contrat_sans_authentification() throws Exception {
        Event event = event();
        addTicket(event, "Early Bird", 120, 2500);
        eventRepository.save(event);

        mvc.perform(get("/events/{id}/tickets", event.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(event.getId() + "_Early Bird"))
                .andExpect(jsonPath("$[0].eventId").value(event.getId()))
                .andExpect(jsonPath("$[0].name").value("Early Bird"))
                .andExpect(jsonPath("$[0].price").value(2500))
                .andExpect(jsonPath("$[0].quantity").value(120));
    }

    @Test
    void les_tickets_sont_tries_du_moins_cher_au_plus_cher() throws Exception {
        Event event = event();
        addTicket(event, "VIP", 10, 9000);
        addTicket(event, "Early Bird", 50, 1500);
        addTicket(event, "Standard", 100, 2500);
        eventRepository.save(event);

        mvc.perform(get("/events/{id}/tickets", event.getId()))
                .andExpect(jsonPath("$[*].name", contains("Early Bird", "Standard", "VIP")));
    }

    @Test
    void un_ticket_epuise_est_renvoye_avec_une_quantite_nulle() throws Exception {
        Event event = event();
        addTicket(event, "Standard", 0, 2500);
        eventRepository.save(event);

        mvc.perform(get("/events/{id}/tickets", event.getId()))
                .andExpect(jsonPath("$[0].quantity").value(0));
    }

    @Test
    void un_evenement_sans_ticket_renvoie_une_liste_vide() throws Exception {
        Event event = eventRepository.save(event());

        mvc.perform(get("/events/{id}/tickets", event.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void les_tickets_d_un_evenement_inexistant_repondent_404() throws Exception {
        mvc.perform(get("/events/{id}/tickets", "evt_inconnu"))
                .andExpect(status().isNotFound());
    }

    private static Event event() {
        Event event = new Event("evt_" + UUID.randomUUID());
        event.setTitle("Concert");
        event.setVenue(new Venue("Salle", "1 rue de la Paix", 48.85, 2.35));
        event.setStartAt(Instant.now().plus(Duration.ofDays(7)));
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
