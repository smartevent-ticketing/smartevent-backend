-- V21: Event-level anti-scalping max tickets per user limit
ALTER TABLE events ADD COLUMN max_tickets_per_user INT;
ALTER TABLE events ADD CONSTRAINT chk_events_max_tickets_per_user
    CHECK (max_tickets_per_user IS NULL OR max_tickets_per_user > 0);
