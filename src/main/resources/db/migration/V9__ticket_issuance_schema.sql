-- V9: Ticket Issuance, QR Tokens, Check-in & Transfers

-- 1. Tickets
CREATE TABLE tickets (
    id UUID PRIMARY KEY,
    order_item_id UUID NOT NULL REFERENCES order_items(id) ON DELETE RESTRICT,
    current_owner_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    original_buyer_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE RESTRICT,
    event_seat_id UUID REFERENCES event_seats(id) ON DELETE RESTRICT,
    event_area_id UUID REFERENCES event_areas(id) ON DELETE RESTRICT,
    ticket_type_id UUID REFERENCES ticket_types(id) ON DELETE RESTRICT,
    sale_phase_id UUID REFERENCES ticket_sale_phases(id) ON DELETE RESTRICT,
    ticket_code VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL DEFAULT 'ISSUED',
    issued_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tickets_current_owner ON tickets(current_owner_user_id);
CREATE INDEX idx_tickets_event_id ON tickets(event_id);
CREATE INDEX idx_tickets_order_item_id ON tickets(order_item_id);
CREATE INDEX idx_tickets_ticket_code ON tickets(ticket_code);
CREATE INDEX idx_tickets_status ON tickets(status);
CREATE INDEX idx_tickets_seat_id ON tickets(event_seat_id) WHERE event_seat_id IS NOT NULL;
CREATE INDEX idx_tickets_area_id ON tickets(event_area_id);

-- 2. Ticket QR Tokens (Dynamic hash verify, revoke upon transfer/resale)
CREATE TABLE ticket_qr_tokens (
    id UUID PRIMARY KEY,
    ticket_id UUID NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    issued_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at TIMESTAMPTZ
);

CREATE INDEX idx_qr_tokens_ticket_id ON ticket_qr_tokens(ticket_id);
CREATE INDEX idx_qr_tokens_hash ON ticket_qr_tokens(token_hash);
CREATE INDEX idx_qr_tokens_active ON ticket_qr_tokens(ticket_id, status) WHERE status = 'ACTIVE';

-- 3. Ticket Check-ins (Audit scan history including duplicates/invalids)
CREATE TABLE ticket_checkins (
    id UUID PRIMARY KEY,
    ticket_id UUID NOT NULL REFERENCES tickets(id) ON DELETE RESTRICT,
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE RESTRICT,
    checked_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    gate_name VARCHAR(100),
    result VARCHAR(30) NOT NULL, -- SUCCESS, INVALID, DUPLICATE
    checked_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ticket_checkins_ticket_id ON ticket_checkins(ticket_id);
CREATE INDEX idx_ticket_checkins_event_id ON ticket_checkins(event_id);
CREATE INDEX idx_ticket_checkins_time ON ticket_checkins(checked_at);

-- 4. Ticket Transfers (Transfer history: Resale, Admin, Refund Reissue)
CREATE TABLE ticket_transfers (
    id UUID PRIMARY KEY,
    ticket_id UUID NOT NULL REFERENCES tickets(id) ON DELETE RESTRICT,
    from_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    to_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    source_type VARCHAR(30) NOT NULL, -- RESALE, ADMIN, REFUND_REISSUE
    source_id UUID,
    status VARCHAR(30) NOT NULL DEFAULT 'COMPLETED',
    transferred_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ticket_transfers_ticket ON ticket_transfers(ticket_id);
CREATE INDEX idx_ticket_transfers_from_user ON ticket_transfers(from_user_id);
CREATE INDEX idx_ticket_transfers_to_user ON ticket_transfers(to_user_id);
