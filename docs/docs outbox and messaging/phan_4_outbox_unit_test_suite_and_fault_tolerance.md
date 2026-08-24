# 🧪 MODULE 9 - PHẦN 4: BỘ UNIT TEST SUITE & KHẢ NĂNG CHỊU LỖI HẠ TẦNG
## (OUTBOX UNIT TESTING, FAULT TOLERANCE & RESILIENCE VALIDATION)

**Trạng thái:** Hoàn thành 100% · 100% Tests Pass Xanh  
**Tác giả / Hệ thống:** Backend Engineering Team · Smart Event Ticketing Platform  

---

## 🧪 1. Ma Trận Kiểm Thử Tự Động Module 9

| STT | File Test | Tên Test Case | Mục Đích Nghiệp Vụ | Kết Quả Kỳ Vọng |
|:---:|---|---|---|:---:|
| **1** | `OutboxServiceTest` | `publishEvent_Success` | Ghi nhận sự kiện nghiệp vụ vào DB trong cùng Transaction | Lưu bản ghi `OutboxEvent` với `status = PENDING` và chuỗi JSON chuẩn. |
| **2** | `OutboxPublisherWorkerTest` | `publishPendingEvents_Success` | Worker quét và đẩy sự kiện sang RabbitMQ khi Broker hoạt động bình thường | Gọi `IntegrationEventPublisher`, đổi trạng thái sang `PUBLISHED`. |
| **3** | `OutboxPublisherWorkerTest` | `publishPendingEvents_Failure_IncrementsRetry` | Xử lý sự cố khi RabbitMQ bị sập mạng | Bắt Exception, không làm crash Worker, tăng `retryCount` và lưu `lastError`. |

---

## 🛡️ 2. Cơ Chế Tự Phục Hồi Khi Gặp Sự Cố (Fault Tolerance):
1. **Nếu RabbitMQ bị sập trong 30 phút:**
   * Mọi giao dịch thanh toán mua vé của khách **vẫn diễn ra bình thường 100%**!
   * Các sự kiện được tích tụ an toàn trong bảng `outbox_events` với trạng thái `PENDING`.
   * Ngay khi RabbitMQ khởi động lại, `OutboxPublisherWorker` sẽ tự động quét và đẩy toàn bộ các sự kiện tồn đọng sang RabbitMQ, không làm mất bất kỳ một email hay thông báo nào!
