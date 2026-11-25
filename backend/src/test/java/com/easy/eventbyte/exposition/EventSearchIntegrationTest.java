package com.easy.eventbyte.exposition;

import com.easy.eventbyte.TestcontainersConfiguration;
import com.easy.eventbyte.client.StrapiClient;
import com.easy.eventbyte.dto.StrapiEventMessage;
import com.easy.eventbyte.dto.TicketMessage;
import com.easy.eventbyte.dto.VenueMessage;
import com.easy.eventbyte.service.EventIngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-7 : GET /events/search?lat&lng&radius&query, de l'ingestion d'un message Strapi à la recherche Meilisearch.
 * Chaque test utilise un mot unique et un endroit différent pour ne pas voir les événements des autres tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EventSearchIntegrationTest {

    static final Instant IN_ONE_WEEK = Instant.now().plus(Duration.ofDays(7));

    @Autowired
    MockMvc mvc;
    @Autowired
    EventIngestionService ingestion;

    /** Date renvoyée par Strapi pour chaque événement (le message RabbitMQ n'en contient pas). */
    @MockitoBean
    StrapiClient strapiClient;
    final Map<String, Instant> startDates = new ConcurrentHashMap<>();

    @BeforeEach
    void strapiReturnsKnownDates() {
        when(strapiClient.findStartAt(anyString()))
                .thenAnswer(call -> Optional.ofNullable(startDates.get(call.<String>getArgument(0))));
    }

    @Test
    void trouve_un_evenement_par_mot_cle_du_titre_de_la_description_ou_du_lieu() throws Exception {
        String word = uniqueWord();
        String inTitle = ingest("Concert " + word, "Soirée", "Salle", "1 rue de la Paix", -30, -130, IN_ONE_WEEK);
        String inDescription = ingest("Festival", "Avec " + word, "Salle", "1 rue de la Paix", -30, -130.01, IN_ONE_WEEK);
        String inVenue = ingest("Expo", "Peinture", "Galerie " + word, "2 rue Haute", -30, -130.02, IN_ONE_WEEK);
        ingest("Sans rapport", "Rien à voir", "Salle", "3 rue Basse", -30, -130.03, IN_ONE_WEEK);

        await().untilAsserted(() -> search(word, -30, -130, 50)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(inTitle, inDescription, inVenue))));
    }

    @Test
    void le_resultat_est_au_format_du_contrat_et_ne_demande_pas_d_authentification() throws Exception {
        String word = uniqueWord();
        String id = ingest("Concert " + word, "Soirée", "Zénith", "1 rue du Concert", -31, -131, IN_ONE_WEEK);

        await().untilAsserted(() -> search(word, -31, -131, 10)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].lowestPrice").value(2550))
                .andExpect(jsonPath("$[0].isSoldOut").value(false))
                .andExpect(jsonPath("$[0].startAt").value(IN_ONE_WEEK.toString()))
                .andExpect(jsonPath("$[0].venue.adresse").value("1 rue du Concert"))
                .andExpect(jsonPath("$[0].venue.coordinates.latitude").value(-31)));
    }

    @Test
    void seuls_les_evenements_dans_le_rayon_sont_renvoyes_du_plus_proche_au_plus_loin() throws Exception {
        String word = uniqueWord();
        String a20km = ingest("Concert " + word, "", "Salle", "A", -32.18, -132, IN_ONE_WEEK);
        String a5km = ingest("Concert " + word, "", "Salle", "B", -32.045, -132, IN_ONE_WEEK);
        ingest("Concert " + word, "", "Salle", "C", -32.9, -132, IN_ONE_WEEK); // ~100 km

        await().untilAsserted(() -> search(word, -32, -132, 30)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(a5km, a20km))));
    }

    @Test
    void sans_mot_cle_tous_les_evenements_du_rayon_sont_renvoyes() throws Exception {
        String premier = ingest("Concert", "", "Salle", "A", -33, -133, IN_ONE_WEEK);
        String second = ingest("Théâtre", "", "Salle", "B", -33.01, -133, IN_ONE_WEEK);

        await().untilAsserted(() -> search("", -33, -133, 5)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(premier, second))));
    }

    @Test
    void les_evenements_passes_sont_exclus() throws Exception {
        String word = uniqueWord();
        String aVenir = ingest("Concert " + word, "", "Salle", "A", -34, -134, IN_ONE_WEEK);
        ingest("Concert " + word, "", "Salle", "B", -34, -134, Instant.now().minus(Duration.ofDays(1)));

        await().untilAsserted(() -> search(word, -34, -134, 10)
                .andExpect(jsonPath("$[*].id", contains(aVenir))));
    }

    @Test
    void l_index_suit_les_modifications_et_les_suppressions_de_strapi() throws Exception {
        String avant = uniqueWord();
        String apres = uniqueWord();
        String id = ingest("Concert " + avant, "", "Salle", "A", -35, -135, IN_ONE_WEEK);
        await().untilAsserted(() -> search(avant, -35, -135, 10).andExpect(jsonPath("$[*].id", contains(id))));

        ingestion.save(message(id, "Concert " + apres, "", "Salle", "A", -35, -135));
        await().untilAsserted(() -> {
            search(apres, -35, -135, 10).andExpect(jsonPath("$[*].id", contains(id)));
            search(avant, -35, -135, 10).andExpect(jsonPath("$", hasSize(0)));
        });

        ingestion.delete(id);
        await().untilAsserted(() -> search(apres, -35, -135, 10).andExpect(jsonPath("$", hasSize(0))));
    }

    @Test
    void sans_position_ou_rayon_la_requete_est_refusee() throws Exception {
        mvc.perform(get("/events/search").param("lat", "48.85").param("lng", "2.35").param("query", "concert"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/events/search").param("radius", "10").param("query", "concert"))
                .andExpect(status().isBadRequest());
    }

    private ResultActions search(String query, double lat, double lng, double radiusKm) throws Exception {
        return mvc.perform(get("/events/search")
                .param("lat", "" + lat)
                .param("lng", "" + lng)
                .param("radius", "" + radiusKm)
                .param("query", query));
    }

    /** Ingère un événement comme s'il venait d'un message Strapi event.created. */
    private String ingest(String title, String description, String venueName, String address,
                          double lat, double lng, Instant startAt) {
        String id = "evt_" + UUID.randomUUID();
        startDates.put(id, startAt);
        ingestion.save(message(id, title, description, venueName, address, lat, lng));
        return id;
    }

    private static StrapiEventMessage message(String id, String title, String description, String venueName,
                                              String address, double lat, double lng) {
        return new StrapiEventMessage(id, title, description, "https://cdn/c.png",
                new VenueMessage(venueName, address, lat, lng),
                List.of(new TicketMessage(id + "_tkt", "Standard", 100, new BigDecimal("25.50"))));
    }

    private static String uniqueWord() {
        return "mot" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
