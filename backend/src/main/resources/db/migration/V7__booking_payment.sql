-- PaymentIntent Stripe de la réservation (le webhook de paiement le référence, US-17)
ALTER TABLE bookings ADD COLUMN payment_intent_id VARCHAR(255);

CREATE UNIQUE INDEX idx_bookings_payment_intent_id ON bookings (payment_intent_id);
