package com.easy.eventbyte.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PricesTest {

    @ParameterizedTest
    @CsvSource({"26.35, 2635", "24.9, 2490", "10, 1000", "0, 0", "19.999, 2000", "0.005, 1", "0.1, 10"})
    void le_prix_en_euros_est_converti_en_centimes_sans_erreur_d_arrondi(String euros, long cents) {
        assertThat(Prices.eurosToCents(new BigDecimal(euros))).isEqualTo(cents);
    }

    @Test
    void un_prix_absent_vaut_zero() {
        assertThat(Prices.eurosToCents(null)).isZero();
    }
}
