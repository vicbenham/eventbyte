package com.easy.eventbyte.exposition;

import com.easy.eventbyte.TestcontainersConfiguration;
import com.easy.eventbyte.model.Event;
import com.easy.eventbyte.model.EventLikeId;
import com.easy.eventbyte.model.Venue;
import com.easy.eventbyte.repository.EventLikeRepository;
import com.easy.eventbyte.repository.EventRepository;
import com.easy.eventbyte.service.LikeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US-8, US-9 et US-10 : liker, retirer son like, voir ses événements likés. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LikeIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    EventRepository eventRepository;
    @Autowired
    EventLikeRepository eventLikeRepository;
    @Autowired
    LikeService likeService;

    @Test
    void liker_un_evenement_repond_200_sans_corps_et_met_a_jour_likedCount() throws Exception {
        String eventId = createEvent();

        mvc.perform(post("/events/{id}/like", eventId).with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        mvc.perform(get("/events/{id}", eventId))
                .andExpect(jsonPath("$.likedCount").value(1));
    }

    @Test
    void liker_deux_fois_ne_compte_qu_un_like() throws Exception {
        String eventId = createEvent();

        mvc.perform(post("/events/{id}/like", eventId).with(user("alice"))).andExpect(status().isOk());
        mvc.perform(post("/events/{id}/like", eventId).with(user("alice"))).andExpect(status().isOk());

        mvc.perform(get("/events/{id}", eventId))
                .andExpect(jsonPath("$.likedCount").value(1));
    }

    @Test
    void chaque_utilisateur_compte_pour_un_like() throws Exception {
        String eventId = createEvent();

        mvc.perform(post("/events/{id}/like", eventId).with(user("alice"))).andExpect(status().isOk());
        mvc.perform(post("/events/{id}/like", eventId).with(user("bob"))).andExpect(status().isOk());

        mvc.perform(get("/events/{id}", eventId))
                .andExpect(jsonPath("$.likedCount").value(2));
    }

    @Test
    void likedCount_est_a_jour_dans_les_listes_d_evenements() throws Exception {
        String liked = createEvent();
        String notLiked = createEvent();
        mvc.perform(post("/events/{id}/like", liked).with(user("alice"))).andExpect(status().isOk());

        mvc.perform(get("/events").param("ids", liked + "," + notLiked))
                .andExpect(jsonPath("$[0].likedCount").value(1))
                .andExpect(jsonPath("$[1].likedCount").value(0));
    }

    @Test
    void des_likes_simultanes_du_meme_utilisateur_ne_comptent_qu_une_fois() {
        String eventId = createEvent();

        try (var executor = Executors.newFixedThreadPool(8)) {
            CompletableFuture.allOf(IntStream.range(0, 20)
                    .mapToObj(i -> CompletableFuture.runAsync(() -> likeService.like("alice", eventId), executor))
                    .toArray(CompletableFuture[]::new)).join();
        }

        assertThat(eventLikeRepository.countByEventId(eventId)).isEqualTo(1);
    }

    @Test
    void liker_un_evenement_inexistant_repond_404() throws Exception {
        mvc.perform(post("/events/{id}/like", "evt_inconnu").with(user("alice")))
                .andExpect(status().isNotFound());
    }

    @Test
    void liker_sans_etre_connecte_repond_401() throws Exception {
        String eventId = createEvent();

        mvc.perform(post("/events/{id}/like", eventId))
                .andExpect(status().isUnauthorized());

        assertThat(eventLikeRepository.countByEventId(eventId)).isZero();
    }

    // US-9 : DELETE /events/:eventId/like

    @Test
    void retirer_son_like_repond_200_sans_corps_et_met_a_jour_likedCount() throws Exception {
        String eventId = createEvent();
        mvc.perform(post("/events/{id}/like", eventId).with(user("alice"))).andExpect(status().isOk());

        mvc.perform(delete("/events/{id}/like", eventId).with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        mvc.perform(get("/events/{id}", eventId))
                .andExpect(jsonPath("$.likedCount").value(0));
    }

    @Test
    void retirer_son_like_ne_touche_pas_aux_likes_des_autres() throws Exception {
        String eventId = createEvent();
        mvc.perform(post("/events/{id}/like", eventId).with(user("alice"))).andExpect(status().isOk());
        mvc.perform(post("/events/{id}/like", eventId).with(user("bob"))).andExpect(status().isOk());

        mvc.perform(delete("/events/{id}/like", eventId).with(user("alice"))).andExpect(status().isOk());

        mvc.perform(get("/events/{id}", eventId))
                .andExpect(jsonPath("$.likedCount").value(1));
        assertThat(eventLikeRepository.existsById(new EventLikeId("bob", eventId))).isTrue();
    }

    @Test
    void retirer_un_like_absent_ou_deja_retire_reussit() throws Exception {
        String eventId = createEvent();

        mvc.perform(delete("/events/{id}/like", eventId).with(user("alice"))).andExpect(status().isOk());
        mvc.perform(delete("/events/{id}/like", "evt_supprime_entre_temps").with(user("alice"))).andExpect(status().isOk());
    }

    @Test
    void on_peut_liker_a_nouveau_apres_avoir_retire_son_like() throws Exception {
        String eventId = createEvent();
        mvc.perform(post("/events/{id}/like", eventId).with(user("alice"))).andExpect(status().isOk());
        mvc.perform(delete("/events/{id}/like", eventId).with(user("alice"))).andExpect(status().isOk());

        mvc.perform(post("/events/{id}/like", eventId).with(user("alice"))).andExpect(status().isOk());

        mvc.perform(get("/events/{id}", eventId))
                .andExpect(jsonPath("$.likedCount").value(1));
    }

    @Test
    void retirer_un_like_sans_etre_connecte_repond_401() throws Exception {
        String eventId = createEvent();
        mvc.perform(post("/events/{id}/like", eventId).with(user("alice"))).andExpect(status().isOk());

        mvc.perform(delete("/events/{id}/like", eventId)).andExpect(status().isUnauthorized());

        assertThat(eventLikeRepository.countByEventId(eventId)).isEqualTo(1);
    }

    // US-10 : GET /events/liked (utilisateurs uniques par test : les autres tests likent aussi)

    @Test
    void mes_evenements_likes_sont_renvoyes_du_plus_recent_au_plus_ancien() throws Exception {
        String me = "user-" + UUID.randomUUID();
        String premier = createEvent();
        String second = createEvent();
        mvc.perform(post("/events/{id}/like", premier).with(user(me))).andExpect(status().isOk());
        mvc.perform(post("/events/{id}/like", second).with(user(me))).andExpect(status().isOk());

        mvc.perform(get("/events/liked").with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(second, premier)))
                .andExpect(jsonPath("$[0].likedCount").value(1))
                .andExpect(jsonPath("$[0].venue.adresse").value("1 rue de la Paix"));
    }

    @Test
    void je_ne_vois_que_mes_propres_likes() throws Exception {
        String me = "user-" + UUID.randomUUID();
        String other = "user-" + UUID.randomUUID();
        String mine = createEvent();
        String theirs = createEvent();
        mvc.perform(post("/events/{id}/like", mine).with(user(me))).andExpect(status().isOk());
        mvc.perform(post("/events/{id}/like", theirs).with(user(other))).andExpect(status().isOk());

        mvc.perform(get("/events/liked").with(user(me)))
                .andExpect(jsonPath("$[*].id", contains(mine)));
        mvc.perform(get("/events/liked").with(user(other)))
                .andExpect(jsonPath("$[*].id", contains(theirs)));
    }

    @Test
    void un_like_retire_n_apparait_plus_et_un_evenement_passe_reste_dans_mes_likes() throws Exception {
        String me = "user-" + UUID.randomUUID();
        String retire = createEvent();
        String passe = createEvent(Instant.now().minus(Duration.ofDays(10)));
        mvc.perform(post("/events/{id}/like", retire).with(user(me))).andExpect(status().isOk());
        mvc.perform(post("/events/{id}/like", passe).with(user(me))).andExpect(status().isOk());
        mvc.perform(delete("/events/{id}/like", retire).with(user(me))).andExpect(status().isOk());

        mvc.perform(get("/events/liked").with(user(me)))
                .andExpect(jsonPath("$[*].id", contains(passe)));
    }

    @Test
    void sans_like_la_liste_est_vide() throws Exception {
        mvc.perform(get("/events/liked").with(user("user-" + UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void mes_likes_sans_etre_connecte_repond_401() throws Exception {
        mvc.perform(get("/events/liked")).andExpect(status().isUnauthorized());
    }

    /** Utilisateur connecté : JWT dont le sub est l'identifiant donné. */
    private static JwtRequestPostProcessor user(String subject) {
        return jwt().jwt(token -> token.subject(subject));
    }

    private String createEvent() {
        return createEvent(Instant.now().plus(Duration.ofDays(7)));
    }

    private String createEvent(Instant startAt) {
        Event event = new Event("evt_" + UUID.randomUUID());
        event.setTitle("Concert");
        event.setDescription("Une soirée");
        event.setCoverUrl("https://cdn/cover.png");
        event.setStartAt(startAt);
        event.setVenue(new Venue("Salle", "1 rue de la Paix", 48.85, 2.35));
        event.setUpdatedAt(Instant.now());
        return eventRepository.save(event).getId();
    }
}
