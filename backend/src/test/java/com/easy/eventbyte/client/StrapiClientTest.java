package com.easy.eventbyte.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Le client est testé contre un faux serveur Strapi local. */
class StrapiClientTest {

    HttpServer strapi;
    String baseUrl;
    final AtomicReference<String> lastQuery = new AtomicReference<>();
    final AtomicReference<String> lastAuthorization = new AtomicReference<>();
    volatile int status = 200;
    volatile String body = "";

    @BeforeEach
    void startFakeStrapi() throws IOException {
        strapi = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        strapi.createContext("/api/events", exchange -> {
            lastQuery.set(URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8));
            lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        strapi.start();
        baseUrl = "http://localhost:" + strapi.getAddress().getPort();
    }

    @AfterEach
    void stopFakeStrapi() {
        strapi.stop(0);
    }

    @Test
    void lit_la_date_de_l_evenement_avec_le_token() {
        body = """
                {"data":[{"id":1,"documentId":"abc","startAt":"2027-01-02T14:00:00.000Z"}],"meta":{}}
                """;

        var startAt = new StrapiClient(baseUrl, "secret-token").findStartAt("evt_1");

        assertThat(startAt).contains(Instant.parse("2027-01-02T14:00:00Z"));
        assertThat(lastQuery.get()).contains("filters[uuid][$eq]=evt_1").contains("fields[0]=startAt");
        assertThat(lastAuthorization.get()).isEqualTo("Bearer secret-token");
    }

    @Test
    void un_evenement_inconnu_de_strapi_n_a_pas_de_date() {
        body = """
                {"data":[],"meta":{}}
                """;

        assertThat(new StrapiClient(baseUrl, "secret-token").findStartAt("evt_1")).isEmpty();
    }

    @Test
    void une_erreur_de_strapi_ne_fait_pas_echouer_l_ingestion() {
        status = 403;

        assertThat(new StrapiClient(baseUrl, "mauvais-token").findStartAt("evt_1")).isEmpty();
    }

    @Test
    void strapi_injoignable_ne_fait_pas_echouer_l_ingestion() {
        strapi.stop(0);

        assertThat(new StrapiClient(baseUrl, "secret-token").findStartAt("evt_1")).isEmpty();
    }

    @Test
    void sans_token_strapi_n_est_pas_appele() {
        assertThat(new StrapiClient(baseUrl, "").findStartAt("evt_1")).isEmpty();
        assertThat(lastQuery.get()).isNull();
    }
}
