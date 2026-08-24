# 📨 MODULE 9 - PHẦN 1: GIẢI PHÁP TRANSACTIONAL OUTBOX PATTERN & CHỐNG MẤT SỰ KIỆN
## (DUAL-WRITE PROBLEM RESOLUTION & AT-LEAST-ONCE DELIVERY GUARANTEE)

**Trạng thái:** Hoàn thành 100% · Production Ready  
**Tác giả / Hệ thống:** Backend Engineering Team · Smart Event Ticketing Platform  

---

## 📖 1. Từ Điển Thuật Ngữ Kiến Trúc Phân Tán (Distributed Systems Terminology)

| Thuật Ngữ | Tên Tiếng Việt | Giải Thích Bản Chất Kỹ Thuật |
|---|---|---|
| **Dual-Write Problem** | Vấn Nạn Ghi Kép | Xung đột khi một hành vi cần ghi vào cả 2 nơi (Database & Message Broker/Mail). Một trong hai nơi bị lỗi sẽ làm sai lệch dữ liệu toàn hệ thống. |
| **Transactional Outbox Pattern** | Mẫu Thiết Kế Hộp Thư Đi | Kỹ thuật ghi sự kiện nghiệp vụ vào bảng `outbox_events` ngay trong cùng một Transaction Database với dữ liệu chính, sau đó dùng Worker quét gửi sang Message Broker. |
| **At-Least-Once Delivery** | Giao Vận Ít Nhất Một Lần | Đảm bảo mọi tin nhắn đều được chuyển giao đến Message Broker thành công, không bao giờ bị mất tin (Zero Message Loss). |
| **Dead Letter Queue (DLQ)** | Hàng Đợi Thư Chết | Nơi chứa các thông điệp bị lỗi xử lý nhiều lần để không làm tắc nghẽn hàng đợi chính và phục vụ điều tra lỗi. |

---

## 🧭 2. Sơ Đồ Kiến Trúc Luồng Sự Kiện Không Thể Mất Dữ Liệu (Zero-Loss Pipeline)

```
                            [ THANH TOÁN THÀNH CÔNG ]
                                       │
                                       ▼
 ┌────────────────────────────────────────────────────────────────────────────┐
 │  CÙNG 1 TRANSACTION DATABASE POSTGRESQL (ACID 100%):                       │
 │                                                                            │
 │  1. `orders.status = PAID`                                                 │
 │  2. `tickets` được tạo (Mã vé, QR Token)                                   │
 │  3. `invoices` được tạo (Hóa đơn điện tử)                                  │
 │  4. `outbox_events` được lưu (Event: TICKET_ISSUED, INVOICE_CREATED, PENDING)│
 └────────────────────────────────────────────────────────────────────────────┘
                                       │ (Commit 100% an toàn)
                                       ▼
 ┌────────────────────────────────────────────────────────────────────────────┐
 │  TIẾN TRÌNH QUÉT NỀN (`OutboxPublisherWorker` chạy mỗi 5s):                │
 │  • Đọc các event `PENDING`                                                 │
 │  • Đẩy sang RabbitMQ qua `IntegrationEventPublisher`                       │
 │  • Đổi trạng thái sang `PUBLISHED` (Nếu lỗi -> tăng retryCount)            │
 └────────────────────────────────────────────────────────────────────────────┘
```

---

## 🗃️ 3. Lược Đồ Database Flyway V11 (`outbox_events`)

```sql
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload_json JSONB NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ,
    retry_count INT NOT NULL DEFAULT 0,
    last_error TEXT
);

CREATE INDEX idx_outbox_events_status_created ON outbox_events(status, created_at) WHERE status = 'PENDING';
CREATE INDEX idx_outbox_events_aggregate ON outbox_events(aggregate_type, aggregate_id);
CREATE INDEX idx_outbox_events_type ON outbox_events(event_type);
```
