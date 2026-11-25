package com.easy.eventbyte.repository;

/** Places d'un ticket immobilisées par des réservations (projection de {@link BookingRepository#countHeldByTicketIds}). */
public interface TicketHeldCount {

    String getTicketId();

    long getHeld();
}
