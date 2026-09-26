# Gửi vé, QR và hóa đơn khi nhiều người mua cùng lúc

## Luồng hiện tại

Khi VNPay callback hợp lệ, một transaction PostgreSQL xác nhận đơn, phát hành vé/QR, tạo bản ghi hóa đơn và ghi các sự kiện Outbox. Outbox worker đọc batch `PENDING`, publish sang RabbitMQ, chờ broker confirm rồi mới đánh dấu `PUBLISHED`. Worker dùng `FOR UPDATE SKIP LOCKED` để nhiều instance không cùng lấy một batch. Consumer gửi email vé và email PDF hóa đơn ở bước sau.

Email vé nhúng PNG bằng `cid:ticket-qr` và đính kèm cùng file PNG. Nhiều email client không hỗ trợ ảnh `data:` URL trong HTML; Content-ID và file đính kèm giải quyết việc QR không hiển thị. QR trong email là bản chụp tại lúc phát hành: nếu vé được chuyển hoặc đổi QR sau đó, người sở hữu cần lấy QR hiện hành từ API `/api/v1/tickets/my-tickets`.

PDF đi kèm email thanh toán hiện là chứng từ xác nhận đơn hàng do hệ thống tự sinh, chưa phải hóa đơn thuế có ký số.

## Ước lượng cho 100 giao dịch đồng thời

Nếu 100 người mua mỗi người một vé, hệ thống tạo khoảng 100 email vé và 100 email hóa đơn; còn có 100 sự kiện `ORDER_PAID` chỉ ghi log. Nếu mỗi đơn có ba vé thì có khoảng 300 QR/email vé nhưng vẫn chỉ 100 hóa đơn/email hóa đơn. RabbitMQ giữ các message trong queue và phân phối cho consumer. Tốc độ nhận email phụ thuộc chủ yếu vào SMTP, số consumer, giới hạn của nhà cung cấp mail, tốc độ tạo PDF và kích thước backlog.

Ví dụ **chỉ để tính sức chứa**: 100 email trong một queue, hai consumer, mỗi lần gửi mất 1 giây thì riêng queue đó cần xấp xỉ 50 giây. Bốn consumer có thể giảm về khoảng 25 giây nếu SMTP cho phép và không có retry. Đây không phải kết quả đo trên hệ thống này. Muốn cam kết latency phải đo p50/p95/p99 và backlog trên môi trường demo/staging.

`APP_NOTIFICATION_CONSUMERS`, `APP_NOTIFICATION_MAX_CONSUMERS` và `APP_NOTIFICATION_PREFETCH` điều chỉnh song song qua Spring AMQP. Mặc định hiện tại là **1, 2 và 5 cho mỗi queue**. Consumer email hiện giữ transaction PostgreSQL trong lúc gửi SMTP để khóa idempotency; nếu cùng lúc có 2 consumer trên mỗi queue mail và consumer `ORDER_PAID`, một phần pool kết nối sẽ bị chiếm trong thời gian chờ SMTP. Tăng từng bước sau khi đo pool, callback latency, lỗi SMTP `429`/quota, thời gian gửi và DLQ. Tăng consumer quá mức có thể làm email đến chậm hơn hoặc làm callback thanh toán phải chờ kết nối database.

## Consistency và xử lý lỗi

1. Vé và hóa đơn là dữ liệu trong PostgreSQL. Sau khi callback commit, người mua có thể tra cứu chúng qua API kể cả khi email còn trong queue.
2. Outbox nằm cùng transaction nghiệp vụ. Nếu transaction rollback, event không được gửi. Nếu publish được broker xác nhận và route tới queue, worker đánh dấu `PUBLISHED`.
3. Consumer vé ghi `eventId` vào `notification_inbox` cùng transaction gửi mail. Message đã xử lý thành công sẽ được bỏ qua khi RabbitMQ phát lại. Consumer hóa đơn khóa bản ghi `invoice_deliveries` theo `deliveryId`, bỏ qua trạng thái `SENT` và cho phép retry trạng thái `FAILED`.
4. SMTP và PostgreSQL không có transaction chung. Nếu SMTP đã nhận thư nhưng process chết trước khi DB commit, email có thể được gửi lại. Hệ thống cung cấp **at-least-once**, không cam kết exactly-once. Mã vé/QR vẫn chỉ là một vé và check-in chống dùng lại tại database.
5. Mail bị tắt hoặc thiếu sender sẽ ném lỗi thay vì báo gửi thành công. RabbitMQ retry tối đa ba lần rồi chuyển sang DLQ. Outbox lỗi publish thử lại tối đa năm lần và có API admin retry; DLQ chưa tự động phát lại.

## Thông báo về hồ sơ hoàn tiền

Hồ sơ `REQUIRED` là việc cần xử lý nội bộ: Admin thấy trong trang `/admin/refund-reviews`. Đổi sang `IN_REVIEW` chỉ là trạng thái xem xét, không phải bằng chứng đã chuyển tiền. Chỉ trạng thái `REFUNDED_CONFIRMED` có mã giao dịch/bằng chứng đối chiếu mới được diễn đạt là đã hoàn; `CLOSED_NO_REFUND` cần lý do rõ ràng. Không đưa QR, secret, hash VNPay hoặc toàn bộ dữ liệu thanh toán vào thông báo.

Hiện tại hệ thống lưu trạng thái và lịch sử cho Admin, **chưa tự động báo cho khách khi trạng thái hồ sơ thay đổi**. Nếu bổ sung, nên ghi sự kiện `REFUND_REVIEW_UPDATED` vào Outbox trong cùng transaction cập nhật, gửi email/in-app sau commit, chống gửi lặp bằng event ID; chỉ thông báo quyết định cuối cùng sau khi Admin đã xác minh. Thất bại gửi thông báo không được đảo ngược quyết định đã lưu.

## Kiểm tra khi demo tải đồng thời

- Chạy 100 request thanh toán hợp lệ trên dữ liệu test, với callback có chữ ký. Không bắn 100 callback giả vào database nghiệp vụ.
- Đo thời gian callback, số order `PAID`, ticket, invoice và Outbox tương ứng. Kiểm tra không có vé/đơn trùng.
- Theo dõi độ dài các queue `ticket.issued.queue`, `invoice.created.queue`, DLQ và Outbox `PENDING`/`FAILED` mỗi 5 giây.
- Đo thời gian từ `outbox_events.created_at` đến `published_at`, và từ tạo `invoice_deliveries` đến `sent_at`.
- Kiểm tra email thật trên nhiều client (Gmail web/mobile, Outlook), cả ảnh QR nội tuyến và tệp PNG đính kèm.
- Thử tắt RabbitMQ và SMTP riêng rẽ rồi bật lại; xác nhận không mất vé/hóa đơn, không đánh dấu `SENT` khi SMTP không gửi.

Tham khảo: [RabbitMQ acknowledgements và publisher confirms](https://www.rabbitmq.com/docs/confirms), [Spring AMQP concurrency](https://docs.spring.io/spring-amqp/reference/amqp/listener-concurrency.html), [Spring Framework inline images](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/mail/javamail/MimeMessageHelper.html).
