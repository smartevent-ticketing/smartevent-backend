-- V5: Ticketing & Inventory (ticket_types, sale_phases, rules, inventory_counters, user_counters)

-- 1. Ticket Types
CREATE TABLE ticket_types (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    event_area_id UUID NOT NULL REFERENCES event_areas(id) ON DELETE RESTRICT,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ticket_types_event_id ON ticket_types(event_id);
CREATE INDEX idx_ticket_types_area_id ON ticket_types(event_area_id);

-- 2. Ticket Sale Phases
CREATE TABLE ticket_sale_phases (
    id UUID PRIMARY KEY,
    ticket_type_id UUID NOT NULL REFERENCES ticket_types(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    price DECIMAL(15,2) NOT NULL,
    quantity INT NOT NULL,
    sale_start_at TIMESTAMPTZ NOT NULL,
    sale_end_at TIMESTAMPTZ NOT NULL,
    sold_out_at TIMESTAMPTZ,
    max_per_order INT NOT NULL DEFAULT 4,
    max_per_user INT,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_phase_price CHECK (price >= 0),
    CONSTRAINT chk_phase_quantity CHECK (quantity > 0),
    CONSTRAINT chk_phase_max_per_order CHECK (max_per_order > 0),
    CONSTRAINT chk_phase_max_per_user CHECK (max_per_user IS NULL OR max_per_user > 0),
    CONSTRAINT chk_phase_sale_time CHECK (sale_end_at > sale_start_at)
);

CREATE INDEX idx_ticket_sale_phases_ticket_type ON ticket_sale_phases(ticket_type_id);
CREATE INDEX idx_ticket_sale_phases_status ON ticket_sale_phases(status);
CREATE INDEX idx_ticket_sale_phases_timing ON ticket_sale_phases(sale_start_at, sale_end_at);

-- 3. Ticket Phase Rules
CREATE TABLE ticket_phase_rules (
    id UUID PRIMARY KEY,
    sale_phase_id UUID NOT NULL REFERENCES ticket_sale_phases(id) ON DELETE CASCADE,
    rule_type VARCHAR(50) NOT NULL,
    rule_value TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ticket_phase_rules_phase_id ON ticket_phase_rules(sale_phase_id);

-- 4. Inventory Counters (Single source of truth for STANDING & phase summary)
CREATE TABLE inventory_counters (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    event_area_id UUID NOT NULL REFERENCES event_areas(id) ON DELETE RESTRICT,
    ticket_type_id UUID NOT NULL REFERENCES ticket_types(id) ON DELETE CASCADE,
    sale_phase_id UUID NOT NULL UNIQUE REFERENCES ticket_sale_phases(id) ON DELETE CASCADE,
    total_quantity INT NOT NULL,
    held_quantity INT NOT NULL DEFAULT 0,
    sold_quantity INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_inv_held CHECK (held_quantity >= 0),
    CONSTRAINT chk_inv_sold CHECK (sold_quantity >= 0),
    CONSTRAINT chk_inv_total CHECK (held_quantity + sold_quantity <= total_quantity)
);

CREATE INDEX idx_inventory_counters_event_id ON inventory_counters(event_id);
CREATE INDEX idx_inventory_counters_phase_id ON inventory_counters(sale_phase_id);

-- 5. User Sale Phase Counters (Single source of truth for max_per_user enforcement)
CREATE TABLE user_sale_phase_counters (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    sale_phase_id UUID NOT NULL REFERENCES ticket_sale_phases(id) ON DELETE CASCADE,
    held_quantity INT NOT NULL DEFAULT 0,
    purchased_quantity INT NOT NULL DEFAULT 0,
    refunded_quantity INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, sale_phase_id),
    CONSTRAINT chk_user_counter_held CHECK (held_quantity >= 0),
    CONSTRAINT chk_user_counter_purchased CHECK (purchased_quantity >= 0),
    CONSTRAINT chk_user_counter_refunded CHECK (refunded_quantity >= 0)
);

CREATE INDEX idx_user_phase_counters_user ON user_sale_phase_counters(user_id);
CREATE INDEX idx_user_phase_counters_phase ON user_sale_phase_counters(sale_phase_id);
