package com.easy.eventbyte.exposition;

import com.easy.eventbyte.TestcontainersConfiguration;
import com.easy.eventbyte.config.RabbitMQConfig;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * US-2 de bout en bout : message publié sur l'exchange events au format exact de Strapi
 * (sans content-type) → listener → service → Postgres.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class StrapiEventListenerIntegrationTest {

    @Autowired
    RabbitTemplate rabbit;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void event_created_enregistre_l_evenement_avec_son_lieu_et_ses_tickets() {
        String id = newEventId();

        publish("event.created", eventJson(id, "Concert", 120, "25.00"));

        await().untilAsserted(() -> assertThat(title(id)).isEqualTo("Concert"));
        assertThat(jdbc.queryForObject("select venue_name from events where id = ?", String.class, id))
                .isEqualTo("Zénith");
        assertThat(jdbc.queryForMap("select name, quantity, price_in_cents from tickets where event_id = ?", id))
                .containsEntry("name", "Standard")
                .containsEntry("quantity", 120)
                .containsEntry("price_in_cents", 2500L);
    }

    @Test
    void un_message_recu_deux_fois_ne_cree_pas_de_doublon() {
        String id = newEventId();
        String json = eventJson(id, "Concert", 120, "25.00");

        publish("event.created", json);
        publish("event.created", json);
        publish("event.updated", json);

        await().untilAsserted(() -> assertThat(title(id)).isEqualTo("Concert"));
        await().during(Duration.ofSeconds(1)).untilAsserted(() -> {
            assertThat(count("select count(*) from events where id = ?", id)).isEqualTo(1);
            assertThat(count("select count(*) from tickets where event_id = ?", id)).isEqualTo(1);
        });
    }

    @Test
    void event_updated_met_a_jour_l_evenement_et_ses_tickets() {
        String id = newEventId();
        publish("event.created", eventJson(id, "Concert", 120, "25.00"));
        await().untilAsserted(() -> assertThat(title(id)).isEqualTo("Concert"));

        publish("event.updated", eventJson(id, "Concert reporté", 80, "30.50"));

        await().untilAsserted(() -> assertThat(title(id)).isEqualTo("Concert reporté"));
        assertThat(jdbc.queryForMap("select quantity, price_in_cents from tickets where event_id = ?", id))
                .containsEntry("quantity", 80)
                .containsEntry("price_in_cents", 3050L);
    }

    @Test
    void event_deleted_supprime_l_evenement_et_un_rejeu_ne_le_recree_pas() {
        String id = newEventId();
        String json = eventJson(id, "Concert", 120, "25.00");
        publish("event.created", json);
        await().untilAsserted(() -> assertThat(title(id)).isEqualTo("Concert"));

        publish("event.deleted", "{\"id\":\"" + id + "\"}");
        await().untilAsserted(() -> assertThat(count("select count(*) from events where id = ?", id)).isZero());

        publish("event.created", json);
        await().during(Duration.ofSeconds(1)).untilAsserted(() -> {
            assertThat(count("select count(*) from events where id = ?", id)).isZero();
            assertThat(count("select count(*) from tickets where event_id = ?", id)).isZero();
        });
    }

    @Test
    void une_suppression_recue_avant_la_creation_l_emporte() {
        // L'outbox de Strapi ne garantit pas l'ordre de publication
        String id = newEventId();

        publish("event.deleted", "{\"id\":\"" + id + "\"}");
        await().untilAsserted(() -> assertThat(count("select count(*) from deleted_events where id = ?", id)).isOne());
        publish("event.created", eventJson(id, "Concert", 120, "25.00"));

        await().during(Duration.ofSeconds(1)).untilAsserted(() ->
                assertThat(count("select count(*) from events where id = ?", id)).isZero());
    }

    private void publish(String routingKey, String json) {
        rabbit.send(RabbitMQConfig.EXCHANGE, routingKey,
                MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8)).build());
    }

    private String title(String id) {
        return jdbc.query("select title from events where id = ?",
                rs -> rs.next() ? rs.getString(1) : null, id);
    }

    private int count(String sql, String id) {
        return jdbc.queryForObject(sql, Integer.class, id);
    }

    private static String newEventId() {
        return "evt_" + UUID.randomUUID();
    }

    private static String eventJson(String id, String title, int quantity, String price) {
        return """
                {"id":"%s","title":"%s","description":"desc","cover":"https://cdn/c.png",
                 "venue":{"name":"Zénith","address":"1 Rue du Concert, 75019 Paris","latitude":48.89,"longitude":2.39},
                 "tickets":[{"id":"%s_tkt","name":"Standard","quantity":%d,"price":%s}]}
                """.formatted(id, title, id, quantity, price);
    }
}
