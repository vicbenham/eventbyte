package com.easy.eventbyte.domain;

/** Quantité de tickets au même prix unitaire (en centimes). */
public record PricedQuantity(int quantity, long unitPrice) {
}
