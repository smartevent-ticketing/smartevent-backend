# Smart Event Ticketing Platform - Nhật Ký Lỗi Và Cách Fix

Ngày: 14/08/2026

Tài liệu này tổng hợp các lỗi đã gặp trong quá trình setup backend Spring Boot, Docker, PostgreSQL, Flyway và IntelliJ. Mục tiêu là sau này gặp lại lỗi tương tự thì nhìn vào là biết nguyên nhân và hướng xử lý.

## 1. Lỗi Spring Boot Không Tìm Thấy DataSource

### Dấu hiệu

```text
Failed to configure a DataSource: 'url' attribute is not specified
Reason: Failed to determine a suitable driver class
```

### Nguyên nhân

Project đã thêm dependency liên quan đến database:

- Spring Data JPA
- PostgreSQL Driver
- Flyway

Nhưng trong `application.yml` hoặc `application.properties` chưa khai báo thông tin kết nối database.

Spring Boot thấy có JPA nên tự động cố tạo `DataSource`, nhưng không biết phải kết nối tới database nào.

### Cách fix

Thêm cấu hình datasource vào:

```text
ticketing/src/main/resources/application.yml
```

Ví dụ:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/smart_event
    username: smart_event
    password: smart_event
    driver-class-name: org.postgresql.Driver
```

Đồng thời đảm bảo PostgreSQL đang chạy bằng Docker.

## 2. Lỗi Docker Không Kết Nối Được Docker Engine

### Dấu hiệu

```text
failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine
The system cannot find the file specified
```

### Nguyên nhân

Docker Desktop chưa chạy, hoặc Docker engine bên trong Docker Desktop chưa start xong.

Đây không phải lỗi `docker-compose.yml`.

### Cách fix

1. Mở Docker Desktop.
2. Đợi Docker Desktop báo trạng thái running.
3. Chạy lại:

```bash
docker compose up -d
```

Nếu vẫn lỗi, kiểm tra:

```bash
docker version
```

Nếu phần `Server` không hiện, Docker engine vẫn chưa chạy.

## 3. Lỗi IntelliJ Không Nhận Java/Gradle Project

### Dấu hiệu

- Icon file Java hiển thị lạ.
- Nút Run bị xám, không active.
- Không thấy nút tam giác xanh ở class `TicketingApplication`.
- Import có vẻ không resolve đúng.

### Nguyên nhân

IntelliJ đang mở folder cha:

```text
D:\Smart Event Ticketing Platform
```

trong khi backend Gradle project thật nằm ở:

```text
D:\Smart Event Ticketing Platform\ticketing
```

Do đó IntelliJ chưa import đúng Gradle module.

### Cách fix

Cách đơn giản nhất:

1. Đóng project hiện tại.
2. Mở trực tiếp folder backend:

```text
D:\Smart Event Ticketing Platform\ticketing
```

3. Chọn `Trust Project`.
4. Chọn `Load Gradle Project`.
5. Vào Project Structure và chọn Java SDK 17.
6. Reload Gradle project.

Nếu vẫn muốn mở folder cha, cần right click vào `ticketing/build.gradle` rồi chọn link/import Gradle project.

## 4. Lỗi Flyway Checksum Mismatch

### Dấu hiệu

```text
Validate failed: Migrations have failed validation
Migration checksum mismatch for migration version 1
-> Applied to database : ...
-> Resolved locally    : ...
Either revert the changes to the migration, or run repair
```

### Nguyên nhân

File migration `V1__identity_schema.sql` đã từng chạy vào database.

Sau đó file này bị sửa trực tiếp, ví dụ đổi từ `BIGSERIAL` sang `UUID`.

Flyway lưu lịch sử migration trong bảng:

```text
flyway_schema_history
```

Khi app chạy lại, Flyway so sánh checksum của file hiện tại với checksum đã lưu trong database. Nếu khác nhau, Flyway chặn app start.

### Cách fix trong local dev

Vì đây là môi trường local ban đầu, chưa có dữ liệu quan trọng, có thể reset database:

```bash
docker compose down -v
docker compose up -d
```

Sau đó chạy lại backend.

### Cách làm đúng về sau

Không sửa migration đã chạy.

Nếu cần thay đổi schema, tạo file mới:

```text
V2__add_avatar_file_id_to_users.sql
V3__create_events_table.sql
V4__alter_roles_table.sql
```

Quy tắc:

```text
Migration chưa chạy ở database nào  -> có thể sửa
Migration đã chạy rồi               -> không sửa, tạo version mới
```

## 5. Lỗi Insert Role Thiếu UUID

### Dấu hiệu

```text
ERROR: null value in column "id" of relation "roles" violates not-null constraint
Detail: Failing row contains (null, CUSTOMER).
Location: db/migration/V1__identity_schema.sql
Line: 23
```

### Nguyên nhân

Bảng `roles` được khai báo:

```sql
CREATE TABLE roles (
    id UUID PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);
