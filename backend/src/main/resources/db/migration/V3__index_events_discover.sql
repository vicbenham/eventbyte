-- GET /events/discover : présélection par zone géographique, puis événements à venir
CREATE INDEX idx_events_venue_coordinates ON events (venue_latitude, venue_longitude);
CREATE INDEX idx_events_start_at ON events (start_at);
