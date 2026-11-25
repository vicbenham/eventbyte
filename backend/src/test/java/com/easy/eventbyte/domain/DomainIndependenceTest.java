package com.easy.eventbyte.domain;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** US-19 : le domaine ne dépend ni de Spring, ni de JPA, ni d'aucun autre framework. */
class DomainIndependenceTest {

    static final Path DOMAIN_SOURCES = Path.of("src/main/java/com/easy/eventbyte/domain");
    static final List<String> FORBIDDEN = List.of(
            "org.springframework", "jakarta.persistence", "org.hibernate", "lombok",
            "com.fasterxml.jackson", "tools.jackson", "com.easy.eventbyte.model", "com.easy.eventbyte.dto");

    @Test
    void aucune_classe_du_domaine_n_importe_un_framework_ni_les_couches_techniques() throws IOException {
        try (Stream<Path> files = Files.walk(DOMAIN_SOURCES)) {
            List<String> violations = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(this::importsOf)
                    .filter(line -> FORBIDDEN.stream().anyMatch(line::contains))
                    .toList();

            assertThat(violations).isEmpty();
        }
    }

    @Test
    void le_domaine_contient_bien_les_regles_metier() throws IOException {
        try (Stream<Path> files = Files.list(DOMAIN_SOURCES)) {
            assertThat(files.map(path -> path.getFileName().toString())).contains(
                    "BookingPrice.java", "BookingRules.java", "TicketStock.java",
                    "BookingExpiration.java", "PaymentDecision.java", "TicketSync.java");
        }
    }

    private Stream<String> importsOf(Path file) {
        try {
            return Files.readAllLines(file).stream()
                    .filter(line -> line.startsWith("import "))
                    .map(line -> file.getFileName() + " : " + line);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
