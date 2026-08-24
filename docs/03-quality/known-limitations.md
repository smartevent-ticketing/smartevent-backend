# Giới hạn và backlog kỹ thuật

Danh sách này cố ý công tâm: Phase 1 đã hoàn thành ở mức đồ án nhưng vẫn còn khoảng cách rõ ràng tới production. Ưu tiên được đánh theo tác động, không phải để phủ nhận kết quả hiện có.

## P1 — Nên xử lý trước khi gọi là production-ready

### 1. Publisher confirm và claim outbox

**Hiện trạng:** worker đọc tối đa 50 event `PENDING`, gọi publish rồi đánh dấu `PUBLISHED`. Chưa có publisher confirms và chưa có cơ chế claim/lock batch cho nhiều app instance.

**Rủi ro:** mất/nhân đôi message ở vùng lỗi mơ hồ; hai worker có thể cùng publish một event.

**Nâng cấp:** Rabbit publisher confirms/returns, trạng thái `PROCESSING`, `SELECT ... FOR UPDATE SKIP LOCKED` hoặc lease/claim token, retry policy có backoff và metric tuổi event.

### 2. Consumer idempotency end-to-end

**Hiện trạng:** invoice có `deliveryId`, nhưng email/ticket handler chưa có inbox/deduplication chung. SMTP không cung cấp transaction chung với DB.

**Rủi ro:** email trùng khi message redelivery hoặc khi SMTP thành công nhưng cập nhật DB thất bại.

**Nâng cấp:** inbox table với `eventId` unique, idempotency key phía provider nếu hỗ trợ, handler transaction rõ ràng và reconciliation.

### 3. Refund và reconciliation tự động

**Hiện trạng:** late payment được lưu an toàn và đưa order sang `CANCELLED` với note.

**Rủi ro:** hoàn tiền phụ thuộc thao tác thủ công; thiếu SLA, audit và cảnh báo.

**Nâng cấp:** refund state machine (`REQUESTED/PROCESSING/SUCCEEDED/FAILED`), admin queue, provider API, webhook refund và daily reconciliation report.

### 4. File validation thực sự

**Hiện trạng:** kiểm tra kích thước, extension, `MultipartFile.getContentType`, chặn SVG và sanitize folder; file được đánh `CLEAN` ngay.

**Rủi ro:** MIME header do client cung cấp; file giả mạo extension vẫn có thể lọt qua; chưa quét malware.

**Nâng cấp:** kiểm tra magic bytes bằng Apache Tika hoặc parser ảnh/PDF, re-encode ảnh, quarantine bucket, antivirus scan và chỉ chuyển `CLEAN` sau khi scan thành công.

### 5. Secret/config hygiene

**Hiện trạng:** một số default sandbox/JWT credential nằm trong YAML; test profile có default database password cụ thể.

**Rủi ro:** cấu hình mẫu bị dùng nhầm ở môi trường thật hoặc lộ credential cá nhân.

**Nâng cấp:** xóa secret mặc định khỏi profile non-local, dùng secret manager/CI secret, rotate mọi key từng được dùng thật, secret scanning trong CI.

## P2 — Nên xử lý để tăng chất lượng đồ án

### 6. Integration concurrency test

Test 50 thread hiện mock repository bằng `AtomicBoolean`, chưa chạy 50 transaction thật trên PostgreSQL. Bổ sung Testcontainers test cho seat CAS, reservation confirm-vs-expire và check-in duplicate.

### 7. Test environment độc lập

Suite phụ thuộc PostgreSQL local; RabbitMQ/Redis/MinIO coverage chưa phản ánh đầy đủ hạ tầng thật. Dùng Testcontainers và tách unit/integration task để chạy ổn định trong CI.

### 8. Scheduler khi scale nhiều instance

Reservation/order/outbox workers có thể chạy trên mọi instance. Conditional update giữ an toàn state ở nhiều chỗ nhưng vẫn gây query/công việc trùng. Dùng ShedLock, database claim hoặc single worker deployment; phân trang/batch để tránh quét danh sách lớn.

### 9. Security hardening

Chưa có rate limiting, explicit CORS allow-list theo môi trường, giới hạn login/IPN abuse, security headers/profile production và chính sách tắt hoặc bảo vệ Swagger. Thêm audit cho hành động nhạy cảm và tránh log toàn bộ payment params.

### 10. Observability

Actuator health/info có sẵn; `prometheus` được exposure nhưng chưa có registry/dashboards/alerts đầy đủ. Cần Micrometer registry, structured log, correlation ID, metric cho outbox/DLQ/late payment, tracing và alert threshold.

### 11. MinIO–PostgreSQL consistency

Ghi object và metadata không cùng transaction. Cần checksum, orphan cleanup, retry/compensation và job đối soát object thiếu/thừa.

### 12. Docker/CI/CD

Compose hiện chỉ chạy hạ tầng, chưa đóng gói application. Cần Dockerfile multi-stage, healthcheck, CI build/test/security scan, migration gate và staging deployment. Backup/restore phải có thử nghiệm khôi phục, không chỉ có lịch backup.

### 13. Redis configuration/reconciliation

Main YAML đang cố định Redis host `localhost`; không phù hợp khi app chạy trong container. Đưa host/port vào environment và định nghĩa cách rebuild counter từ PostgreSQL.

## P3 — Cải thiện tài liệu và maintainability

- Sinh OpenAPI snapshot trong CI và kiểm tra breaking change.
- Chuẩn hóa package/module dependency bằng ArchUnit.
- Loại bỏ fallback cập nhật nhiều invoice delivery sau khi payload cũ hết vòng đời.
- Thêm ADR riêng khi triển khai refund, inbox/dedup hoặc multi-instance worker.
- Dần chuyển tài liệu module cũ sang tên thư mục/filename không có khoảng trắng; chỉ thực hiện kèm link checker để tránh gãy liên kết.

## Thứ tự đề xuất

1. Testcontainers concurrency test + RabbitMQ integration test.
2. Secret cleanup và magic-byte/file scanning.
3. Publisher confirms + outbox claim + inbox dedup.
4. Late-payment alert/refund workflow.
5. Load test có số liệu, observability và CI/CD.

Với đồ án môn học, hoàn thành hai bước đầu đã tăng đáng kể độ tin cậy khi demo và phản biện; không cần mở thêm chức năng Phase 2 trước khi các bằng chứng này ổn định.
