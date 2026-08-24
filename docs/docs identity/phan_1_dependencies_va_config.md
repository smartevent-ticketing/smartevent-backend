# 📦 PHẦN 1: KHAI BÁO DEPENDENCIES & CẤU HÌNH PROPERTIES HỆ THỐNG JWT
## Smart Event Ticketing Platform — Module Identity & Authentication

**Ngày tạo:** 16/08/2026  
**Trạng thái:** Hoàn thành & Đã kiểm tra biên dịch (`BUILD SUCCESSFUL`)  
**Tác giả / Hệ thống:** Backend Engineering Team  

---

## 🎯 1. Mục Tiêu Của Phần 1

Để xây dựng một hệ thống bán vé sự kiện chịu tải lớn, cơ chế xác thực người dùng (**Authentication**) và phân quyền (**Authorization**) cần đảm bảo:
1. **Stateless (Phi trạng thái):** Không lưu Session trên RAM của Server để dễ dàng mở rộng ngang (Scale out) nhiều instance backend qua Load Balancer.
2. **Hiệu Năng Cao & Bảo Mật:** Sử dụng thư viện JWT chuẩn mực, khóa bí mật HMAC-SHA256 đạt chuẩn an toàn 256-bit.
3. **Linh Hoạt Trong Cấu Hình:** Cho phép ghi đè thời hạn token và khóa bí mật qua biến môi trường (Environment Variables) khi deploy Docker/Kubernetes.

---

## 🛠️ 2. Khai Báo Dependencies Trong `build.gradle`

### 2.1. Lựa Chọn Thư Viện: JJWT (Java JWT) 0.12.6
Chúng ta lựa chọn bộ ba thư viện **JJWT (io.jsonwebtoken) version 0.12.6** mới nhất:

```groovy
// d:/Smart Event Ticketing Platform/ticketing/build.gradle

dependencies {
    // ... các dependencies khác ...

    // JWT Security (JJWT 0.12.6)
    implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'

    // ...
}
```

### 2.2. Phân Tích Chức Năng Của Từng Dependency

| Dependency | Scope | Vai Trò & Bản Chất Kỹ Thuật |
|---|:---:|---|
| **`jjwt-api:0.12.6`** | `implementation` | Chứa các Interface và Contract định nghĩa chuẩn JWT (như `Jwts`, `Claims`, `JwtParser`, `JwtBuilder`). Được dùng trong lúc viết code và compile Java. |
| **`jjwt-impl:0.12.6`** | `runtimeOnly` | Cài đặt thực thi (Runtime Implementation) của các thuật toán mã hóa và giải mã JWT. Không cần gọi trực tiếp trong mã nguồn nên để `runtimeOnly` giúp tối ưu classpath. |
| **`jjwt-jackson:0.12.6`** | `runtimeOnly` | Cung cấp JSON Serializer/Deserializer cho JJWT thông qua thư viện `Jackson` (đã có sẵn trong Spring Boot). Giúp parse Payload (Claims) của JWT thành JSON và ngược lại cực nhanh. |

---

## ⚙️ 3. Cấu Hình Thuộc Tính Bảo Mật Trong `application.yml`

### 3.1. Nội Dung Cấu Hình

Trong file [`application.yml`](../../ticketing/src/main/resources/application.yml), nhóm cấu hình `app.jwt` được bổ sung như sau:

```yaml
app:
  jwt:
    # Khóa bí mật HMAC-SHA256 (Tối thiểu 256-bit / 32 bytes)
    secret: ${APP_JWT_SECRET:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}
    
    # Thời gian sống Access Token: 15 phút = 15 * 60 * 1000 = 900,000 ms
    access-token-expiration-ms: ${APP_JWT_ACCESS_EXPIRATION_MS:900000}
    
    # Thời gian sống Refresh Token: 7 ngày = 7 * 24 * 60 * 60 * 1000 = 604,800,000 ms
    refresh-token-expiration-ms: ${APP_JWT_REFRESH_EXPIRATION_MS:604800000}
```

### 3.2. Giải Thích Chi Tiết Các Tham Số

1. **`secret` (Secret Key):**
   - Sử dụng khóa Hex/Base64 có độ dài **64 ký tự hex (tương đương 256 bits)** để đảm bảo an toàn tuyệt đối cho thuật toán ký `HS256` (`HMAC-SHA256`).
   - Cú pháp `${APP_JWT_SECRET:default_key}`:
     - Khi chạy Local: Tự động dùng fallback key mặc định phía sau dấu `:`.
     - Khi deploy Production: Nhận giá trị từ biến môi trường `APP_JWT_SECRET` được truyền từ file `.env` hoặc Docker Compose / Kubernetes Secrets.

2. **`access-token-expiration-ms` (Thời Hạn Access Token - 15 phút):**
   - Access Token sống ngắn (15 phút) để nếu lỡ bị lộ trên đường truyền mạng (Man-In-The-Middle), kẻ tấn công cũng chỉ khai thác được trong thời gian rất ngắn.
   - Khi hết hạn, Client tự động dùng Refresh Token để lấy token mới mà không làm gián đoạn trải nghiệm người dùng.

3. **`refresh-token-expiration-ms` (Thời Hạn Refresh Token - 7 ngày):**
   - Refresh Token sống dài (7 ngày) và được lưu trữ dạng băm SHA-256 trong Database.
   - Cho phép người dùng duy trì đăng nhập trên ứng dụng trong 7 ngày mà không phải nhập lại mật khẩu.

---

## 🔒 4. Best Practices Về Bảo Mật Khi Đưa Lên Production

1. **Không Bao Giờ Commit Khóa Thật Lên Git:** Khóa Production phải được sinh ngẫu nhiên bằng công cụ bảo mật (ví dụ: `openssl rand -hex 32`) và inject qua CI/CD Pipeline hoặc file `.env`.
2. **Key Rotation (Đổi Khóa Định Kỳ):** Thiết kế cho phép cập nhật `APP_JWT_SECRET` mà không làm sập server; các token cũ sẽ tự động hết hạn và người dùng chỉ cần login lại.
