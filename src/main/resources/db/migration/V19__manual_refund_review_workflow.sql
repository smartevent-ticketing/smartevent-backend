ALTER TABLE payment_refund_reviews
    ADD COLUMN resolution_note TEXT,
    ADD COLUMN evidence_reference VARCHAR(200),
    ADD COLUMN updated_by_user_id UUID REFERENCES users(id),
    ADD COLUMN resolved_at TIMESTAMPTZ;

ALTER TABLE payment_refund_reviews
    ADD CONSTRAINT chk_refund_review_status
    CHECK (status IN ('REQUIRED', 'IN_REVIEW', 'REFUNDED_CONFIRMED', 'CLOSED_NO_REFUND'));

CREATE TABLE payment_refund_review_actions (
    id UUID PRIMARY KEY,
    review_id UUID NOT NULL REFERENCES payment_refund_reviews(id),
    previous_status VARCHAR(30) NOT NULL,
    new_status VARCHAR(30) NOT NULL,
    note TEXT NOT NULL,
    evidence_reference VARCHAR(200),
    admin_user_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_refund_review_actions_review_time
    ON payment_refund_review_actions(review_id, created_at);
