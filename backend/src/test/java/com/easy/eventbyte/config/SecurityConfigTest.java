package com.easy.eventbyte.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.easy.eventbyte.exposition.EventController;
import com.easy.eventbyte.service.EventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-3 : les routes authentifiées répondent 401 sans token valide.
 * Les tokens sont signés avec une clé générée pour le test, à la place de celle de Keycloak.
 * Une requête autorisée par la sécurité aboutit à autre chose que 401 (404 si la route n'a pas encore de controller).
 */
@WebMvcTest(EventController.class)
@Import({SecurityConfig.class, SecurityConfigTest.TestJwtConfig.class})
class SecurityConfigTest {

    static final String ISSUER = "http://localhost:8180/realms/eventbyte";
    static final KeyPair KEYCLOAK_KEYS = generateRsaKeyPair();
    static final KeyPair OTHER_KEYS = generateRsaKeyPair();

    @Autowired
    MockMvc mvc;
    @MockitoBean
    EventService eventService;

    @Test
    void sans_token_une_route_authentifiee_repond_401() throws Exception {
        mvc.perform(get("/bookings/user")).andExpect(status().isUnauthorized());
        mvc.perform(post("/bookings")).andExpect(status().isUnauthorized());
        mvc.perform(get("/events/liked")).andExpect(status().isUnauthorized());
        mvc.perform(post("/events/evt_1/like")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/events/evt_1/like")).andExpect(status().isUnauthorized());
    }

    @Test
    void avec_un_token_valide_la_requete_passe() throws Exception {
        mvc.perform(get("/bookings/user").header(HttpHeaders.AUTHORIZATION, bearer(validToken())))
                .andExpect(status().isNotFound());
    }

    @Test
    void un_token_expire_est_refuse() throws Exception {
        String token = token(KEYCLOAK_KEYS, ISSUER, Instant.now().minus(Duration.ofHours(1)));

        mvc.perform(get("/bookings/user").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void un_token_d_un_autre_emetteur_est_refuse() throws Exception {
        String token = token(KEYCLOAK_KEYS, "http://localhost:8180/realms/autre", Instant.now());

        mvc.perform(get("/bookings/user").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void un_token_signe_par_une_autre_cle_est_refuse() throws Exception {
        String token = token(OTHER_KEYS, ISSUER, Instant.now());

        mvc.perform(get("/bookings/user").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void un_token_mal_forme_est_refuse() throws Exception {
        mvc.perform(get("/bookings/user").header(HttpHeaders.AUTHORIZATION, bearer("pas-un-jwt")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void les_routes_publiques_sont_accessibles_sans_token() throws Exception {
        mvc.perform(get("/events/discover").param("lat", "48.85").param("lng", "2.35")).andExpect(notUnauthorized());
        mvc.perform(get("/events").param("ids", "evt_1")).andExpect(notUnauthorized());
        mvc.perform(get("/events/evt_1")).andExpect(notUnauthorized());
        mvc.perform(get("/events/evt_1/tickets")).andExpect(notUnauthorized());
        mvc.perform(get("/events/search")).andExpect(notUnauthorized());
    }

    @Test
    void le_frontend_peut_appeler_l_api_avec_un_token() throws Exception {
        mvc.perform(options("/bookings/user")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }

    @Test
    void une_origine_inconnue_est_refusee() throws Exception {
        mvc.perform(options("/bookings/user")
                        .header(HttpHeaders.ORIGIN, "http://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }

    private static ResultMatcher notUnauthorized() {
        return result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401);
    }

    private static String validToken() {
        return token(KEYCLOAK_KEYS, ISSUER, Instant.now());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String token(KeyPair keys, String issuer, Instant issuedAt) {
        RSAKey jwk = new RSAKey.Builder((RSAPublicKey) keys.getPublic())
                .privateKey((RSAPrivateKey) keys.getPrivate())
                .build();
        var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("user-123")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(Duration.ofMinutes(5)))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
                .getTokenValue();
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Remplace le décodeur Keycloak : même validation (signature, expiration, issuer), clé du test. */
    @TestConfiguration
    static class TestJwtConfig {

        @Bean
        JwtDecoder jwtDecoder() {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEYCLOAK_KEYS.getPublic()).build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
            return decoder;
        }
    }
}
