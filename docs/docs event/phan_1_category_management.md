# 🏷️ PHẦN 1: QUẢN LÝ DANH MỤC SỰ KIỆN (CATEGORY MANAGEMENT)
## Smart Event Ticketing Platform — Module Event · Sub-module Category

**Ngày hoàn thành:** 18/08/2026  
**Trạng thái:** Hoàn thành 100% · Biên dịch thành công (`BUILD SUCCESSFUL`)  
**Tác giả / Hệ thống:** Backend Engineering Team  

---

## 🧭 1. Tổng Quan Bài Toán & Vai Trò Nghiệp Vụ

Trong hệ thống bán vé sự kiện, **Danh mục (Category)** đóng vai trò phân loại sự kiện theo chủ đề, giúp người dùng lọc và tìm kiếm sự kiện nhanh chóng. Ví dụ:
* 🎵 **Âm nhạc & Concert** — Các buổi biểu diễn ca nhạc, liveshow.
* ⚽ **Thể thao** — Giải bóng đá, đua xe, marathon.
* 🎓 **Workshop & Hội thảo** — Khóa học, seminar chuyên đề.
* 🎭 **Nghệ thuật & Sân khấu** — Kịch, múa, triển lãm.

Mỗi sự kiện (Event) có thể thuộc **nhiều danh mục** cùng lúc (quan hệ N:N thông qua bảng `event_categories`).

### 💡 Quy tắc nghiệp vụ:
1. **Chỉ Admin mới có quyền** tạo, sửa, xóa danh mục — vì đây là dữ liệu cấp hệ thống.
2. **Mọi người đều xem được** danh sách danh mục (kể cả chưa đăng nhập) — phục vụ lọc sự kiện trên trang chủ.
3. **Soft Delete** — Xóa danh mục chỉ đổi `status = "INACTIVE"`, không xóa cứng khỏi DB vì các sự kiện cũ vẫn đang tham chiếu tới danh mục đó.
4. **Slug URL-friendly** — Tên danh mục được chuyển thành chuỗi slug để dùng làm đường dẫn URL thân thiện với SEO.

```mermaid
flowchart TD
    Admin([Admin]) -->|POST / PUT / DELETE| Controller["CategoryController\n/api/v1/categories"]
    Public([Mọi người\nKhông cần Login]) -->|GET| Controller
    Controller -->|@Valid + @PreAuthorize| Service["CategoryServiceImpl"]
    Service -->|Slugify| Slug["Sinh slug URL-friendly\nVD: Âm Nhạc → am-nhac"]
    Service -->|CRUD| Repository["CategoryRepository\nJPA"]
    Repository --> DB[(PostgreSQL\nBảng: categories)]
```

---

## 🗃️ 2. Lược Đồ Database (`categories`)

Được quản lý bởi Flyway Migration [`V4__event_schema.sql`](../../ticketing/src/main/resources/db/migration/V4__event_schema.sql):

```sql
CREATE TABLE categories (
    id          UUID PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    slug        VARCHAR(100) NOT NULL UNIQUE,    -- URL-friendly, ví dụ: "am-nhac-concert"
    description TEXT,
    status      VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_categories_slug ON categories(slug);
```

| Cột | Kiểu | Mô tả | Ràng buộc |
|---|---|---|---|
| `id` | UUID | Khóa chính, tự sinh | PRIMARY KEY |
| `name` | VARCHAR(100) | Tên hiển thị: "Âm Nhạc & Concert" | NOT NULL |
| `slug` | VARCHAR(100) | Chuỗi URL: "am-nhac-concert" | NOT NULL, UNIQUE |
| `description` | TEXT | Mô tả chi tiết danh mục | Nullable |
| `status` | VARCHAR(30) | Trạng thái: `ACTIVE` / `INACTIVE` | NOT NULL, DEFAULT `'ACTIVE'` |
| `created_at` | TIMESTAMPTZ | Thời điểm tạo | NOT NULL, DEFAULT NOW() |
| `updated_at` | TIMESTAMPTZ | Thời điểm cập nhật cuối | NOT NULL, DEFAULT NOW() |

