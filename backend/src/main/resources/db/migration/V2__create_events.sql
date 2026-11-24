-- Table de test créée par V1, inutile
DROP TABLE IF EXISTS totocraque;

-- Événements ingérés depuis Strapi. L'id est celui de Strapi (evt_...) : il sert de clé d'idempotence.
CREATE TABLE events (
    id              VARCHAR(64)              PRIMARY KEY,
    title           VARCHAR(255)             NOT NULL,
    description     TEXT,
    cover_url       TEXT,
    start_at        TIMESTAMP WITH TIME ZONE,
    venue_name      VARCHAR(255)             NOT NULL,
    venue_address   VARCHAR(500)             NOT NULL,
    venue_latitude  DOUBLE PRECISION         NOT NULL,
    venue_longitude DOUBLE PRECISION         NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE tickets (
    id             VARCHAR(64)  PRIMARY KEY,
    event_id       VARCHAR(64)  NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    name           VARCHAR(255) NOT NULL,
    quantity       INTEGER      NOT NULL CHECK (quantity >= 0),
    price_in_cents BIGINT       NOT NULL CHECK (price_in_cents >= 0)
);

CREATE INDEX idx_tickets_event_id ON tickets (event_id);

-- Tombstones : un message created/updated rejoué après la suppression est ignoré
CREATE TABLE deleted_events (
    id         VARCHAR(64)              PRIMARY KEY,
    deleted_at TIMESTAMP WITH TIME ZONE NOT NULL
);
