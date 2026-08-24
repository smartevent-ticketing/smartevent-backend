# Trạng thái Phase 1

**Mốc đánh giá:** 23/08/2026  
**Phạm vi:** đồ án môn học, Phase 1 – Core Ticketing  
**Kết luận:** hoàn thành Phase 1 ở mức **9,2/10**; đủ điều kiện đóng phase và chuyển sang hoàn thiện demo/báo cáo.

## Tiêu chí đánh giá

Đặc tả gốc yêu cầu Phase 1 có thể tạo sự kiện và bán vé end-to-end mà không bán trùng, gồm auth/RBAC, venue/event/category, khu vực và ghế, loại vé/đợt bán, giữ chỗ/hết hạn, order/payment sandbox, invoice PDF/email, ticket QR/check-in, MinIO, Flyway và OpenAPI.

Đánh giá chỉ ghi nhận phần có bằng chứng trong mã nguồn, cấu hình, migration hoặc test; không xem nội dung roadmap là chức năng đã hoàn thành.

## Ma trận hoàn thành

| Hạng mục Phase 1 | Trạng thái | Bằng chứng chính | Ghi chú |
|---|---|---|---|
| Auth, JWT, RBAC | Hoàn thành | `SecurityConfig`, identity module | `/auth/me` cần JWT; route public được giới hạn |
| Category, venue, event | Hoàn thành | event module, controller/service test | Truy vấn công khai theo ID/slug chỉ cho `PUBLISHED` |
| STANDING/SEATED, seat map | Hoàn thành | event area/seat module | Ghế có state machine và atomic update |
| Ticket type, sale phase, inventory | Hoàn thành | ticketing module | Có counter/quota và validation nghiệp vụ |
| Reservation hold/expiry | Hoàn thành | reservation module, scheduler | Lease 10 phút; worker 30 giây; CAS chống tranh chấp confirm/expire |
| Order và price snapshot | Hoàn thành | ordering module | Số tiền do server tính, lưu snapshot tại order item |
| Payment sandbox/IPN | Hoàn thành cho luồng Phase 1 | payment module | VNPay IPN là luồng xác nhận chính; late payment được ghi nhận để đối soát |
| Ticket/QR/check-in | Hoàn thành | ticket module | Check-in `ISSUED → USED` bằng conditional update nguyên tử |
| Invoice PDF/email | Hoàn thành | invoice + notification module | PDF OpenPDF; resend gắn đúng `deliveryId` |
| Outbox/RabbitMQ | Hoàn thành ở mức đồ án | outbox worker, RabbitMQ config | Retry consumer 3 lần và DLQ; semantics at-least-once |
| MinIO upload/download | Hoàn thành ở mức Phase 1 | storage module | Giới hạn 10 MB, allow-list extension/MIME, chặn SVG |
| Flyway/OpenAPI | Hoàn thành | `V1`–`V12`, Springdoc | Swagger UI được bật |

## Các tình huống cạnh tranh đã xử lý

### Hai người giữ cùng một ghế

`UPDATE ... WHERE status = AVAILABLE` đảm bảo chỉ một transaction đổi ghế sang `HELD`. Luồng còn lại nhận `0` row và bị từ chối.

### Payment callback đua với expiry worker

Hai nhánh cùng dùng CAS trên reservation:

- payment: `PENDING → CONFIRMED`;
- worker: `PENDING → EXPIRED`.

Chỉ một nhánh thắng. Nếu payment đến muộn sau khi reservation hết hạn, hệ thống vẫn ghi `Payment.SUCCESS`, chuyển order sang `CANCELLED`, thêm note `LATE_PAYMENT_EXPIRED` và trả mã xác nhận cho VNPay để dừng retry. Đây là **đối soát hoàn tiền thủ công**, chưa phải refund tự động.

### Hai máy quét cùng một QR

`UPDATE ... WHERE status = ISSUED` chuyển vé sang `USED`. Một máy nhận thành công; máy còn lại nhận `0` row và trả kết quả `DUPLICATE`.

## Bằng chứng kiểm thử

Báo cáo Gradle gần nhất trong `ticketing/build/test-results/test`:

- 146 test;
- 0 failure;
- 0 error;
- 0 skipped;
- 25 test suite report.

Các test đáng chú ý bao phủ reservation concurrency, late payment, DRAFT event access, check-in duplicate, invoice delivery, SMTP failure và notification consumer.

Test pass chứng minh regression suite hiện tại đạt yêu cầu; nó không tự động chứng minh hiệu năng, độ bền broker, khả năng phục hồi hạ tầng hay bảo mật production. Xem [chiến lược kiểm thử](../03-quality/test-strategy.md).

## Phạm vi không tuyên bố

Phase 1 hiện tại không được mô tả là production-ready. Chưa có đủ bằng chứng cho:

- load/stress test có số liệu p95/p99;
- Testcontainers hoặc integration test độc lập hạ tầng cục bộ;
- publisher confirms và consumer idempotency trên mọi loại event;
- refund tự động và đối soát tài chính hoàn chỉnh;
- kiểm tra magic bytes/antivirus cho file upload;
- rate limiting, secret manager, CI/CD, backup/restore drill và observability hoàn chỉnh.

Danh sách ưu tiên nằm tại [Known limitations](../03-quality/known-limitations.md).

## Quyết định đóng Phase 1

Với bối cảnh môn học, phần cốt lõi khó nhất — state transition, race condition, late payment và async delivery — đã được xử lý có chủ đích và có test. Nên đóng Phase 1, đóng băng phạm vi chức năng, sau đó dành thời gian cho integration/load test, demo script, báo cáo và các rủi ro P1 còn lại thay vì mở thêm module mới.
