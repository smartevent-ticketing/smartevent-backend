-- V8: Payment & Webhook Schema (payments, payment_webhook_events)

-- 1. Payments
CREATE TABLE payments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    payment_method VARCHAR(30) NOT NULL,
    provider VARCHAR(30) NOT NULL DEFAULT 'VNPAY',
    transaction_id VARCHAR(100),
    amount DECIMAL(15,2) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'VND',
    status VARCHAR(30) NOT NULL DEFAULT 'INITIATED',
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_payments_amount CHECK (amount > 0)
);

CREATE INDEX idx_payments_order_id ON payments(order_id);
CREATE INDEX idx_payments_provider_tx ON payments(provider, transaction_id) WHERE transaction_id IS NOT NULL;
CREATE INDEX idx_payments_status ON payments(status);

-- 2. Payment Webhook Events (Idempotency control)
CREATE TABLE payment_webhook_events (
    id UUID PRIMARY KEY,
    provider VARCHAR(30) NOT NULL,
    provider_event_id VARCHAR(100) NOT NULL,
    transaction_id VARCHAR(100),
    payload_hash VARCHAR(64),
    payload_json JSONB,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL DEFAULT 'RECEIVED',
    UNIQUE (provider, provider_event_id)
);

CREATE INDEX idx_webhook_events_provider_event ON payment_webhook_events(provider, provider_event_id);
CREATE INDEX idx_webhook_events_status ON payment_webhook_events(status);
