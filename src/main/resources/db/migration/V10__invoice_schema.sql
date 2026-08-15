-- V10: Billing & Invoice Schema (invoices, invoice_items, invoice_deliveries)

-- 1. Invoices (Exactly-once issuance per order)
CREATE TABLE invoices (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE REFERENCES orders(id) ON DELETE RESTRICT,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    invoice_code VARCHAR(100) NOT NULL UNIQUE,
    billing_email VARCHAR(255) NOT NULL,
    subtotal DECIMAL(15,2) NOT NULL,
    discount_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    fee_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(15,2) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ISSUED',
    issued_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    file_id UUID REFERENCES files(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_invoices_amounts CHECK (
        subtotal >= 0 AND 
        total_amount >= 0 AND 
        discount_amount >= 0 AND 
        fee_amount >= 0
    )
);

CREATE INDEX idx_invoices_order_id ON invoices(order_id);
CREATE INDEX idx_invoices_user_id ON invoices(user_id);
CREATE INDEX idx_invoices_invoice_code ON invoices(invoice_code);
CREATE INDEX idx_invoices_status ON invoices(status);

-- 2. Invoice Items
CREATE TABLE invoice_items (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    order_item_id UUID REFERENCES order_items(id) ON DELETE SET NULL,
    description VARCHAR(255) NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(15,2) NOT NULL,
    total_price DECIMAL(15,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_invoice_item_qty CHECK (quantity > 0),
    CONSTRAINT chk_invoice_item_price CHECK (unit_price >= 0 AND total_price >= 0)
);

CREATE INDEX idx_invoice_items_invoice_id ON invoice_items(invoice_id);

-- 3. Invoice Deliveries (Async email delivery tracking)
CREATE TABLE invoice_deliveries (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    channel VARCHAR(30) NOT NULL DEFAULT 'EMAIL',
    recipient_email VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    sent_at TIMESTAMPTZ,
    provider_message_id VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_invoice_deliveries_invoice_id ON invoice_deliveries(invoice_id);
CREATE INDEX idx_invoice_deliveries_status ON invoice_deliveries(status);
