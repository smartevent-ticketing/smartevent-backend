-- Do not silently discard historical orders or financial data if duplicates need reconciliation.
DO $$
BEGIN
    IF EXISTS (SELECT reservation_id FROM orders WHERE reservation_id IS NOT NULL
               GROUP BY reservation_id HAVING COUNT(*) > 1) THEN
        RAISE EXCEPTION 'Duplicate orders for a reservation: reconcile existing orders before applying V15';
    END IF;
END $$;

CREATE UNIQUE INDEX uq_orders_reservation ON orders(reservation_id) WHERE reservation_id IS NOT NULL;

-- Retain the latest active QR and revoke older credentials from previous concurrent rotations.
WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY ticket_id ORDER BY issued_at DESC, id DESC) AS position
    FROM ticket_qr_tokens WHERE status = 'ACTIVE'
)
UPDATE ticket_qr_tokens q SET status = 'REVOKED', revoked_at = NOW()
FROM ranked r WHERE q.id = r.id AND r.position > 1;

CREATE UNIQUE INDEX uq_ticket_active_qr ON ticket_qr_tokens(ticket_id) WHERE status = 'ACTIVE';
