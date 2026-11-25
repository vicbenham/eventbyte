package com.easy.eventbyte.exposition;

import com.easy.eventbyte.TestcontainersConfiguration;
import com.easy.eventbyte.model.Event;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US-8 : POST /events/:eventId/like. */
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

    /** Utilisateur connecté : JWT dont le sub est l'identifiant donné. */
    private static JwtRequestPostProcessor user(String subject) {
        return jwt().jwt(token -> token.subject(subject));
    }

    private String createEvent() {
        Event event = new Event("evt_" + UUID.randomUUID());
        event.setTitle("Concert");
        event.setDescription("Une soirée");
        event.setCoverUrl("https://cdn/cover.png");
        event.setStartAt(Instant.now().plus(Duration.ofDays(7)));
        event.setVenue(new Venue("Salle", "1 rue de la Paix", 48.85, 2.35));
        event.setUpdatedAt(Instant.now());
        return eventRepository.save(event).getId();
    }
}
