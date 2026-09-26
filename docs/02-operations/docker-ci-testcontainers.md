# Dockerfile, CI và Testcontainers khi phát triển local

| Công cụ | Làm gì | Khi nào cần? |
| --- | --- | --- |
| Dockerfile | Đóng gói backend thành image chạy bằng JRE 17 | Khi muốn thử chạy backend trong container |
| Testcontainers | Tạo PostgreSQL tạm cho test, tự dọn sau khi JVM test kết thúc | Khi chạy `test`/`postgresTest` |
| GitHub Actions CI | Chạy test và dựng image khi push/PR; không deploy | Khi đưa code lên GitHub |

## Chạy test an toàn

Từ thư mục `smartevent-backend`, bật Docker Desktop/Engine rồi kiểm tra `docker info`. Sau đó:

```powershell
.\gradlew.bat test postgresTest
```

`test` chạy unit test và bài khởi động Spring/Flyway; bài khởi động dùng URL `jdbc:tc:`. `postgresTest` chạy các ca giao dịch và tranh chấp dữ liệu bằng một `PostgreSQLContainer` giữ sống suốt bộ test. Cả hai Gradle test task dùng PostgreSQL 16 Alpine tạm. Không cần tạo `backend_review`, không cần nhập mật khẩu PostgreSQL thật, và không đụng database `smart_event_db` của ứng dụng. Biến `SPRING_DATASOURCE_URL` trong terminal không đổi đích database của test mặc định.

Nếu Docker chưa chạy, Testcontainers báo lỗi kết nối Docker; bật Docker rồi chạy lại. Bộ `postgresTest` vẫn cho phép kết nối database local `backend_review` bằng `BACKEND_TEST_JDBC_URL` để phục vụ môi trường cũ; chỉ chấp nhận URL loopback với đúng tên database đó. Không dùng database nghiệp vụ để chạy test.

## Tạo và chạy image backend trên máy

Trong `smartevent-backend`:

```powershell
docker build -t smartevent-backend:local .
```

Image chỉ chứa ứng dụng backend, không chứa PostgreSQL, Redis, RabbitMQ hay MinIO. Nếu hạ tầng từ `smartevent-infra/docker-compose.yml` đã chạy, nối container vào mạng `smartevent-network`:

```powershell
docker run --rm --name smartevent-api --network smartevent-network `
  --env-file .env `
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/smart_event_db `
  -e SPRING_DATA_REDIS_HOST=redis `
  -e SPRING_RABBITMQ_HOST=rabbitmq `
  -e APP_MINIO_ENDPOINT=http://minio:9000 `
  -p 8080:8080 smartevent-backend:local
```

Điều chỉnh tên database nếu `.env` infra dùng tên khác. Các mật khẩu trong `.env` backend phải khớp với infra. Trong container, `localhost` chỉ chính container backend, vì vậy URL dịch vụ phụ trợ phải dùng tên service `postgres`, `redis`, `rabbitmq`, `minio`. `APP_FRONTEND_URL` và VNPay Return URL vẫn là địa chỉ trình duyệt/đối tác truy cập được; IPN cần URL HTTPS công khai khi thử callback thật.

Không đưa `.env` vào image. `.dockerignore` loại file này khỏi build context; `--env-file` chỉ nạp giá trị lúc chạy. Dockerfile dựng JAR ở stage JDK rồi chạy bằng JRE với user không có quyền root.

## CI làm gì

`.github/workflows/ci.yml` chạy trên GitHub-hosted Ubuntu khi push, mở PR hoặc bấm chạy thủ công. Các bước là JDK 17 → `test` → `postgresTest` → `bootJar` → `docker build`. Testcontainers dùng Docker Engine trên runner. Workflow không có bước đẩy image, phát hành hay triển khai, và không cần secret VNPay/SMTP vì test không gọi dịch vụ thật. Nếu test lỗi, báo cáo được lưu thành artifact.

CI có thể mất thêm thời gian lần đầu để tải Gradle, dependency và image PostgreSQL. Trên máy không có Docker Engine đang chạy, Testcontainers và `docker build` sẽ không chạy được; cần bật Docker rồi thử lại.
