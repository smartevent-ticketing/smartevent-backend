-- VNPay HMAC-SHA512 signatures contain 128 hexadecimal characters.
-- Preserve existing signatures while allowing verified callbacks to commit.
ALTER TABLE payment_webhook_events
    ALTER COLUMN payload_hash TYPE VARCHAR(128);
