package com.easy.eventbyte.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;


import static org.assertj.core.api.Assertions.assertThat;

class StrapiEventMessageTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    // Message réel capturé sur l'exchange events
    private static final String CREATED = """
            {
              "id": "evt_01a20df1-596a-712b-81b9-c3c48e678bd2",
              "title": "Marathon de la Ville",
              "description": "Course à pied pour tous les niveaux",
              "cover": "https://res.cloudinary.com/demo/image/upload/cover.png",
              "venue": {
                "name": "Centre-ville McKenzie",
                "address": "72 Impasse de la Paix, 49000 Angers",
                "latitude": 47.457696208107414,
                "longitude": -0.524091916596272
              },
              "tickets": [
                { "id": "tkt_01a20df1-5968-7120-80a0-35916dd7e918", "name": "Standard", "quantity": 14801, "price": 26.35 }
              ]
            }
            """;

    @Test
    void le_message_strapi_est_lu_avec_son_lieu_et_ses_tickets() {
        var message = jsonMapper.readValue(CREATED, StrapiEventMessage.class);

        assertThat(message.id()).isEqualTo("evt_01a20df1-596a-712b-81b9-c3c48e678bd2");
        assertThat(message.venue().address()).isEqualTo("72 Impasse de la Paix, 49000 Angers");
        assertThat(message.venue().latitude()).isEqualTo(47.457696208107414);
        assertThat(message.tickets()).singleElement().satisfies(ticket -> {
            assertThat(ticket.quantity()).isEqualTo(14801);
            assertThat(ticket.priceInCents()).isEqualTo(2635);
        });
    }

    @Test
    void le_message_de_suppression_ne_contient_que_l_id() {
        var message = jsonMapper.readValue("{\"id\":\"evt_1\"}", StrapiEventMessage.class);

        assertThat(message.id()).isEqualTo("evt_1");
    }

}
