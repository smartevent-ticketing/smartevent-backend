# Smart Event Ticketing Platform - Các Bước Setup Dự Án

Ngày: 14/08/2026

Tài liệu này ghi lại các bước đã làm từ lúc khởi tạo dự án backend đến khi Docker services chạy ổn và endpoint health check trả về `UP`.

## 1. Chốt Công Nghệ Nền Tảng

Backend sử dụng stack:

- Java 17
- Spring Boot 4.0.7
- Gradle Groovy DSL
- PostgreSQL
- Redis
- RabbitMQ
- MinIO
- Flyway
- Spring Data JPA
- Spring Security
- Spring Boot Actuator
- Lombok
- Springdoc OpenAPI

Lý do chọn Gradle:

- Phù hợp với nhiều môi trường doanh nghiệp hiện đại.
- Tốt cho việc học cách build project thực tế.
- Linh hoạt hơn khi dự án lớn dần.

Spring Boot 4.0.7 được chọn thay vì 4.1.x vì version này ổn định hơn với dependency hiện tại, tránh lỗi tương thích ngay khi mới setup.

## 2. Tạo Project Spring Boot

Tạo project bằng Spring Initializr với cấu hình:

- Project: Gradle - Groovy
- Language: Java
- Spring Boot: 4.0.7
- Java: 17
- Group: `com.smartevent`
- Artifact: `ticketing`
- Package: `com.smartevent.ticketing`
- Packaging: Jar

Dependencies đã chọn:

- Spring Web
- Spring Data JPA
- Spring Security
- Validation
- PostgreSQL Driver
- Flyway Migration
- Spring Boot Actuator
- Spring Data Redis
- Spring for RabbitMQ
- Lombok

Lưu ý với RabbitMQ:

- Chọn `Spring for RabbitMQ`.
- Không chọn `Spring for RabbitMQ Streams` trong giai đoạn này.

## 3. Chốt Kiến Trúc Source Code

Hướng kiến trúc đã chọn:

```text
Modular Monolith with Layered/Infrastructure Separation
```

Không dùng Clean Architecture quá nặng ngay từ đầu. Thay vào đó, dự án được chia theo module nghiệp vụ và tách riêng các thành phần dùng chung.

Cấu trúc tổng quát:

```text
com.smartevent.ticketing
├── common
├── config
├── infrastructure
│   ├── mail
│   ├── storage
│   ├── messaging
│   ├── security
│   └── redis
└── modules
    ├── identity
    ├── event
    ├── ticketing
    ├── reservation
    ├── ordering
    ├── payment
    ├── notification
    ├── resale
    ├── recommendation
    ├── analytics
    └── audit
```

Giai đoạn đầu, trong `common` chỉ nên tạo các phần lõi:

```text
common
├── api
├── error
└── entity
```

Sau này mới thêm khi thật sự cần:

```text
common/security
common/validation
common/util
common/pagination
```

## 4. Đặt Docker Compose Ở Folder Cha

Quyết định đặt `docker-compose.yml` ở folder cha chung của project:

```text
Smart Event Ticketing Platform/
├── docker-compose.yml
├── docs/
└── ticketing/
    ├── build.gradle
    ├── settings.gradle
    ├── gradlew.bat
    └── src/
```

Lý do:

- Docker Compose phục vụ toàn bộ local environment.
- PostgreSQL, Redis, RabbitMQ, MinIO không thuộc riêng source code backend.
- Sau này thêm frontend thì vẫn dùng chung một compose.

## 5. Tạo Docker Compose Services

Các service cần có:

- PostgreSQL 16
- Redis 7
- RabbitMQ 3 Management
- MinIO

Tài khoản local đã thống nhất:

```text
PostgreSQL:
  database: smart_event
  username: smart_event
  password: smart_event

RabbitMQ:
  username: smart_event
  password: smart_event

MinIO:
  username: minioadmin
  password: minioadmin

Redis:
  không đặt password cho môi trường local
```

## 6. Cấu Hình `application.yml`

Trong backend, đổi từ `application.properties` sang:

```text
ticketing/src/main/resources/application.yml
```

Quan trọng:

- Không cần tạo lại project.
- Chỉ đổi format cấu hình từ properties sang YAML.
- Không nên để cả `application.properties` và `application.yml` cùng cấu hình database để tránh bị đè config.

Config backend cần khớp với Docker Compose:

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/smart_event
    username: smart_event
    password: smart_event
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    properties:
      hibernate:
        format_sql: true

  flyway:
    enabled: true
    locations: classpath:db/migration

  data:
    redis:
      host: localhost
      port: 6379

  rabbitmq:
    host: localhost
    port: 5672
    username: smart_event
    password: smart_event

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    health:
      probes:
        enabled: true
```

MinIO có thể thêm sau bằng custom config:

```yaml
app:
  storage:
    minio:
      endpoint: http://localhost:9000
      access-key: minioadmin
      secret-key: minioadmin
      bucket: smart-event
```

## 7. Chạy Docker Desktop Và Compose

Lỗi đã gặp:

```text
failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine
```

Nguyên nhân:

- Docker Desktop chưa chạy.
- Docker engine chưa start xong.

Cách xử lý:

1. Mở Docker Desktop.
2. Đợi Docker Desktop báo đang running.
3. Chạy lại:

```bash
docker compose up -d
```

Kết quả đã đạt:

```text
Container smart_event_postgres   Started
Container smart_event_redis      Started
Container smart_event_minio      Started
Container smart_event_rabbitmq   Started
```

Kiểm tra container:

```bash
docker ps
```

Đã thấy 4 container đang chạy:

```text
smart_event_postgres
smart_event_redis
smart_event_rabbitmq
smart_event_minio
```

## 9. Kiểm Tra Backend Health

Sau khi Docker services chạy và backend start thành công, mở:

```text
http://localhost:8080/actuator/health
```

Kết quả đã đạt:

```json
{
  "groups": [
    "liveness",
    "readiness"
  ],
  "status": "UP"
}
```

Điều này xác nhận:

- Backend đã start được.
- Actuator health endpoint hoạt động.
- Nền tảng local đã ổn để tiếp tục code.

## 11. Trạng Thái Hiện Tại

Đã hoàn thành:

- Chốt Gradle và Spring Boot 4.0.7.
- Tạo project backend `ticketing`.
- Chốt hướng kiến trúc modular monolith.
- Tạo Docker Compose local stack.
- Chạy thành công PostgreSQL, Redis, RabbitMQ, MinIO.
- Cấu hình backend kết nối local environment.
- Backend health check trả về `UP`.

Bước tiếp theo nên làm:

1. Hoàn thiện `common/api`, `common/error`, `common/entity`.
2. Tạo migration đầu tiên cho identity:

```text
V1__identity_schema.sql
```

3. Bắt đầu module `identity`:

```text
modules/identity
├── controller
├── dto
├── entity
├── repository
├── service
└── mapper
```

4. Làm user/role N:N trước.
5. Sau đó mới làm auth register/login/JWT.

## 12. Ghi Nhớ Quan Trọng

- PostgreSQL là source of truth.
- Redis chỉ dùng cho cache, TTL và hỗ trợ performance, không làm nguồn dữ liệu chính.
- Docker Compose đặt ở folder cha.
- Backend Spring Boot nằm trong folder `ticketing`.
- Khi config database, Docker Compose và `application.yml` phải dùng cùng username/password.
- Khi IntelliJ không hiện nút Run, thường là do chưa import đúng Gradle project hoặc chưa chọn Java SDK.
