# 🗄️ PHẦN 2: THIẾT KẾ THỰC THỂ (ENTITIES) & TẦNG TRUY CẬP DỮ LIỆU (REPOSITORIES)
## Smart Event Ticketing Platform — Module Identity & Authentication

**Ngày tạo:** 16/08/2026  
**Trạng thái:** Hoàn thành & Đã kiểm tra biên dịch (`BUILD SUCCESSFUL`)  
**Tác giả / Hệ thống:** Backend Engineering Team  

---

## 🧭 1. Tổng Quan Sơ Đồ ERD Module Identity (5 Bảng)

Module Identity quản lý toàn bộ tài khoản người dùng, ban tổ chức, vai trò phân quyền và các phiên đăng nhập dài hạn:

```mermaid
erDiagram
    users {
        UUID id PK
        VARCHAR email UK
        VARCHAR password_hash
        VARCHAR full_name
        VARCHAR phone
        UUID avatar_file_id FK "→ files (V3), nullable"
        VARCHAR status "ACTIVE / DISABLED / DELETED"
        TIMESTAMPTZ deleted_at "Soft Delete (V2)"
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }
    roles {
        UUID id PK
        VARCHAR name UK "CUSTOMER / ORGANIZER / ADMIN"
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }
    user_roles {
        UUID id PK
        UUID user_id FK "→ users"
        UUID role_id FK "→ roles"
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }
    organizer_profiles {
        UUID id PK
        UUID user_id FK UK "→ users (1:1)"
        VARCHAR company_name
        VARCHAR tax_code
        TEXT business_address
        VARCHAR bank_account_status "PENDING / APPROVED"
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }
    refresh_tokens {
        UUID id PK
        UUID user_id FK "→ users (N:1, ON DELETE CASCADE)"
        VARCHAR token_hash UK
        TIMESTAMPTZ expires_at
        TIMESTAMPTZ revoked_at "nullable"
        TIMESTAMPTZ created_at
    }

    users ||--o{ user_roles : "has"
    roles ||--o{ user_roles : "assigned"
    users ||--o| organizer_profiles : "1:1 profile"
    users ||--o{ refresh_tokens : "1:N sessions"
```

---

## 🏛️ 2. Chi Tiết Các Thực Thể JPA (JPA Entities)

### 2.1. [`User.java`](../../ticketing/src/main/java/com/smartevent/modules/identity/entity/User.java) — Thực Thể Người Dùng Trung Tâm

* **Kế thừa `SoftDeleteEntity`:** Tự động thừa hưởng `id` (UUID), `createdAt`, `updatedAt` và `deletedAt` cùng các helper methods:
  - `softDelete()`: Đánh dấu `deletedAt = Instant.now()`.
  - `restore()`: Phục hồi tài khoản (`deletedAt = null`).
  - `isDeleted()`: Kiểm tra trạng thái xóa mềm.
* **Cột `avatar_file_id` (UUID):** Lưu khóa ngoại tham chiếu đến bảng `files` (`V3`) trên MinIO.
* **Quan hệ thực thể:**
  - `@OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true) private Set<UserRole> userRoles;`
  - `@OneToOne(mappedBy = "user", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY) private OrganizerProfile organizerProfile;`
  - `@OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true) private List<RefreshToken> refreshTokens;`
* **Helper Methods Nghiệp Vụ:**
  - `addRole(Role role)`: Gán vai trò cho người dùng.
  - `removeRole(Role role)`: Hủy vai trò.
  - `getRoleNames()`: Trích xuất danh sách tên Role (`Set<String>`) cho Security Claims.
  - `isActive()`: Kiểm tra người dùng có đang hoạt động và chưa bị xóa mềm.

---

### 2.2. [`OrganizerProfile.java`](../../ticketing/src/main/java/com/smartevent/modules/identity/entity/OrganizerProfile.java) — Hồ Sơ Ban Tổ Chức Sự Kiện

* **Kế thừa `BaseEntity`:** Tự động sinh `id` (UUID), `createdAt`, `updatedAt`.
* **Quan hệ 1:1 Với User:**
  ```java
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(
          name = "user_id",
          referencedColumnName = "id",
          nullable = false,
          unique = true
  )
  private User user;
  ```
* **Các trường nghiệp vụ:** `companyName` (Tên công ty/tổ chức), `taxCode` (Mã số thuế), `businessAddress` (Địa chỉ trụ sở), `bankAccountStatus` (Trạng thái tài khoản nhận tiền vé, mặc định `'PENDING'`).
* **Lưu ý đã khắc phục:** Đã bổ sung khai báo `private User user;` bị thiếu trước đó phía dưới annotation `@JoinColumn`.

---

### 2.3. [`RefreshToken.java`](../../ticketing/src/main/java/com/smartevent/modules/identity/entity/RefreshToken.java) — Quản Lý Phiên Đăng Nhập Dài Hạn

