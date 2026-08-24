# 🐇 MODULE 9 - PHẦN 2: CẤU TRÚC MẠNG LƯỚI ĐỊNH TUYẾN RABBITMQ TOPOLOGY
## (TOPIC EXCHANGE, QUEUES, ROUTING KEYS & DEAD LETTER EXCHANGES)

**Trạng thái:** Hoàn thành 100% · Production Ready  
**Tác giả / Hệ thống:** Backend Engineering Team · Smart Event Ticketing Platform  

---

## 🧭 1. Sơ Đồ Định Tuyến Thông Điệp (Routing Architecture)

Hệ thống sử dụng **Topic Exchange** trung tâm kết hợp **Dead Letter Exchange (DLX)** để cô lập thông điệp lỗi:

```
                                [ TOPIC EXCHANGE: smartevent.topic.exchange ]
                                                    │
                 ┌──────────────────────────────────┼──────────────────────────────────┐
                 │ (Routing Key:                    │ (Routing Key:                    │ (Routing Key:
                 │  event.ticket.issued)            │  event.invoice.created)          │  event.order.paid)
                 ▼                                  ▼                                  ▼
      [ ticket.issued.queue ]            [ invoice.created.queue ]            [ order.paid.queue ]
                 │                                  │                                  │
                 ▼ (Lỗi xử lý)                      ▼ (Lỗi xử lý)                      ▼
    ┌────────────────────────────────────────────────────────────────────────────────────────┐
    │                DEAD LETTER EXCHANGE: smartevent.dlx.exchange                           │
    │                ROUTING KEY: event.dead.letter                                          │
    │                QUEUE ĐÍCH: smartevent.dead.letter.queue (DLQ)                          │
    └────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 🗃️ 2. Bảng Danh Mục Queues & Routing Keys

| Tên Hàng Đợi (Queue) | Exchange Gắn Vào | Routing Key | Nhiệm Vụ Nghiệp Vụ |
|---|---|---|---|
| `ticket.issued.queue` | `smartevent.topic.exchange` | `event.ticket.issued` | Nhận thông điệp vé phát hành $\rightarrow$ Gửi email kèm mã QR vào cửa |
| `invoice.created.queue` | `smartevent.topic.exchange` | `event.invoice.created` | Nhận thông điệp hóa đơn $\rightarrow$ Gửi email hóa đơn tài chính VAT |
| `order.paid.queue` | `smartevent.topic.exchange` | `event.order.paid` | Nhận thông điệp thanh toán $\rightarrow$ Ghi nhận kiểm toán tài chính |
| `smartevent.dead.letter.queue` | `smartevent.dlx.exchange` | `event.dead.letter` | Chứa các thông điệp bị lỗi (Retry quá số lần quy định) |
