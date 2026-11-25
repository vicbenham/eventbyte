package com.easy.eventbyte.repository;

/** Nombre de likes d'un événement (projection de {@link EventLikeRepository#countByEventIds}). */
public interface EventLikeCount {

    String getEventId();

    long getCount();
}
