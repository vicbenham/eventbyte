-- Échéance de paiement : passé ce délai, une réservation non payée expire et ses places retournent en vente
ALTER TABLE bookings ADD COLUMN expires_at TIMESTAMP WITH TIME ZONE;

-- Réservations déjà en cours : même délai que les nouvelles (10 minutes)
UPDATE bookings
SET expires_at = booked_at + INTERVAL '10 minutes'
WHERE status IN ('RESERVED', 'AWAITING_PAYMENT');

-- Recherche des réservations à expirer
CREATE INDEX idx_bookings_pending_expiration ON bookings (expires_at)
    WHERE status IN ('RESERVED', 'AWAITING_PAYMENT');
