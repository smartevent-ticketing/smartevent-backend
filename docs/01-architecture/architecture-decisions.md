# Architecture Decision Records

File này ghi các quyết định kiến trúc đang có hiệu lực. Mỗi quyết định nêu bối cảnh, lựa chọn và hệ quả để tránh tài liệu chỉ mô tả “code đang làm gì” mà không giải thích “vì sao”.

## ADR-001 — Modular monolith cho Phase 1

**Trạng thái:** Accepted

**Quyết định:** triển khai một Spring Boot application, chia package theo domain/module, dùng transaction cục bộ trên PostgreSQL.

**Lý do:** phù hợp nhóm nhỏ và thời gian môn học; giảm chi phí deploy, tracing và distributed transaction; vẫn tạo ranh giới để tách worker/service về sau.

**Hệ quả:** module phải tránh phụ thuộc vòng; việc scale hiện theo toàn application; không gọi nội bộ qua HTTP chỉ để giả lập microservice.

## ADR-002 — PostgreSQL là nguồn dữ liệu chuẩn

**Trạng thái:** Accepted

**Quyết định:** trạng thái seat, reservation, order, payment, ticket và invoice được quyết định ở PostgreSQL. Redis chỉ giữ dữ liệu dẫn xuất/counter/cache.

**Lý do:** các bất biến tài chính và trạng thái cần ACID, unique constraint và conditional update.

**Hệ quả:** Redis mất dữ liệu không được làm mất sự thật nghiệp vụ; cần cơ chế rebuild/reconcile counter nếu mở rộng sử dụng Redis.

## ADR-003 — Conditional update thay cho read-then-write

**Trạng thái:** Accepted

**Quyết định:** dùng CAS dạng `UPDATE ... WHERE current_status = expected_status` cho giữ ghế, confirm/expire reservation và check-in.

**Lý do:** hai request có thể cùng đọc một trạng thái cũ; chỉ database có thể phân xử nguyên tử tại row được cập nhật.

**Hệ quả:** `affectedRows == 0` là một kết quả nghiệp vụ bình thường, không mặc định là lỗi hệ thống; test phải bao phủ hai luồng tranh chấp.

## ADR-004 — Time-bound lease 10 phút với sweeper

**Trạng thái:** Accepted

**Quyết định:** reservation `PENDING` có `expiresAt`; scheduler quét mỗi 30 giây và CAS sang `EXPIRED` trước khi nhả tài nguyên.

**Lý do:** không giữ ghế vô hạn khi khách rời checkout; worker đơn giản và phù hợp Phase 1.

**Hệ quả:** expiry có độ trễ tối đa xấp xỉ chu kỳ quét; khi chạy nhiều application instance cần chiến lược claim/batch/locking rõ hơn để giảm công việc trùng.

## ADR-005 — Transactional Outbox với at-least-once delivery

**Trạng thái:** Accepted

**Quyết định:** lưu event cùng transaction nghiệp vụ, worker publish sang RabbitMQ sau commit; consumer dùng retry hữu hạn và DLQ.

**Lý do:** tránh dual-write trực tiếp giữa PostgreSQL và broker.

**Hệ quả:** consumer phải idempotent; cần publisher confirms và cơ chế claim event nếu muốn nâng cấp độ tin cậy. Không gọi đây là exactly-once end-to-end.

## ADR-006 — Late payment là compensating flow

**Trạng thái:** Accepted

**Quyết định:** nếu VNPay xác nhận thành công sau khi reservation đã hết hạn, vẫn lưu `Payment.SUCCESS`, hủy order, ghi note đối soát và xác nhận webhook.

**Lý do:** sự thật “ngân hàng đã trừ tiền” không được rollback chỉ vì ghế không còn hợp lệ; gateway không nên retry vô hạn.

**Hệ quả:** phát sinh nghĩa vụ hoàn tiền; Phase 1 xử lý thủ công. Phase sau cần refund state machine, audit trail, alert và SLA.

## ADR-007 — Một `deliveryId` cho mỗi lượt gửi invoice

**Trạng thái:** Accepted

**Quyết định:** mỗi yêu cầu gửi/resend tạo một `InvoiceDelivery`; event mang đúng ID đó và consumer chỉ cập nhật record tương ứng.

**Lý do:** tránh một event đánh dấu hàng loạt delivery `PENDING` thành `SENT/FAILED`.

**Hệ quả:** `deliveryId` phải là trường bắt buộc cho event mới; nhánh fallback chỉ để tương thích payload cũ và nên được loại bỏ sau migration.

## ADR-008 — PDF/QR được sinh theo yêu cầu

**Trạng thái:** Accepted cho Phase 1

**Quyết định:** OpenPDF sinh hóa đơn; ZXing sinh QR trong memory thay vì lưu một file QR cho mỗi vé.

**Lý do:** giảm object/file không cần thiết và đơn giản hóa demo.

**Hệ quả:** CPU/memory tăng theo lượng request/consumer; production cần benchmark, cache có thời hạn hoặc pre-generation nếu lưu lượng lớn.

## Cách thêm ADR

Khi thay đổi data ownership, transaction boundary, delivery semantics, state machine hoặc công nghệ hạ tầng, thêm ADR mới thay vì sửa lịch sử quyết định cũ. Quyết định bị thay thế phải ghi `Superseded by ADR-xxx`.
