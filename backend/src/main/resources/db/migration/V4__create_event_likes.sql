-- Un like par utilisateur et par événement : la clé primaire rend le like idempotent.
-- user_id est le sub du JWT Keycloak. Les likes disparaissent avec l'événement.
CREATE TABLE event_likes (
    user_id  VARCHAR(255)             NOT NULL,
    event_id VARCHAR(64)              NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    liked_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (user_id, event_id)
);

-- likedCount : comptage des likes par événement
CREATE INDEX idx_event_likes_event_id ON event_likes (event_id);
