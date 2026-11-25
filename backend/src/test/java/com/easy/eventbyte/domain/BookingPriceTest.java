package com.easy.eventbyte.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BookingPriceTest {

    @Test
    void le_total_est_le_prix_des_tickets_plus_30_centimes_de_frais_par_ticket() {
        BookingPrice price = BookingPrice.of(List.of(
                new PricedQuantity(2, 2500),
                new PricedQuantity(1, 9000)));

        assertThat(price.subtotal()).isEqualTo(2 * 2500 + 9000);
        assertThat(price.fees()).isEqualTo(3 * 30);
        assertThat(price.total()).isEqualTo(2 * 2500 + 9000 + 3 * 30);
    }

    @Test
    void des_tickets_gratuits_ne_coutent_que_les_frais() {
        BookingPrice price = BookingPrice.of(List.of(new PricedQuantity(4, 0)));

        assertThat(price.subtotal()).isZero();
        assertThat(price.total()).isEqualTo(4 * 30);
    }

    @Test
    void sans_ligne_tout_vaut_zero() {
        assertThat(BookingPrice.of(List.of())).isEqualTo(new BookingPrice(0, 0, 0));
    }
}
