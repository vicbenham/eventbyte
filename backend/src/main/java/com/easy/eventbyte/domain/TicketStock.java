package com.easy.eventbyte.domain;

import java.util.Collection;

/** Stock des tickets : stock défini par l'organisateur moins les places immobilisées par des réservations. */
public final class TicketStock {

    private TicketStock() {
    }

    /** Places encore réservables (jamais négatif, même si le stock a été réduit sous les places déjà vendues). */
    public static int available(int stock, long held) {
        return (int) Math.max(stock - held, 0);
    }

    /** Complet quand plus aucun type de ticket n'a de place (un événement sans ticket est complet). */
    public static boolean isSoldOut(Collection<Integer> availableByTicket) {
        return availableByTicket.stream().allMatch(available -> available <= 0);
    }
}
