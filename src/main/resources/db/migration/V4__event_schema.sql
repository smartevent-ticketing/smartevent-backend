-- V4: Event, Venue, Category, Area, Seat, Event Files & Payment Methods

CREATE EXTENSION IF NOT EXISTS btree_gist;

-- 1. Venues (Admin CRUD, Organizer SELECT)
CREATE TABLE venues (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(500) NOT NULL,
    city VARCHAR(100) NOT NULL,
    latitude DECIMAL(9,6),
    longitude DECIMAL(9,6),
    capacity INT,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_venue_capacity CHECK (capacity IS NULL OR capacity > 0)
);

CREATE INDEX idx_venues_city ON venues(city);
CREATE INDEX idx_venues_status ON venues(status);

-- 2. Categories
CREATE TABLE categories (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_categories_slug ON categories(slug);

-- 3. Events
CREATE TABLE events (
    id UUID PRIMARY KEY,
    organizer_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    venue_id UUID REFERENCES venues(id) ON DELETE RESTRICT,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    resale_enabled BOOLEAN NOT NULL DEFAULT false,
    max_resale_price_multiplier NUMERIC(5,2),
    resale_deadline_hours_before INT,
    virtual_queue_enabled BOOLEAN NOT NULL DEFAULT false,
    queue_batch_size INT DEFAULT 50,
    city VARCHAR(100),
    search_vector TSVECTOR,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_events_time CHECK (end_time > start_time),
    CONSTRAINT chk_events_multiplier CHECK (max_resale_price_multiplier IS NULL OR max_resale_price_multiplier > 0),
    CONSTRAINT chk_events_resale_deadline CHECK (resale_deadline_hours_before IS NULL OR resale_deadline_hours_before >= 0),
    EXCLUDE USING gist (
        venue_id WITH =,
        tstzrange(start_time, end_time) WITH &&
    ) WHERE (status IN ('PUBLISHED', 'PENDING_APPROVAL') AND venue_id IS NOT NULL)
);

CREATE INDEX idx_events_organizer_id ON events(organizer_id);
CREATE INDEX idx_events_venue_id ON events(venue_id);
CREATE INDEX idx_events_status ON events(status);
CREATE INDEX idx_events_start_time ON events(start_time);
CREATE INDEX idx_events_city ON events(city);
CREATE INDEX idx_events_search ON events USING GIN(search_vector);

-- Trigger auto-update search_vector
CREATE OR REPLACE FUNCTION events_search_vector_update() RETURNS trigger AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('simple', COALESCE(NEW.name, '')), 'A') ||
        setweight(to_tsvector('simple', COALESCE(NEW.city, '')), 'B') ||
        setweight(to_tsvector('simple', COALESCE(NEW.description, '')), 'C');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_events_search_vector
    BEFORE INSERT OR UPDATE OF name, city, description
    ON events
    FOR EACH ROW
    EXECUTE FUNCTION events_search_vector_update();

-- 4. Event Categories (N:N)
CREATE TABLE event_categories (
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    PRIMARY KEY (event_id, category_id)
);

CREATE INDEX idx_event_categories_category_id ON event_categories(category_id);

-- 5. Event Areas
CREATE TABLE event_areas (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    area_type VARCHAR(30) NOT NULL DEFAULT 'STANDING',
    capacity INT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_area_capacity CHECK (capacity > 0)
);

CREATE INDEX idx_event_areas_event_id ON event_areas(event_id);

-- 6. Event Seats (SEATED only)
CREATE TABLE event_seats (
    id UUID PRIMARY KEY,
    event_area_id UUID NOT NULL REFERENCES event_areas(id) ON DELETE CASCADE,
    row_name VARCHAR(50) NOT NULL,
    seat_number VARCHAR(50) NOT NULL,
    label VARCHAR(100),
    status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
    hold_expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (event_area_id, row_name, seat_number)
);

CREATE INDEX idx_event_seats_area_status ON event_seats(event_area_id, status);
CREATE INDEX idx_event_seats_hold_expires ON event_seats(hold_expires_at) WHERE status = 'HELD';

-- 7. Event Files
CREATE TABLE event_files (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    file_id UUID NOT NULL REFERENCES files(id) ON DELETE RESTRICT,
    file_type VARCHAR(30) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_event_files_event_id ON event_files(event_id);

-- 8. Event Payment Methods
CREATE TABLE event_payment_methods (
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    method VARCHAR(30) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    config_json JSONB,
    PRIMARY KEY (event_id, method)
);
