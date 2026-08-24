# Reliability runbook

Runbook này dành cho demo/local/staging. Nó mô tả cách nhận biết và xử lý các lỗi ảnh hưởng tới reservation, payment, outbox và email mà không thay đổi dữ liệu một cách mù quáng.

## Các job nền

| Job | Chu kỳ | Input | Kết quả |
|---|---:|---|---|
| `ReservationExpiryWorker` | 30 giây, fixed rate | Reservation `PENDING` đã quá `expiresAt` | CAS sang `EXPIRED`, nhả seat/inventory |
| `OrderExpiryWorker` | 30 giây, fixed delay | Order `PENDING_PAYMENT` quá deadline | Expire/cancel order theo service |
| `OutboxPublisherWorker` | 5 giây, fixed delay | Tối đa 50 outbox `PENDING` cũ nhất | Publish RabbitMQ rồi đánh dấu `PUBLISHED`; lỗi thành `FAILED` |

`@EnableScheduling` được bật tại application entry point. Health endpoint `UP` chưa chứng minh từng scheduler đang chạy; cần kiểm tra log và độ tuổi của record `PENDING`.

## Health và tín hiệu cần quan sát

### Endpoint

- `/actuator/health`: tình trạng application và dependency được contributor hỗ trợ.
- `/api/v1/admin/outbox/stats`: số lượng theo trạng thái.
- `/api/v1/admin/outbox/pending`: event chờ publish.
- `/api/v1/admin/outbox/failed`: event publish lỗi.

### RabbitMQ

Theo dõi:

- message ready/unacked của `ticket.issued.queue`, `invoice.created.queue`, `order.paid.queue`;
- message trong `smartevent.dead.letter.queue`;
- consumer count và tốc độ tăng backlog.

Prometheus được khai báo trong danh sách endpoint exposure nhưng project chưa có registry Prometheus riêng; không xem đây là monitoring hoàn chỉnh.

## Playbook 1 — Outbox `PENDING` tăng liên tục

1. Kiểm tra application log để xác nhận worker có chạy mỗi 5 giây.
2. Kiểm tra RabbitMQ host/port/credential và exchange `smartevent.topic.exchange`.
3. Kiểm tra event type có mapping routing key hợp lệ.
4. Sau khi sửa nguyên nhân, theo dõi backlog giảm dần theo batch 50.
5. Nếu event đã thành `FAILED`, dùng endpoint admin retry cho từng event; không sửa trực tiếp status trong database khi chưa đọc error.

## Playbook 2 — Message vào DLQ

1. Không xóa message ngay; lưu payload, routing key, exception và thời điểm.
2. Xác định lỗi transient (SMTP/network) hay permanent (payload sai, entity không tồn tại).
3. Sửa nguyên nhân và kiểm tra handler có an toàn khi chạy lại không.
4. Replay message có kiểm soát qua RabbitMQ UI/tool vận hành.
5. Đối chiếu `InvoiceDelivery` sau replay.

Code hiện chưa có API redrive DLQ tự động. DLQ là nơi cách ly để điều tra, không phải bằng chứng lỗi đã tự được xử lý.

## Playbook 3 — Email invoice thất bại

1. Tìm đúng `InvoiceDelivery` bằng `deliveryId` trong event.
2. Kiểm tra trạng thái `FAILED` và error rút gọn trong `providerMessageId`.
3. Kiểm tra SMTP auth/TLS/timeout và khả năng sinh PDF.
4. Consumer retry tối đa 3 lần; sau đó message đi DLQ.
5. Khi dịch vụ ổn định, dùng chức năng resend để tạo **delivery mới**, không tái sử dụng record cũ.

Email có thể bị gửi trùng nếu SMTP đã nhận request nhưng consumer lỗi trước khi commit trạng thái `SENT`. Khi xử lý khiếu nại, tra cả delivery history và hộp thư nhà cung cấp.

## Playbook 4 — Late payment

Dấu hiệu: `Payment.SUCCESS`, `Order.CANCELLED`, note bắt đầu bằng `LATE_PAYMENT_EXPIRED`.

1. Đối chiếu transaction number, amount, order và thời gian callback với VNPay.
2. Xác nhận reservation đã `EXPIRED/CANCELLED` và không có ticket được phát hành cho order đó.
3. Tạo case hoàn tiền thủ công theo quy trình kế toán.
4. Ghi mã hoàn tiền, người xử lý, thời điểm và kết quả vào biên bản/audit ngoài hệ thống nếu schema hiện tại chưa hỗ trợ.
5. Thông báo khách hàng; không tự đổi order sang `PAID` nếu ghế đã được bán cho người khác.

Phase 1 chưa tự gọi refund API. Note đối soát chỉ giúp không làm mất giao dịch, không hoàn thành nghĩa vụ hoàn tiền.

## Playbook 5 — Reservation/seat bất nhất

1. Dừng thao tác thủ công trên bản ghi liên quan.
2. Thu thập reservation status, `expiresAt`, seat status, order/payment và worker log.
3. Kiểm tra nhánh CAS nào thắng; không suy luận chỉ từ timestamp ứng dụng.
4. Nếu payment thành công, ưu tiên sự thật tài chính và áp dụng late-payment/reconciliation flow.
5. Chỉ sửa dữ liệu bằng script có review và lưu audit; Phase 1 chưa cung cấp repair command chuẩn.

## Playbook 6 — MinIO và metadata lệch nhau

Upload/delete chạm cả MinIO và PostgreSQL nhưng không có distributed transaction. Có thể xuất hiện orphan object hoặc metadata trỏ tới object thiếu.

1. So sánh bucket/objectName với record file.
2. Không xóa hàng loạt bằng prefix chưa xác minh.
3. Khôi phục object hoặc metadata theo nguồn có thể tin cậy.
4. Ghi lại incident; backlog cần bổ sung checksum, orphan cleanup và reconciliation job.

## Nguyên tắc phục hồi

- Không replay payment IPN hoặc Rabbit message nếu chưa kiểm tra idempotency.
- Không đổi trực tiếp `Payment.SUCCESS` thành trạng thái khác để “cho đẹp dữ liệu”.
- Luôn giữ transaction number và payload gốc cho đối soát.
- Mọi thao tác sửa dữ liệu phải có bản sao/backup và người review.
