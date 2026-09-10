ALTER TABLE events ADD COLUMN cancellation_reason TEXT;

CREATE TABLE payment_refund_reviews (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL UNIQUE REFERENCES payments(id),
    order_id UUID NOT NULL REFERENCES orders(id),
    amount NUMERIC(15,2) NOT NULL CHECK (amount >= 0),
    reason VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'REQUIRED',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_refund_reviews_status ON payment_refund_reviews(status, created_at);

-- Preserve obligations already identified by the previous late-payment handler.
INSERT INTO payment_refund_reviews (id, payment_id, order_id, amount, reason, status, created_at, updated_at)
SELECT gen_random_uuid(), p.id, p.order_id, p.amount, 'LATE_PAYMENT_EXPIRED', 'REQUIRED', NOW(), NOW()
FROM payments p JOIN orders o ON o.id = p.order_id
WHERE p.status = 'SUCCESS' AND o.status = 'CANCELLED' AND o.customer_note LIKE 'LATE_PAYMENT_EXPIRED:%'
ON CONFLICT (payment_id) DO NOTHING;
