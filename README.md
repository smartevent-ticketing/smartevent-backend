# 🎟️ Smart Event — Backend API Platform

[![Java](https://img.shields.io/badge/Java-17-orange.svg?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.7-brightgreen.svg?logo=springboot)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg?logo=postgresql)](https://www.postgresql.org/)
[![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.13-ff6600.svg?logo=rabbitmq)](https://www.rabbitmq.com/)
[![Redis](https://img.shields.io/badge/Redis-7.4-red.svg?logo=redis)](https://redis.io/)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED.svg?logo=docker)](https://www.docker.com/)

Hệ thống Backend cho nền tảng quản lý sự kiện và phân phối vé điện tử trực tuyến **Smart Event**. Dự án được thiết kế theo kiến trúc **Modular Monolith** chuẩn doanh nghiệp, chú trọng vào **khả năng chịu tải cao, tính nhất quán dữ liệu, chống overbooking** và xử lý bất đồng bộ tin cậy.

---

## 🌟 Tính năng nổi bật

### 1. Dành cho Khán giả / Người mua vé
* **Tìm kiếm & Khám phá sự kiện:** Lọc sự kiện theo danh mục, địa điểm, thời gian và trạng thái mở bán.
* **Giữ chỗ thời gian thực (Real-time Reservation):** Cơ chế giữ chỗ có thời hạn (TTL) với khóa phân tán chống giữ trùng ghế/vượt số lượng mở bán (anti-overbooking).
* **Thanh toán trực tuyến:** Tích hợp cổng thanh toán trực tuyến (VNPay Sandbox) với quy trình xác thực chữ ký số HMAC-SHA512 an toàn.
* **Vé điện tử & Mã QR:** Nhận vé điện tử ngay sau khi thanh toán; vé nhúng mã QR bảo mật cao được gửi trực tiếp qua email kèm file PDF xác nhận đơn hàng.

### 2. Dành cho Ban tổ chức (Event Organizer)
* **Khởi tạo & Cấu hình sự kiện linh hoạt:** Thiết lập thông tin địa điểm (Venue), phân khu (Zone), sơ đồ chỗ ngồi (Seat Map) và đa dạng các hạng vé (Ticket Types).
* **Quản lý đợt mở bán (Sale Phases):** Cấu hình linh hoạt các giai đoạn bán vé (Early Bird, Standard, VIP, Last Minute) với thời gian và số lượng giới hạn riêng biệt.
* **Theo dõi & Nộp duyệt:** Quy trình kiểm duyệt sự kiện nhiều bước (Draft $\rightarrow$ Pending Approval $\rightarrow$ Published).

### 3. Dành cho Quản trị viên & Đội ngũ vận hành (Admin & Staff)
* **Kiểm soát vé tại cổng (Check-in System):** Quét mã QR xác thực vé thời gian thực; chống sử dụng lại vé đã check-in hoặc vé đã bị chuyển nhượng/thu hồi.
* **Đối soát & Quản lý hoàn tiền (Refund Review Workflow):** Bảng điều khiển đối soát cho các giao dịch tranh chấp/thanh toán trễ, lưu vết lịch sử phê duyệt (Audit Trail) và bằng chứng chứng từ chuyển khoản ngân hàng.
* **Quản trị Outbox & Message Broker:** Theo dõi tình trạng các sự kiện tích hợp, hỗ trợ cơ chế retry thủ công hoặc tự động khi có sự cố mạng.

---

## 🏗️ Kiến trúc & Giải pháp kỹ thuật chuyên sâu

```
                              ┌────────────────────────┐
                              │    Smart Event Web     │
                              └───────────┬────────────┘
                                          │ REST API / JWT
                                          ▼
┌──────────────────────────────────────────────────────────────────────────────────┐
│                             Smart Event Backend Core                             │
│                                                                                  │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐   ┌───────────────────┐  │
│  │   Identity   │   │    Event     │   │  Ticketing   │   │     Payment       │  │
│  │  Module      │   │   Module     │   │   Module     │   │     Module        │  │
│  └──────┬───────┘   └──────┬───────┘   └──────┬───────┘   └─────────┬─────────┘  │
│         │                  │                  │                     │            │
│         │                  ▼                  ▼                     │            │
│         │           ┌────────────────────────────────┐              │            │
│         │           │   Transactional Outbox Pattern │              │            │
│         │           │   (SKIP LOCKED Batch Publisher)│              │            │
│         │           └───────────────┬────────────────┘              │            │
└─────────┼───────────────────────────┼───────────────────────────────┼────────────┘
          │                           │                               │
          ▼                           ▼                               ▼
    ┌───────────┐              ┌─────────────┐                 ┌─────────────┐
    │  Redis 7  │              │  RabbitMQ   │                 │ PostgreSQL  │
    │Rate limit │              │Event Broker │                 │16 (Primary) │
    └───────────┘              └──────┬──────┘                 └─────────────┘
                                      │
                                      ▼
                        ┌───────────────────────────┐
                        │   Notification Consumer   │
                        │ (Inbox Pattern Idempotency│
                        └─────────────┬─────────────┘
                                      ▼
                               ┌─────────────┐
                               │ SMTP Server │
                               │(Email + QR) │
                               └─────────────┘
```

* **Kiến trúc Modular Monolith:** Các module nghiệp vụ (`auth`, `event`, `ticket`, `ordering`, `payment`, `checkin`, `notification`, `outbox`) được phân chia ranh giới rõ ràng (bounded context), dễ dàng bảo trì và sẵn sàng tách thành Microservices khi quy mô mở rộng.
* **Kiểm soát tranh chấp đồng thời (Concurrency Control):** Kết hợp Redis Lock và Pessimistic Locking (`PESSIMISTIC_WRITE`) tại tầng Database đảm bảo không xảy ra hiện tượng bán vượt số lượng vé (Double Booking) khi hàng nghìn người cùng mua một thời điểm.
* **Transactional Outbox Pattern với `SKIP LOCKED`:** Sự kiện nghiệp vụ được ghi đồng thời vào bảng `outbox_events` trong cùng một Database Transaction. Background Worker truy vấn bằng `SELECT ... FOR UPDATE SKIP LOCKED` cho phép chạy nhiều node/worker song song mà không bị nghẽn hay trùng thông điệp.
* **Xác nhận 2 chiều (Publisher Confirms):** Tích hợp xác nhận phản hồi từ RabbitMQ Broker trước khi đổi trạng thái sự kiện sang `PUBLISHED`, đảm bảo không bao giờ thất thoát dữ liệu.
* **Idempotent Consumer (Inbox Pattern):** Bảng `notification_inbox` giúp kiểm soát tính duy nhất khi nhận message từ hàng đợi RabbitMQ, triệt tiêu nguy cơ gửi trùng email vé khi có hiện tượng redelivery mạng.
* **Bảo mật chuẩn hóa:**
  * Xác thực Stateless JWT với mã hóa HS256, yêu cầu Secret tối thiểu 256-bit đạt chuẩn bảo mật.
  * Thuật toán sinh token QR sử dụng chuỗi ngẫu nhiên 32-byte từ `SecureRandom`, xác thực trực tiếp qua cơ sở dữ liệu thay vì dùng secret tĩnh.
  * Tích hợp Rate Limiting trên Redis (chống brute-force đăng nhập).
  * Kiểm soát truy cập tài liệu Swagger UI/OpenAPI theo quyền hạn (`ROLE_ADMIN`).

---

## 🛠️ Công nghệ sử dụng

| Lớp | Công nghệ / Thư viện | Vai trò |
|---|---|---|
| **Core** | Java 17, Spring Boot 4.0.7 | Nền tảng ứng dụng chính |
| **Frameworks** | Spring WebMVC, Spring Security, Spring Data JPA | REST API, Bảo mật, ORM |
| **Cơ sở dữ liệu** | PostgreSQL 16, Flyway Migration | Lưu trữ dữ liệu quan hệ, quản lý schema versioning |
| **Caching & In-Memory**| Redis 7 | Rate limiting, Session cache, Distributed locks |
| **Hàng đợi thông điệp** | RabbitMQ 3.13 | Message broker giao tiếp bất đồng bộ giữa các module |
| **Lưu trữ tệp tin** | MinIO (S3 Compatible) | Lưu trữ hình ảnh sự kiện, sơ đồ chỗ ngồi, hóa đơn |
| **Tài liệu & Xử lý ảnh** | OpenPDF, ZXing | Kết xuất hóa đơn PDF và sinh ảnh mã QR |
| **Kiểm thử** | JUnit 5, Mockito, Testcontainers | Unit test và kiểm thử tích hợp tự động với container thật |
| **Đóng gói & CI/CD** | Multi-stage Docker, GitHub Actions | Đóng gói container JRE 17 non-root, pipeline tự động |

---

## 📂 Cấu trúc mã nguồn

```
src/main/java/com/smartevent/
├── common/                  # Các đối tượng dùng chung, Exception handler, Enum, Utilities
├── config/                  # Cấu hình hệ thống (Security, RabbitMQ, Redis, OpenAPI,...)
├── infrastructure/          # Giao tiếp với hạ tầng bên ngoài (Mail, MinIO S3, Event Publisher)
└── modules/                 # Các module nghiệp vụ theo từng miền (Domain-Driven)
    ├── auth/                # Xác thực, cấp phát & làm mới JWT token
    ├── user/                # Quản lý người dùng, phân quyền người dùng
    ├── event/               # Quản lý sự kiện, địa điểm, sơ đồ ghế, đa phương tiện
    ├── ticket/              # Hạng vé, các đợt mở bán, phát hành vé
    ├── ordering/            # Giữ chỗ (Reservation), tạo và quản lý đơn hàng
    ├── payment/             # Tích hợp VNPay, callback/IPN, đối soát hoàn tiền
    ├── invoice/             # Tạo hóa đơn và xuất file PDF xác nhận đơn hàng
    ├── checkin/             # Quét mã QR check-in tại cửa kiểm soát sự kiện
    ├── notification/        # Consumer hàng đợi, gửi email vé kèm mã QR inline
    └── outbox/              # Quản lý sự kiện outbox, worker quét batch phân tán
```

---

## 🚀 Hướng dẫn cài đặt & Khởi chạy

### 1. Yêu cầu hệ thống
* **Java Development Kit (JDK):** Phiên bản 17 trở lên.
* **Docker & Docker Compose:** Đã cài đặt và đang chạy Docker Engine.
* **Git**

### 2. Khởi động hạ tầng dịch vụ (Infrastructure)
Dự án sử dụng repository [smartevent-infra](https://github.com/smartevent-ticketing/smartevent-infra) để chạy cụm dịch vụ phụ trợ:
```bash
# Clone repository hạ tầng cùng cấp thư mục với backend
git clone https://github.com/smartevent-ticketing/smartevent-infra.git
cd smartevent-infra

# Sao chép file cấu hình mẫu và khởi chạy cụm container
cp .env.example .env
docker compose up -d
```

Các dịch vụ sẽ sẵn sàng tại:
* **PostgreSQL:** `localhost:5432`
* **Redis:** `localhost:6379`
* **RabbitMQ:** `localhost:5672` (Giao diện Management: `http://localhost:15672`)
* **MinIO Console:** `http://localhost:9001` (S3 API: `http://localhost:9000`)

### 3. Cấu hình Backend
Di chuyển vào thư mục `smartevent-backend`:
```bash
cp .env.example .env
```
Thiết lập các biến môi trường quan trọng trong `.env`:
* `JWT_SECRET`: Chuỗi khóa bí mật chuẩn Base64 (tối thiểu 32 ký tự ngẫu nhiên).
* `SPRING_DATASOURCE_*`: Thông tin kết nối PostgreSQL (khớp với file `.env` của infra).
* `VNPAY_*`: Mã Merchant (`TMN_CODE`) và chuỗi bảo mật (`HASH_SECRET`) từ cổng VNPay Sandbox.
* `APP_DOCS_PUBLIC`: Đặt `true` nếu muốn truy cập trực tiếp Swagger UI mà không cần đăng nhập Admin.

### 4. Chạy ứng dụng

#### Lựa chọn A: Chạy trực tiếp qua Gradle Wrapper (Khuyên dùng khi lập trình)
```bash
# Trên Windows PowerShell:
.\gradlew.bat bootRun

# Trên Linux / macOS:
./gradlew bootRun
```

#### Lựa chọn B: Đóng gói và chạy qua Docker Container
Ứng dụng cung cấp sẵn Multi-stage Dockerfile tối ưu kích thước và bảo mật với user `app` (non-root):
```bash
# 1. Dựng image
docker build -t smartevent-backend:local .

# 2. Khởi chạy container nối trực tiếp vào mạng dịch vụ phụ trợ
docker run --rm --name smartevent-api --network smartevent-network \
  --env-file .env \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/smart_event_db \
  -e SPRING_DATA_REDIS_HOST=redis \
  -e SPRING_RABBITMQ_HOST=rabbitmq \
  -e APP_MINIO_ENDPOINT=http://minio:9000 \
  -p 8080:8080 smartevent-backend:local
```

---

## 🧪 Kiểm thử tự động (Testing)

Dự án áp dụng **Testcontainers** giúp khởi chạy môi trường kiểm thử thực tế trên container PostgreSQL độc lập mà không cần tạo database thủ công.

```bash
# Thực thi toàn bộ Unit test và Integration test
.\gradlew.bat test postgresTest
```

* **Unit & MockMvc Tests:** Kiểm tra các tầng Service, Controller và Validation.
* **Postgres Integration Tests:** Tự động kích hoạt PostgreSQL 16 Alpine container, áp dụng toàn bộ migrations của Flyway, mô phỏng tranh chấp đa luồng và đối soát giao dịch.

---

## 📖 Danh mục API tiêu biểu

Tài liệu API chi tiết được cung cấp qua giao diện Swagger UI:
* **Giao diện trực quan:** `http://localhost:8080/swagger-ui.html`
* **OpenAPI Specs (JSON):** `http://localhost:8080/v3/api-docs`

| Phân hệ | Phương thức | Endpoint | Mô tả |
|---|---|---|---|
| **Auth** | `POST` | `/api/v1/auth/register` | Đăng ký tài khoản |
| | `POST` | `/api/v1/auth/login` | Đăng nhập hệ thống (Rate limit: 5 req/min) |
| | `POST` | `/api/v1/auth/refresh` | Làm mới access token |
| **Events** | `GET` | `/api/v1/events` | Danh sách sự kiện công khai |
| | `POST` | `/api/v1/events/setup` | Thiết lập sự kiện, địa điểm, khu vực, hạng vé |
| | `POST` | `/api/v1/events/{id}/submit` | Gửi yêu cầu duyệt sự kiện |
| **Tickets** | `POST` | `/api/v1/reservations` | Giữ chỗ có thời hạn (chống giữ trùng) |
| | `POST` | `/api/v1/orders` | Khởi tạo đơn hàng từ chỗ đã giữ |
| | `GET` | `/api/v1/tickets/my-tickets` | Danh sách vé cá nhân của người dùng |
| **Payment**| `POST` | `/api/v1/payments/create-url` | Tạo đường dẫn thanh toán qua VNPay |
| | `GET` | `/api/v1/payments/vnpay/return` | Xử lý redirect sau thanh toán |
| | `GET` | `/api/v1/payments/vnpay/ipn` | Webhook IPN nhận kết quả thanh toán từ VNPay |
| **Check-in**| `POST` | `/api/v1/checkin/scan` | Quét mã QR check-in vé tại cổng |
| **Admin** | `GET` | `/api/v1/admin/payment-refund-reviews` | Danh sách hồ sơ cần đối soát hoàn tiền |
| | `PATCH`| `/api/v1/admin/payment-refund-reviews/{id}` | Cập nhật kết quả đối soát & chứng từ hoàn |
| | `GET` | `/api/v1/admin/outbox-events` | Giám sát hàng đợi sự kiện Outbox |
| | `POST` | `/api/v1/admin/outbox-events/{id}/retry` | Kích hoạt retry sự kiện Outbox thủ công |

---

## 📄 Bản quyền & Tác giả

Phát triển bởi **Trịnh Đăng Huy** ([@trinhdanghuy-tech](https://github.com/trinhdanghuy-tech)).  
Dự án được phân phối theo giấy phép mã nguồn mở [MIT License](LICENSE).
