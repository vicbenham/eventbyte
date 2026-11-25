-- Réservations. event_id n'a pas de clé étrangère : une réservation reste consultable
-- même si l'événement est ensuite supprimé dans Strapi.
CREATE TABLE bookings (
    id            VARCHAR(64)              PRIMARY KEY,
    user_id       VARCHAR(255)             NOT NULL,
    event_id      VARCHAR(64)              NOT NULL,
    booked_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    status        VARCHAR(32)              NOT NULL,
    total         BIGINT                   NOT NULL,
    fees          BIGINT                   NOT NULL,
    client_secret VARCHAR(255)
);

CREATE INDEX idx_bookings_user_id ON bookings (user_id);

-- Lignes : nom et prix du ticket figés au moment de la réservation
CREATE TABLE booking_lines (
    booking_id  VARCHAR(64)  NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    ticket_id   VARCHAR(64)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    quantity    INTEGER      NOT NULL CHECK (quantity > 0),
    unit_price  BIGINT       NOT NULL CHECK (unit_price >= 0),
    PRIMARY KEY (booking_id, ticket_id)
);

-- Stock disponible : places réservées par ticket
CREATE INDEX idx_booking_lines_ticket_id ON booking_lines (ticket_id);
