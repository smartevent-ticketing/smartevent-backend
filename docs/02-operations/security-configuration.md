# Cấu hình bảo mật: giải thích và cách làm

## Phân biệt cấu hình và secret

`server.port`, số consumer và URL frontend là cấu hình thông thường. Mật khẩu database/SMTP/RabbitMQ/MinIO, `JWT_SECRET`, `VNPAY_HASH_SECRET` là **secret**: ai có chúng có thể truy cập hệ thống hoặc giả mạo chữ ký. Secret chỉ nằm trong môi trường chạy hoặc secret manager, không đưa vào Git, ảnh chụp, frontend hay log. File `.env` của hai repository đã nằm trong `.gitignore`; giá trị `change_me_local` trong `.env.example` là chỗ giữ chỗ, phải đổi trước khi dùng. Base64 chỉ là cách biểu diễn byte, **không phải mã hóa bảo mật**.

Spring Boot nhận giá trị từ biến môi trường theo cú pháp `${NAME}`. Khi chạy bằng Gradle, `.env` không tự được nạp; dùng đoạn PowerShell trong README hoặc Run Configuration của IDE. Trong môi trường triển khai, có thể dùng secret manager hoặc file mount với `spring.config.import=configtree:...` theo [tài liệu Spring Boot](https://docs.spring.io/spring-boot/reference/features/external-config.html).

## Thiết lập local theo thứ tự

1. Sao chép `.env.example` của infra và backend thành `.env`, rồi đặt mật khẩu database, RabbitMQ, MinIO giống nhau ở hai file.
2. Tạo `JWT_SECRET` là **32 byte ngẫu nhiên biểu diễn bằng Base64**, ví dụ trong PowerShell:

   ```powershell
   $bytes = New-Object byte[] 32
   [System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
   [Convert]::ToBase64String($bytes)
   ```

   Sao chép kết quả vào `JWT_SECRET` trong `.env`; không commit hoặc đăng ảnh chứa kết quả. Nếu đang dùng `.env` cũ có giá trị JWT mẫu từng xuất hiện trong tài liệu, hãy thay bằng khóa mới dù giá trị cũ đủ độ dài. Backend từ chối khóa rỗng/ngắn. Nếu thay khóa, JWT đã phát hành sẽ không còn hợp lệ và người dùng phải đăng nhập lại.
3. Điền cặp `VNPAY_TMN_CODE`/`VNPAY_HASH_SECRET` do merchant Sandbox cấp. Nếu để trống, backend từ chối tạo link; callback không được xác nhận.
4. Điền `SPRING_MAIL_USERNAME`/`SPRING_MAIL_PASSWORD` để gửi thư thật. `APP_MAIL_ENABLED=false` dành cho môi trường không gửi mail; khi đó giao thư thất bại có chủ đích và đi qua retry/DLQ, không đánh dấu `SENT`.
5. Đặt `CORS_ALLOWED_ORIGINS` bằng origin frontend thực tế. `APP_DOCS_PUBLIC=true` chỉ dành cho local/demo; mặc định Swagger yêu cầu vai trò Admin. CORS không thay thế xác thực JWT hoặc xác minh chữ ký IPN.

## Khi triển khai ngoài máy cá nhân

- Tạo secret riêng cho từng môi trường và mỗi nhà cung cấp. Không dùng lại khóa từ `.env.example` hoặc test.
- Truy cập backend qua HTTPS; cấu hình Return/IPN VNPay theo hostname thực. Chỉ cho phép frontend origin cần thiết.
- Bảo vệ log và backup vì chúng có thể chứa dữ liệu giao dịch. Controller không còn ghi toàn bộ tham số VNPay ra log; redirect chỉ chuyển các trường hiển thị cần thiết, không chuyển `vnp_SecureHash`.
- Đặt `APP_DOCS_PUBLIC=false`. Hạn chế truy cập RabbitMQ Management, MinIO Console và PostgreSQL; infra local đang bind vào `127.0.0.1`.
- Nếu nghi secret lộ: đổi tại nhà cung cấp và ứng dụng, kiểm tra log/truy cập, rồi thu hồi secret cũ. Với JWT, việc đổi khóa làm vô hiệu token đang lưu hành.

## Việc bảo mật còn mở

Rate limit login hiện cho qua khi Redis lỗi và tin `X-Forwarded-For`; trước khi đưa qua reverse proxy công khai phải cấu hình proxy tin cậy và kiểm thử HTTP cho giới hạn này. Test profile đã dùng PostgreSQL tạm qua Testcontainers, không còn mật khẩu database local mặc định. Upload file chưa kiểm tra magic bytes/quét malware. Bảng `ticket_qr_tokens.token_hash` hiện lưu giá trị QR để tra cứu trực tiếp dù tên cột có chữ “hash”; nếu database lộ, QR còn hiệu lực cũng lộ. Cần chuyển sang lưu băm token kèm lộ trình tương thích dữ liệu cũ trước khi vận hành thực tế. Các mục này không được xem là đã hoàn thành chỉ vì ứng dụng khởi động hoặc unit test pass.
