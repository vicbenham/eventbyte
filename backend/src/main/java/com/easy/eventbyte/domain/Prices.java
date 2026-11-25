package com.easy.eventbyte.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Conversion des prix : Strapi les saisit en euros, tout le backend travaille en centimes. */
public final class Prices {

    private Prices() {
    }

    /** Arrondi au centime le plus proche, sans erreur de virgule flottante. Un prix absent vaut 0. */
    public static long eurosToCents(BigDecimal euros) {
        if (euros == null) {
            return 0;
        }
        return euros.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