---

## 🏗️ 3. Kiến Trúc Code & Danh Sách File

```
modules/event/
  ├── entity/
  │     └── Category.java              ← JPA Entity, kế thừa BaseEntity
  ├── repository/
  │     └── CategoryRepository.java    ← Spring Data JPA Repository
  ├── dto/
  │     ├── request/
  │     │     └── CategoryRequest.java ← Request DTO (Java Record)
  │     └── response/
  │           └── CategoryResponse.java← Response DTO (Java Record + static factory)
  ├── service/
  │     ├── CategoryService.java       ← Interface hợp đồng 5 phương thức
  │     └── impl/
  │           └── CategoryServiceImpl.java ← Business Logic Implementation
  ├── controller/
  │     └── CategoryController.java    ← REST API Endpoints
  └── exception/
        └── EventException.java        ← Exception dùng chung cho module event
```

---

## 📦 4. Chi Tiết Từng Tầng (Layer-by-Layer)

### 🔹 4.1. Entity — [`Category.java`](../../ticketing/src/main/java/com/smartevent/modules/event/entity/Category.java)

```java
@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "categories")
public class Category extends BaseEntity {
    private String name;            // Tên danh mục
    private String slug;            // URL slug (unique)
    private String description;     // Mô tả
    private String status = "ACTIVE"; // Trạng thái mặc định

    public Category(String name, String slug, String description) { ... }
}
```

**Điểm thiết kế quan trọng:**
* Kế thừa `BaseEntity` → có sẵn `id` (UUID, `@UuidGenerator`), `createdAt`, `updatedAt` tự động quản lý bởi JPA `@PrePersist` / `@PreUpdate`.
* `status` mặc định `"ACTIVE"` → Constructor không cần truyền vào, Java tự gán.

---

### 🔹 4.2. Repository — [`CategoryRepository.java`](../../ticketing/src/main/java/com/smartevent/modules/event/repository/CategoryRepository.java)

```java
@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {
    Optional<Category> findBySlug(String slug);      // Tìm theo slug cho API công khai
    boolean existsBySlug(String slug);               // Kiểm tra trùng slug khi tạo/sửa
    List<Category> findByStatus(String status);      // Lấy danh mục ACTIVE cho người dùng xem
}
```

**Tại sao cần cả `findBySlug` lẫn `existsBySlug`?**
* `existsBySlug` → Kiểm tra nhanh (SQL `SELECT 1 WHERE slug = ?`) khi tạo/sửa danh mục.
* `findBySlug` → Trả về đối tượng `Category` đầy đủ khi người dùng truy cập URL `/categories/am-nhac`.

---

### 🔹 4.3. DTOs — Request & Response

#### [`CategoryRequest.java`](../../ticketing/src/main/java/com/smartevent/modules/event/dto/request/CategoryRequest.java) (Input từ Client)

```java
public record CategoryRequest(
    @NotBlank(message = "Danh mục không được để trống") String name,
    String description
) {}
```

* Client **không gửi `slug`** → Server tự động sinh slug từ `name` bằng thư viện `Slugify`.
* Client **không gửi `status`** → Mặc định `ACTIVE` khi tạo mới.

#### [`CategoryResponse.java`](../../ticketing/src/main/java/com/smartevent/modules/event/dto/response/CategoryResponse.java) (Output trả về Client)

```java
public record CategoryResponse(UUID id, String name, String slug, String description,
                                String status, Instant createdAt) {
    public static CategoryResponse from(Category category) { ... }  // Static Factory Method
}
```

* Dùng `static factory method from(Category)` thay vì constructor thủ công → Code gọn hơn khi mapping `entity → DTO`.

---

### 🔹 4.4. Service Layer — Logic Nghiệp Vụ Chi Tiết

#### Interface — [`CategoryService.java`](../../ticketing/src/main/java/com/smartevent/modules/event/service/CategoryService.java)

