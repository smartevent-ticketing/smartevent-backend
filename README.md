# Smart Event Ticketing — Backend

Backend cho nền tảng bán vé sự kiện Smart Event. Ứng dụng dùng Java 17, Spring Boot 4, PostgreSQL và kiến trúc modular monolith. Đây là dự án đồ án/demo: luồng bán vé cốt lõi đã có, còn các mục trong [Phần cần hoàn thiện](#phần-cần-hoàn-thiện) phải được xử lý trước khi vận hành thực tế.

## Trạng thái được kiểm tra

Kiểm tra mã nguồn ngày **26/09/2026**:

- Có **20 Flyway migration** (`V1`–`V20`), gồm lịch sử Admin xử lý đối soát và inbox chống xử lý lặp email vé.
- `test` chạy **188/188** và `postgresTest` chạy **13/13** trên PostgreSQL 16 do Testcontainers tạo, 0 lỗi, 0 bỏ qua. Chưa kiểm thử tích hợp với RabbitMQ, MinIO, SMTP hoặc VNPay thật.

| Phân hệ | Hiện trạng trong code |
| --- | --- |
| Identity | Đăng ký, đăng nhập, JWT, refresh/logout, RBAC; login được gắn giới hạn 5 lần/phút dựa trên Redis. |
| Sự kiện | Category, venue, event, khu vực, ghế, media; tạo cấu hình, kiểm tra điều kiện gửi duyệt, duyệt/từ chối/hủy. |
| Bán vé | Loại vé, đợt bán, tồn kho, giữ chỗ có hạn, tạo đơn với giá được chốt ở server. |
| Thanh toán | Tạo URL và xử lý Return/IPN cho **VNPay Sandbox**. Các enum/tiện ích cũ cho phương thức khác chưa tạo giao dịch được. |
| Sau thanh toán | Phát hành vé, QR, chuyển vé, check-in; gửi email vé có PNG QR nội tuyến và đính kèm, cùng PDF xác nhận đơn hàng nội bộ qua Outbox/RabbitMQ. |
| Đối soát | Admin xem, cập nhật trạng thái xử lý hoàn tiền thủ công, ghi chú, bằng chứng và lịch sử thao tác; trang `/admin/refund-reviews` ở web hỗ trợ thao tác này. Backend không gọi refund tự động. |
| Lưu trữ | Tải file lên MinIO, metadata PostgreSQL, URL có thời hạn cho file riêng tư. |

Luồng chính: đăng nhập → tạo và duyệt sự kiện → cấu hình vé → giữ chỗ → tạo đơn → thanh toán VNPay Sandbox → phát hành vé/QR và PDF xác nhận → check-in. Giao dịch thanh toán đến muộn hoặc sự kiện bị hủy được đưa vào danh sách **cần đối soát** để Admin xử lý và cập nhật kết quả thủ công.

## Công nghệ và cấu trúc

| Thành phần | Công nghệ / vai trò |
| --- | --- |
| API | Java 17, Spring Boot 4.0.7, Spring WebMVC, Spring Security, OpenAPI |
| Dữ liệu | PostgreSQL 16, Spring Data JPA, Flyway |
| Hỗ trợ | Redis 7, RabbitMQ, MinIO, Spring Mail |
| Tài liệu | OpenPDF cho PDF, ZXing cho QR |
| Build và test | Gradle Wrapper, JUnit 5, Mockito, PostgreSQL Testcontainers, GitHub Actions |

Mã chính nằm trong `src/main/java/com/smartevent`: `modules/` chứa nghiệp vụ; `common/`, `config/` và `infrastructure/` chứa thành phần dùng chung. Migration ở `src/main/resources/db/migration`. PostgreSQL là nguồn dữ liệu chuẩn; Redis hỗ trợ giới hạn truy cập và bộ đếm; Outbox chuyển thông báo sang RabbitMQ để gửi mail bất đồng bộ.

Hạ tầng local nằm ở repository [smartevent-infra](https://github.com/smartevent-ticketing/smartevent-infra); giao diện ở [smartevent-web](https://github.com/smartevent-ticketing/smartevent-web). Docker Compose của infra chạy dịch vụ phụ trợ; backend có [Dockerfile và hướng dẫn chạy image riêng](docs/02-operations/docker-ci-testcontainers.md). Flyway của backend quản lý schema database.

## Chạy trên máy local

### Yêu cầu

- JDK 17 và Docker Desktop/Docker Engine với Compose.
- Clone `smartevent-backend` và `smartevent-infra` cạnh nhau.
- Các cổng mặc định còn trống: `8080`, `5432`, `6379`, `5672`, `15672`, `9000`, `9001`.

### 1. Khởi động hạ tầng

Trong thư mục `smartevent-infra`, tạo `.env` từ `.env.example` nếu chưa có, thay các mật khẩu mẫu rồi khởi động:

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
docker compose up -d
docker compose ps
```

### 2. Cấu hình backend

Trong thư mục `smartevent-backend`, tạo `.env` từ `.env.example` nếu chưa có. Đối chiếu database, RabbitMQ và MinIO với `.env` của infra. **Phải tạo `JWT_SECRET` Base64 từ ít nhất 32 byte ngẫu nhiên**; mã merchant và hash secret VNPay để trống cho tới khi nhận từ Sandbox. Xem [hướng dẫn bảo mật cấu hình](docs/02-operations/security-configuration.md).

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
```

Spring Boot/Gradle **không tự đọc** file `.env`. Có thể nạp file dạng `KEY=value` vào process PowerShell hiện tại rồi chạy backend trong cùng terminal:

```powershell
Get-Content .env |
  Where-Object { $_ -and -not $_.StartsWith('#') } |
  ForEach-Object {
    $name, $value = $_.Split('=', 2)
    [Environment]::SetEnvironmentVariable($name, $value, 'Process')
  }
.\gradlew.bat bootRun
```

Nếu dùng IDE, khai báo các biến tương ứng trong Run Configuration. Trên macOS/Linux dùng `./gradlew` và nạp biến môi trường bằng công cụ shell phù hợp. Không commit `.env` hoặc dùng lại secret của môi trường thật.

Để gửi mail thật, cấu hình `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`. Nếu `APP_MAIL_ENABLED=false` hoặc thiếu sender, giao thư thất bại và đi qua retry/DLQ; hệ thống không ghi `SENT` giả. `APP_DOCS_PUBLIC=true` chỉ phù hợp local/demo; mặc định Swagger yêu cầu Admin.

### 3. Kiểm tra

Khi Docker Engine đang chạy:

```powershell
.\gradlew.bat test postgresTest
```

- API: `http://localhost:8080/api/v1`
- Swagger UI: `http://localhost:8080/swagger-ui.html` (public khi `APP_DOCS_PUBLIC=true`)
- Health: `http://localhost:8080/actuator/health`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Báo cáo test: `build/reports/tests/test/index.html`

Test mặc định có bài kiểm tra khởi động ứng dụng và Flyway. Cả `test` và `postgresTest` dùng PostgreSQL tạm qua Testcontainers, không dùng database ứng dụng local. Xem [hướng dẫn Dockerfile, CI và Testcontainers](docs/02-operations/docker-ci-testcontainers.md). Tài liệu `BACKEND_FIXES_AND_NGROK.md` ghi lại cách test cũ bằng `backend_review` local.

### 4. Demo thanh toán VNPay

`VNPAY_RETURN_URL` là nơi trình duyệt quay về; `VNPAY_IPN_URL` là webhook từ VNPay tới backend. IPN cần một địa chỉ HTTPS mà VNPay truy cập được và phải được cấu hình ở phía merchant. Backend kiểm tra chữ ký, mã merchant, số tiền và trạng thái trước khi xác nhận giao dịch. Xem [hướng dẫn demo qua ngrok](docs/BACKEND_FIXES_AND_NGROK.md#2-cài-ngrok-và-lấy-địa-chỉ-https).

## API chính

| Nghiệp vụ | Endpoint tiêu biểu |
| --- | --- |
| Tài khoản | `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, `GET /api/v1/auth/me` |
| Sự kiện | `GET /api/v1/events`, `POST /api/v1/events/setup`, `POST /api/v1/events/{id}/submit` |
| Giữ vé và đơn hàng | `POST /api/v1/reservations`, `POST /api/v1/orders` |
| Thanh toán VNPay | `POST /api/v1/payments/create-url`, `GET /api/v1/payments/vnpay/ipn` |
| Vé và check-in | `GET /api/v1/tickets/my-tickets`, `POST /api/v1/checkin/scan` |
| Đối soát | `GET /api/v1/admin/payment-refund-reviews?status=REQUIRED`, `PATCH /api/v1/admin/payment-refund-reviews/{id}`, `GET /api/v1/admin/payment-refund-reviews/{id}/history` |

Chi tiết request, response và yêu cầu xác thực xem tại Swagger UI. Các endpoint quản lý cần JWT và vai trò phù hợp; IPN dùng chữ ký của VNPay. Khi cập nhật đối soát, Admin chọn `IN_REVIEW`, `REFUNDED_CONFIRMED` hoặc `CLOSED_NO_REFUND`, luôn ghi `note`; `REFUNDED_CONFIRMED` yêu cầu `evidenceReference` của khoản hoàn đã đối chiếu. Không có lệnh gọi refund từ API này.

Ví dụ cập nhật sau khi đối chiếu giao dịch hoàn thực tế:

```json
{"status":"REFUNDED_CONFIRMED","note":"Đã kiểm tra sao kê và xác nhận hoàn tiền","evidenceReference":"Mã giao dịch từ VNPay hoặc ngân hàng"}
```

## Phần cần hoàn thiện

Các mục sau được đối chiếu trực tiếp với mã hiện tại. Luồng chính dùng được cho demo, nhưng chưa có đủ cơ sở để gọi là sẵn sàng cho production.

| Ưu tiên | Hạng mục | Hiện trạng / việc còn thiếu |
| --- | --- | --- |
| Cao | SMTP không thể exactly-once | Inbox và khóa delivery ngăn xử lý lặp đã commit, nhưng nếu SMTP nhận thư rồi process chết trước khi DB commit, email có thể gửi lại. Cần theo dõi, đối soát và chấp nhận semantics at-least-once. |
| Cao | Kiểm thử tải và hạ tầng | Chưa đo 100 giao dịch đồng thời với RabbitMQ/SMTP thật. Consumer email còn giữ kết nối database khi chờ SMTP; cần đo pool, backlog, latency, lỗi SMTP và DLQ trên môi trường tách biệt trước khi cam kết sức chứa. |
| Trung bình | Vận hành thông báo | Outbox thử lại tối đa năm lần, Rabbit consumer tối đa ba lần rồi vào DLQ; chưa có job tự động phát lại DLQ hoặc cảnh báo backlog. Admin cần kiểm tra và xử lý. |
| Trung bình | Thông báo hồ sơ hoàn tiền | Admin đã có trang xử lý và lịch sử; khách mua vé chưa nhận email/in-app khi Admin đổi trạng thái hồ sơ. Nếu bổ sung, phát sự kiện Outbox sau quyết định đã xác minh. |
| Trung bình | Bảo mật còn mở | Rate limit login tin `X-Forwarded-For` và cho qua khi Redis lỗi; QR token đang lưu trực tiếp trong database. Cần proxy tin cậy, băm token có lộ trình tương thích và kiểm thử HTTP. |
| Theo kế hoạch | Cổng thanh toán khác | Hiện chỉ hỗ trợ VNPay. VietQR có thể cân nhắc sau khi luồng hiện tại được đo và vận hành ổn định; các enum/tiện ích cũ chưa phải tính năng hoạt động. |
| Trung bình | Kiểm tra file | Upload dựa vào extension và MIME do client cung cấp; checksum để `null`, file được đánh `CLEAN` ngay. Chưa có kiểm tra nội dung thực, quét malware hoặc đối soát object MinIO với metadata PostgreSQL. |
| Trung bình | Triển khai và tài liệu tài chính | Đã có Dockerfile, CI kiểm thử và PostgreSQL Testcontainers; các bộ test đã chạy local, CI trên GitHub chưa được kích hoạt. Chưa kiểm thử end-to-end với dịch vụ ngoài. PDF xác nhận được sinh nội bộ; chưa tích hợp ký số/nhà cung cấp hóa đơn thuế. |

Ưu tiên tiếp theo: kiểm thử tải và sự cố RabbitMQ/SMTP trên môi trường riêng, thêm cảnh báo backlog/DLQ, rồi hoàn thiện các mục bảo mật và kiểm tra file còn mở.

## Tài liệu liên quan

- [Trang tài liệu backend](docs/README.md) — sơ đồ kiến trúc, các luồng nghiệp vụ và hướng dẫn module.
- [Các sửa lỗi và kiểm thử PostgreSQL](docs/BACKEND_FIXES_AND_NGROK.md) — mốc 11/09/2026; số lượng test ở tài liệu này là lịch sử.
- [Runbook vận hành](docs/02-operations/reliability-runbook.md).
- [Thông báo khi 100 người mua đồng thời](docs/02-operations/notification-reliability.md).
- [Hướng dẫn bảo mật cấu hình](docs/02-operations/security-configuration.md).
- [Dockerfile, CI và Testcontainers cho local](docs/02-operations/docker-ci-testcontainers.md).
- [Đặc tả và tài liệu Phase 1](docs/00-overview/phase-1-status.md) — mốc 23/08/2026; trạng thái cập nhật nằm trong README này.

## Tác giả

Trịnh Đăng Huy — [GitHub](https://github.com/trinhdanghuy-tech)
