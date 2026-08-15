-- V7: Ordering Schema (orders, order_items)

-- 1. Orders
CREATE TABLE orders (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    reservation_id UUID REFERENCES reservations(id) ON DELETE RESTRICT,
    order_code VARCHAR(50) NOT NULL UNIQUE,
    subtotal DECIMAL(15,2) NOT NULL,
    discount_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    fee_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(15,2) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'VND',
    payment_deadline TIMESTAMPTZ,
    customer_note TEXT,
    coupon_id UUID, -- Sẽ được liên kết với coupons(id) ở Phase 2
    selected_payment_method VARCHAR(30),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_PAYMENT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_orders_amounts CHECK (
        subtotal >= 0 AND 
        total_amount >= 0 AND 
        discount_amount >= 0 AND 
        fee_amount >= 0
    )
);

CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_reservation_id ON orders(reservation_id);
CREATE INDEX idx_orders_order_code ON orders(order_code);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_deadline ON orders(payment_deadline) WHERE status = 'PENDING_PAYMENT';

-- 2. Order Items
CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    ticket_type_id UUID NOT NULL REFERENCES ticket_types(id) ON DELETE RESTRICT,
    sale_phase_id UUID NOT NULL REFERENCES ticket_sale_phases(id) ON DELETE RESTRICT,
    event_seat_id UUID REFERENCES event_seats(id) ON DELETE RESTRICT,
    quantity INT NOT NULL,
    unit_price DECIMAL(15,2) NOT NULL,
    total_price DECIMAL(15,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_order_item_qty CHECK (quantity > 0),
    CONSTRAINT chk_order_item_price CHECK (unit_price >= 0 AND total_price >= 0)
);

CREATE INDEX idx_order_items_order_id ON order_items(order_id);
CREATE INDEX idx_order_items_ticket_type ON order_items(ticket_type_id);
CREATE INDEX idx_order_items_sale_phase ON order_items(sale_phase_id);
CREATE INDEX idx_order_items_seat_id ON order_items(event_seat_id) WHERE event_seat_id IS NOT NULL;