```java
public interface CategoryService {
    CategoryResponse createCategory(CategoryRequest request);
    List<CategoryResponse> getAllActiveCategories();
    CategoryResponse getCategoryBySlug(String slug);
    CategoryResponse updateCategory(UUID id, CategoryRequest request);
    void deleteCategory(UUID id);
}
```

#### Implementation — [`CategoryServiceImpl.java`](../../ticketing/src/main/java/com/smartevent/modules/event/service/impl/CategoryServiceImpl.java)

**Dependencies:**
```java
@Slf4j @Service @RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {
    private final CategoryRepository categoryRepository;
    private final Slugify slugify = Slugify.builder().lowerCase(true).build();
}
```

#### 📌 Nghiệp vụ 1: `createCategory(CategoryRequest request)`

```
① Sinh slug từ tên: slugify.slugify("Âm Nhạc & Concert") → "am-nhac-concert"
② Kiểm tra trùng slug trong DB → Nếu trùng → Ném BUSINESS_RULE_VIOLATION
③ Tạo entity Category(name, slug, description) → Lưu DB
④ Trả về CategoryResponse.from(savedCategory)
```

**Tại sao kiểm tra trùng `slug` chứ không phải trùng `name`?**
* Hai tên khác nhau có thể sinh ra cùng một slug (ví dụ: "Âm-Nhạc" vs "Âm Nhạc" đều ra `"am-nhac"`).
* Slug là giá trị dùng trong URL, **phải duy nhất** để tránh xung đột routing.

---

#### 📌 Nghiệp vụ 2: `getAllActiveCategories()`

```
① Query: categoryRepository.findByStatus("ACTIVE")
② Stream → map(CategoryResponse::from) → toList()
```

* Annotation `@Transactional(readOnly = true)` → Tối ưu hiệu suất đọc (Hibernate không cần flush dirty checking).

---

#### 📌 Nghiệp vụ 3: `getCategoryBySlug(String slug)`

```
① findBySlug(slug) → orElseThrow(RESOURCE_NOT_FOUND)
② Trả về CategoryResponse
```

* Tìm theo **slug** (cho SEO) chứ không phải UUID (cho nội bộ).

---

#### 📌 Nghiệp vụ 4: `updateCategory(UUID id, CategoryRequest request)`

```
① findById(id) → orElseThrow(RESOURCE_NOT_FOUND)
② Sinh slug mới từ request.name()
③ Kiểm tra: slug mới ≠ slug cũ AND slug mới đã tồn tại → Ném lỗi trùng
④ Cập nhật name, slug, description → save → Trả về Response
```

**Logic điều kiện bước ③ rất quan trọng:**
* Nếu Admin chỉ sửa `description` mà giữ nguyên `name` → slug mới = slug cũ → **KHÔNG ném lỗi trùng** (vì đang update chính nó).
* Chỉ ném lỗi khi slug thay đổi VÀ trùng với danh mục khác.

---

#### 📌 Nghiệp vụ 5: `deleteCategory(UUID id)` — Soft Delete

```
① findById(id) → orElseThrow(RESOURCE_NOT_FOUND)
② category.setStatus("INACTIVE")
③ categoryRepository.save(category)   ← LƯU lại, KHÔNG XÓA
```

**Tại sao Soft Delete?**
* Bảng `event_categories` có khóa ngoại `REFERENCES categories(id) ON DELETE CASCADE`.
* Nếu xóa cứng (DELETE) → Tất cả liên kết event ↔ category sẽ bị xóa theo → **Mất dữ liệu!**
* Soft Delete (đổi status) giữ nguyên dữ liệu lịch sử, chỉ ẩn khỏi giao diện người dùng.

---

### 🔹 4.5. Controller — [`CategoryController.java`](../../ticketing/src/main/java/com/smartevent/modules/event/controller/CategoryController.java)

```java
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {
    private final CategoryService categoryService;
    // ... 5 endpoints
}
```

---

## 🌐 5. Danh Sách REST API Endpoints