```

Nhưng câu insert lại chỉ truyền `name`:

```sql
INSERT INTO roles(name) VALUES
('CUSTOMER'),
('ORGANIZER'),
('ADMIN');
```

Vì `id` không có default value, PostgreSQL insert `id = null`, trong khi `id` là primary key nên không được null.

### Cách fix

Bật extension `pgcrypto` để dùng `gen_random_uuid()`:

```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;
```

Sau đó insert role kèm `id`:

```sql
INSERT INTO roles(id, name) VALUES
(gen_random_uuid(), 'CUSTOMER'),
(gen_random_uuid(), 'ORGANIZER'),
(gen_random_uuid(), 'ADMIN');
```

Migration hoàn chỉnh:

```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    phone VARCHAR(30),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE roles (
    id UUID PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id),
    role_id UUID NOT NULL REFERENCES roles(id),
    PRIMARY KEY (user_id, role_id)
);

INSERT INTO roles(id, name) VALUES
(gen_random_uuid(), 'CUSTOMER'),
(gen_random_uuid(), 'ORGANIZER'),
(gen_random_uuid(), 'ADMIN');
```

Nếu database local đã từng chạy bản lỗi, reset lại:

```bash
docker compose down -v
docker compose up -d
```

## 6. Lỗi Viết Sai `NOW()` Trong SQL

### Dấu hiệu

Trong migration có đoạn:

```sql
DEFAULT *NOW*()
```

### Nguyên nhân

Dấu `*` là ký tự Markdown để in nghiêng hoặc bôi đậm, không phải cú pháp SQL.

PostgreSQL chỉ hiểu:

```sql
NOW()
```

### Cách fix

Sửa thành:

```sql
created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
```

Không dùng:

```sql
DEFAULT *NOW*()
```

## 7. Lỗi Do Đổi ID Từ BIGSERIAL Sang UUID

### Dấu hiệu

Sau khi đổi schema sang UUID, các chỗ liên quan bị lỗi:

- Insert seed data thiếu UUID.
- Repository vẫn có thể dùng `Long`.
- Entity vẫn có thể dùng `Long id`.
- Migration cũ bị checksum mismatch.

### Nguyên nhân

Khi đổi chiến lược ID, phải đổi đồng bộ cả 3 lớp:

```text
Database       -> UUID
Java Entity    -> UUID
Repository     -> JpaRepository<Entity, UUID>
```

Không thể chỉ sửa mỗi migration.

### Cách fix

Trong database:

```sql
id UUID PRIMARY KEY
```

Trong `BaseEntity.java`:

```java
@Id
@UuidGenerator
@Column(nullable = false, updatable = false)
private UUID id;
```

Trong repository:

```java
JpaRepository<User, UUID>
```

Nếu seed data bằng SQL, nhớ truyền `id`:

```sql
gen_random_uuid()
```

## 8. Quy Trình Debug Lỗi Spring Boot Nên Dùng

Khi Spring Boot lỗi dài, không đọc từ đầu xuống ngay. Tìm các đoạn:

```text
Caused by:
```

và đặc biệt dòng cuối cùng gần cuối log.

Ví dụ với lỗi Flyway:

```text
Caused by: org.postgresql.util.PSQLException
ERROR: null value in column "id" of relation "roles"
```

Đó mới là lỗi gốc.

Phần phía trên như:

```text
Error creating bean with name 'entityManagerFactory'
```

thường chỉ là hậu quả dây chuyền vì Flyway fail trước nên Hibernate/JPA không start được.

## 9. Checklist Fix Nhanh Khi Backend Không Start

Kiểm tra theo thứ tự:

1. Docker Desktop đã chạy chưa?
2. `docker ps` có đủ 4 container chưa?
3. `application.yml` có đúng database username/password chưa?
4. Có còn `application.properties` cũ đè config không?
5. Flyway migration có lỗi SQL không?
6. Có sửa migration đã chạy chưa?
7. Entity ID và database ID có cùng kiểu không?
8. Nếu local DB bẩn và chưa có data quan trọng, reset bằng:

```bash
docker compose down -v
docker compose up -d
```

## 10. Ghi Nhớ Quan Trọng

- Đã dùng Flyway thì không sửa migration đã chạy.
- Đổi ID sang UUID phải đổi cả database, entity và repository.
- Nếu seed data vào bảng có `id UUID PRIMARY KEY`, phải truyền `id` hoặc đặt default.
- `NOW()` là SQL function hợp lệ, `*NOW*()` là sai.
- Docker Compose lỗi engine thường do Docker Desktop chưa chạy, không phải compose sai.
- IntelliJ không hiện nút Run thường do mở sai folder hoặc chưa import Gradle project.
