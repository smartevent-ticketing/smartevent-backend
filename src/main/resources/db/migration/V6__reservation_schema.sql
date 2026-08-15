-- V6: Reservation Schema (reservations, reservation_items)

-- 1. Reservations
CREATE TABLE reservations (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE RESTRICT,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMPTZ NOT NULL,
    idempotency_key VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_reservation_expiry CHECK (expires_at > created_at)
);

CREATE INDEX idx_reservations_user_id ON reservations(user_id);
CREATE INDEX idx_reservations_event_id ON reservations(event_id);
CREATE INDEX idx_reservations_status_expires ON reservations(status, expires_at);
CREATE INDEX idx_reservations_idempotency ON reservations(idempotency_key) WHERE idempotency_key IS NOT NULL;

-- Chỉ cho phép 1 reservation PENDING cho mỗi user trên 1 event tại một thời điểm
CREATE UNIQUE INDEX idx_reservations_active_per_user_event 
    ON reservations(user_id, event_id) 
    WHERE status = 'PENDING';

-- 2. Reservation Items
CREATE TABLE reservation_items (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
    ticket_type_id UUID NOT NULL REFERENCES ticket_types(id) ON DELETE RESTRICT,
    sale_phase_id UUID NOT NULL REFERENCES ticket_sale_phases(id) ON DELETE RESTRICT,
    event_seat_id UUID REFERENCES event_seats(id) ON DELETE RESTRICT,
    quantity INT NOT NULL,
    unit_price DECIMAL(15,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_res_item_quantity CHECK (quantity > 0),
    CONSTRAINT chk_res_item_price CHECK (unit_price >= 0)
);

CREATE INDEX idx_reservation_items_res_id ON reservation_items(reservation_id);
CREATE INDEX idx_reservation_items_seat_id ON reservation_items(event_seat_id) WHERE event_seat_id IS NOT NULL;