* **Cấu trúc bảng theo `V2`:** Bảng `refresh_tokens` trong PostgreSQL không có cột `updated_at`, nên `RefreshToken` quản lý trực tiếp các trường:
  - `@Id @UuidGenerator private UUID id;`
  - `@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false) private User user;`
  - `@Column(name = "token_hash", nullable = false, unique = true) private String tokenHash;`
  - `@Column(name = "expires_at", nullable = false) private Instant expiresAt;`
  - `@Column(name = "revoked_at") private Instant revokedAt;`
  - `@Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;`
* **Helper Methods An Ninh:**
  - `isExpired()`: So sánh `expiresAt` với `Instant.now()`.
  - `isRevoked()`: Kiểm tra `revokedAt != null`.
  - `isValid()`: Trả về `true` khi token **chưa hết hạn và chưa bị thu hồi**.
  - `revoke()`: Gán `revokedAt = Instant.now()`.

---

### 2.4. [`Role.java`](../../ticketing/src/main/java/com/smartevent/modules/identity/entity/Role.java) & [`UserRole.java`](../../ticketing/src/main/java/com/smartevent/modules/identity/entity/UserRole.java) — Bảng Phân Quyền N:N

* **`Role`:** Chứa tên quyền hạn duy nhất: `CUSTOMER` (Khách mua vé), `ORGANIZER` (Ban tổ chức tạo sự kiện), `ADMIN` (Quản trị viên nền tảng).
* **`UserRole`:** Bảng trung gian N:N lưu liên kết giữa `User` và `Role`, có `id` riêng và timestamp `createdAt`, `updatedAt`.

---

## 🔍 3. Chi Tiết Tầng Repositories (Spring Data JPA)

### 3.1. [`UserRepository.java`](../../ticketing/src/main/java/com/smartevent/modules/identity/repository/UserRepository.java)

```java
public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    // Nạp User kèm Roles trong 1 query duy nhất - Tối ưu chống LazyInitException
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.userRoles ur LEFT JOIN FETCH ur.role " +
           "WHERE u.email = :email AND u.deletedAt IS NULL")
    Optional<User> findByEmailWithRoles(@Param("email") String email);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.userRoles ur LEFT JOIN FETCH ur.role " +
           "WHERE u.id = :id AND u.deletedAt IS NULL")
    Optional<User> findByIdWithRoles(@Param("id") UUID id);
}
```

> **Tại sao cần `LEFT JOIN FETCH`?**  
> Mặc định quan hệ `userRoles` là `LAZY`. Nếu Spring Security gọi `user.getAuthorities()` ngoài phạm vi Transaction của Hibernate, ứng dụng sẽ bị văng lỗi `LazyInitializationException`. Lệnh `LEFT JOIN FETCH` giải quyết dứt điểm vấn đề này chỉ với 1 câu lệnh SQL JOIN.

---

### 3.2. [`OrganizerProfileRepository.java`](../../ticketing/src/main/java/com/smartevent/modules/identity/repository/OrganizerProfileRepository.java)

```java
public interface OrganizerProfileRepository extends JpaRepository<OrganizerProfile, UUID> {
    Optional<OrganizerProfile> findByUserId(UUID userId);
    boolean existsByUserId(UUID userId);
}
```

---

### 3.3. [`RefreshTokenRepository.java`](../../ticketing/src/main/java/com/smartevent/modules/identity/repository/RefreshTokenRepository.java)

```java
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // Tìm kiếm Refresh Token và nạp sẵn User cùng Roles phục vụ cấp token mới
    @Query("SELECT r FROM RefreshToken r JOIN FETCH r.user u " +
           "LEFT JOIN FETCH u.userRoles ur LEFT JOIN FETCH ur.role " +
           "WHERE r.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashWithUser(@Param("tokenHash") String tokenHash);

    // Thu hồi toàn bộ Refresh Token của 1 User (khi đổi mật khẩu hoặc đăng xuất tất cả thiết bị)
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revokedAt = :revokedAt " +
           "WHERE r.user.id = :userId AND r.revokedAt IS NULL")
    int revokeAllUserTokens(@Param("userId") UUID userId, @Param("revokedAt") Instant revokedAt);
}
```

---

## 📊 4. Tóm Tắt Trạng Thái Sau Phần 2

| Thành Phần | Tên File | Chức Năng | Trạng Thái |
|---|---|---|:---:|
| Entity | `User.java` | Thực thể người dùng + Soft Delete + Avatar | ✅ Hoàn thành |
| Entity | `OrganizerProfile.java` | Hồ sơ ban tổ chức (1:1 với User) | ✅ Hoàn thành |
| Entity | `RefreshToken.java` | Phiên đăng nhập dài hạn | ✅ Hoàn thành |
| Entity | `Role.java` & `UserRole.java` | Phân quyền N:N | ✅ Hoàn thành |
| Repository | `UserRepository.java` | Truy vấn User kèm Eager Fetch Roles | ✅ Hoàn thành |
| Repository | `OrganizerProfileRepository.java` | Truy vấn hồ sơ ban tổ chức | ✅ Hoàn thành |
| Repository | `RefreshTokenRepository.java` | Truy vấn & Thu hồi Token băm SHA-256 | ✅ Hoàn thành |