| HTTP Method | Endpoint | Phân Quyền | Tham Số | Mô Tả |
|:---:|---|:---:|---|---|
| `POST` | `/api/v1/categories` | `ADMIN` | Body: `{ "name": "...", "description": "..." }` | Tạo danh mục mới, tự sinh slug |
| `GET` | `/api/v1/categories` | **Công khai** | Không | Lấy tất cả danh mục `ACTIVE` |
| `GET` | `/api/v1/categories/{slug}` | **Công khai** | Path: `slug` (String) | Xem chi tiết danh mục theo slug |
| `PUT` | `/api/v1/categories/{id}` | `ADMIN` | Path: `id` (UUID), Body: `{ "name": "...", "description": "..." }` | Cập nhật danh mục |
| `DELETE` | `/api/v1/categories/{id}` | `ADMIN` | Path: `id` (UUID) | Soft Delete (đổi INACTIVE) |

### Ví dụ Request / Response:

**POST `/api/v1/categories`** — Tạo danh mục mới:
```json
// Request Body
{
    "name": "Âm Nhạc & Concert",
    "description": "Các buổi biểu diễn ca nhạc, liveshow, festival âm nhạc"
}

// Response 200
{
    "success": true,
    "data": {
        "id": "550e8400-e29b-41d4-a716-446655440001",
        "name": "Âm Nhạc & Concert",
        "slug": "am-nhac-concert",
        "description": "Các buổi biểu diễn ca nhạc, liveshow, festival âm nhạc",
        "status": "ACTIVE",
        "createdAt": "2026-08-18T04:00:00Z"
    }
}
```

**DELETE `/api/v1/categories/{id}`** — Xóa danh mục (Soft Delete):
```json
// Response 200
{
    "success": true,
    "message": "Xóa danh mục thành công"
}
```

---

## 🔐 6. Cấu Hình Bảo Mật (SecurityConfig)

Trong [`SecurityConfig.java`](../../ticketing/src/main/java/com/smartevent/config/SecurityConfig.java), endpoint GET được mở công khai:

```java
.requestMatchers(HttpMethod.GET, "/api/v1/categories/**").permitAll()
```

* **GET** → `permitAll()`: Ai cũng xem được danh mục, không cần token.
* **POST / PUT / DELETE** → `@PreAuthorize("hasRole('ADMIN')")`: Bắt buộc login bằng tài khoản Admin.

---

## ⚠️ 7. Exception Handling — [`EventException.java`](../../ticketing/src/main/java/com/smartevent/modules/event/exception/EventException.java)

```java
public class EventException extends BusinessException {
    public EventException(ErrorCode errorCode) { super(errorCode); }
    public EventException(ErrorCode errorCode, String detail) { super(errorCode, detail); }
}
```

Kế thừa `BusinessException` → Được bắt bởi `GlobalExceptionHandler` → Trả response lỗi chuẩn hóa:

| Tình huống lỗi | ErrorCode | Chi tiết |
|---|---|---|
| Tạo danh mục trùng slug | `BUSINESS_RULE_VIOLATION` | "Danh mục này đã tồn tại" |
| Sửa danh mục trùng slug | `BUSINESS_RULE_VIOLATION` | "Slug đã bị trùng" |
| Tìm danh mục không tồn tại | `RESOURCE_NOT_FOUND` | "Không tìm thấy danh mục" |

---

## 🔑 8. Thư Viện Bên Ngoài: Slugify

**Dependency (build.gradle):**
```groovy
implementation 'com.github.slugify:slugify:3.0.7'
```

**Cách hoạt động:**
```
Input: "Âm Nhạc & Concert"  → Output: "am-nhac-concert"
Input: "Thể Thao Điện Tử"   → Output: "the-thao-dien-tu"
Input: "Workshop & Hội Thảo" → Output: "workshop-hoi-thao"
```

Slugify tự động:
* Loại bỏ dấu tiếng Việt (Unicode → ASCII).
* Chuyển chữ hoa → chữ thường.
* Thay khoảng trắng và ký tự đặc biệt thành dấu gạch ngang `-`.
