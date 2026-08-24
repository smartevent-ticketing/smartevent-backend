# 🏛️ PHẦN 2: QUẢN LÝ ĐỊA ĐIỂM TỔ CHỨC SỰ KIỆN (VENUE MANAGEMENT)
## Smart Event Ticketing Platform — Module Event · Sub-module Venue

**Ngày hoàn thành:** 18/08/2026  
**Trạng thái:** Hoàn thành 100% · Biên dịch thành công (`BUILD SUCCESSFUL`)  
**Tác giả / Hệ thống:** Backend Engineering Team  

---

## 🧭 1. Tổng Quan Bài Toán & Vai Trò Nghiệp Vụ

**Venue (Địa điểm)** là nơi diễn ra sự kiện vật lý. Mỗi sự kiện phải gắn với một địa điểm cụ thể, và hệ thống cần đảm bảo:
* Không cho 2 sự kiện diễn ra tại cùng một địa điểm trong cùng khoảng thời gian.
* Hỗ trợ lọc sự kiện theo thành phố (city).
* Lưu tọa độ GPS (latitude, longitude) để tích hợp bản đồ trên Frontend.

### Ví dụ thực tế:
| Tên Venue | Địa chỉ | Thành phố | Sức chứa |
|---|---|---|---|
| Nhà hát Lớn Hà Nội | 1 Tràng Tiền, Hoàn Kiếm | Hà Nội | 598 |
| Sân vận động Mỹ Đình | Lê Đức Thọ, Nam Từ Liêm | Hà Nội | 40,192 |
| GEM Center | 8 Nguyễn Bỉnh Khiêm, Q.1 | TP. Hồ Chí Minh | 2,500 |
| Phú Thọ Indoor Stadium | 1 Lữ Gia, Q.11 | TP. Hồ Chí Minh | 5,000 |

### 💡 Quy tắc nghiệp vụ:
1. **Admin và Organizer** đều có quyền tạo/sửa địa điểm — vì Organizer cần đăng ký địa điểm mới cho sự kiện của mình.
2. **Chỉ Admin mới có quyền xóa** — vì xóa Venue ảnh hưởng tới nhiều sự kiện khác nhau.
3. **Mọi người đều xem được** danh sách Venue (không cần đăng nhập).
4. **Soft Delete** — Xóa chỉ đổi `status = "INACTIVE"`, giữ nguyên dữ liệu lịch sử.
5. **Chống trùng lặp:** Không cho tạo 2 Venue cùng tên + cùng thành phố (ví dụ: 2 "Nhà hát Lớn" ở "Hà Nội").

```mermaid
flowchart TD
    Admin([Admin]) -->|POST / PUT / DELETE| Controller["VenueController\n/api/v1/venues"]
    Organizer([Organizer]) -->|POST / PUT| Controller
    Public([Mọi người\nKhông cần Login]) -->|GET| Controller
    Controller -->|@Valid + @PreAuthorize| Service["VenueServiceImpl"]
    Service -->|Kiểm tra trùng\nname + city| DupCheck{"existsByNameAndCity?"}
    DupCheck -->|Không trùng| Repository["VenueRepository\nJPA"]
    DupCheck -->|Trùng| Error["EventException\nBUSINESS_RULE_VIOLATION"]
    Repository --> DB[(PostgreSQL\nBảng: venues)]
```

---

## 🗃️ 2. Lược Đồ Database (`venues`)

Được quản lý bởi Flyway Migration [`V4__event_schema.sql`](../../ticketing/src/main/resources/db/migration/V4__event_schema.sql):

```sql
CREATE TABLE venues (
    id          UUID PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    address     VARCHAR(500) NOT NULL,
    city        VARCHAR(100) NOT NULL,
    latitude    DECIMAL(9,6),             -- Vĩ độ GPS (tùy chọn)
    longitude   DECIMAL(9,6),             -- Kinh độ GPS (tùy chọn)
    capacity    INT,                       -- Sức chứa tối đa (tùy chọn)
    status      VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_venue_capacity CHECK (capacity IS NULL OR capacity > 0)
);

CREATE INDEX idx_venues_city ON venues(city);
CREATE INDEX idx_venues_status ON venues(status);
```

| Cột | Kiểu | Mô tả | Ràng buộc |
|---|---|---|---|
| `id` | UUID | Khóa chính, tự sinh | PRIMARY KEY |
| `name` | VARCHAR(255) | Tên địa điểm: "Nhà hát Lớn Hà Nội" | NOT NULL |
| `address` | VARCHAR(500) | Địa chỉ chi tiết: "1 Tràng Tiền, Hoàn Kiếm" | NOT NULL |
| `city` | VARCHAR(100) | Thành phố: "Hà Nội" | NOT NULL |
| `latitude` | DECIMAL(9,6) | Vĩ độ: 21.024500 | Nullable |
| `longitude` | DECIMAL(9,6) | Kinh độ: 105.858100 | Nullable |
| `capacity` | INT | Sức chứa tối đa | Nullable, CHECK > 0 |
| `status` | VARCHAR(30) | `ACTIVE` / `INACTIVE` | NOT NULL, DEFAULT `'ACTIVE'` |
| `created_at` | TIMESTAMPTZ | Thời điểm tạo | NOT NULL |
| `updated_at` | TIMESTAMPTZ | Thời điểm cập nhật | NOT NULL |

### ⚠️ Lưu ý kỹ thuật:
* **`latitude` / `longitude` dùng `DECIMAL(9,6)`:** 9 chữ số tổng, 6 chữ số thập phân → Độ chính xác ~11cm, đủ dùng cho bản đồ.
* **`capacity` có CHECK constraint:** Đảm bảo nếu điền sức chứa thì phải > 0 (không cho nhập số âm hoặc 0).
* **2 Indexes:** `idx_venues_city` (lọc theo thành phố nhanh) và `idx_venues_status` (lọc ACTIVE nhanh).

---

## 🏗️ 3. Kiến Trúc Code & Danh Sách File

```
modules/event/
  ├── entity/
  │     └── Venue.java                 ← JPA Entity, kế thừa BaseEntity
  ├── repository/
  │     └── VenueRepository.java       ← Spring Data JPA Repository
  ├── dto/
  │     ├── request/
  │     │     └── VenueRequest.java    ← Request DTO (Java Record + Validation)
  │     └── response/
  │           └── VenueResponse.java   ← Response DTO (Java Record + static factory)
  ├── service/
  │     ├── VenueService.java          ← Interface hợp đồng 6 phương thức
  │     └── impl/
  │           └── VenueServiceImpl.java← Business Logic Implementation
  ├── controller/
  │     └── VenueController.java       ← REST API Endpoints
  └── exception/
        └── EventException.java        ← Exception dùng chung cho module event
```

---

## 📦 4. Chi Tiết Từng Tầng (Layer-by-Layer)

### 🔹 4.1. Entity — [`Venue.java`](../../ticketing/src/main/java/com/smartevent/modules/event/entity/Venue.java)

```java
@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "venues")
public class Venue extends BaseEntity {
    private String name;             // Tên địa điểm
    private String address;          // Địa chỉ chi tiết
    private String city;             // Thành phố
    private BigDecimal latitude;     // Vĩ độ GPS
    private BigDecimal longitude;    // Kinh độ GPS
    private Integer capacity;        // Sức chứa tối đa
    private String status = "ACTIVE";// Trạng thái mặc định

    public Venue(String name, String address, String city,
                 BigDecimal latitude, BigDecimal longitude, Integer capacity) { ... }
}
```

**So sánh với Category Entity:**
| Đặc điểm | Category | Venue |
|---|---|---|
| Nhận dạng duy nhất | `slug` (UNIQUE) | `name + city` (tổ hợp) |
| URL-friendly | Có slug | Không cần (dùng UUID) |
| Trường tùy chọn | `description` | `latitude`, `longitude`, `capacity` |
| Kiểu dữ liệu đặc biệt | Không | `BigDecimal` cho tọa độ GPS |

---

### 🔹 4.2. Repository — [`VenueRepository.java`](../../ticketing/src/main/java/com/smartevent/modules/event/repository/VenueRepository.java)

```java
@Repository
public interface VenueRepository extends JpaRepository<Venue, UUID> {
    // Lấy danh sách các Venue đang hoạt động
    List<Venue> findByStatus(String status);

    // Lọc Venue theo thành phố (không phân biệt hoa/thường)
    List<Venue> findByCityIgnoreCaseAndStatus(String city, String status);

    // Kiểm tra trùng lặp: cùng tên + cùng thành phố
    boolean existsByNameAndCity(String name, String city);
}
```

**Tại sao `findByCityIgnoreCaseAndStatus`?**
* Người dùng có thể nhập "hà nội", "Hà Nội", hay "HÀ NỘI" → Tất cả phải trả cùng kết quả.
* `IgnoreCase` → Spring Data JPA tự sinh SQL `LOWER(city) = LOWER(?)`.

**Tại sao kiểm tra trùng bằng `name + city` thay vì chỉ `name`?**
* Hai thành phố khác nhau có thể có cùng tên venue. Ví dụ: "Trung tâm Hội nghị" có thể có ở cả "Hà Nội" và "Đà Nẵng" → Hoàn toàn hợp lệ.

---

### 🔹 4.3. DTOs — Request & Response

#### [`VenueRequest.java`](../../ticketing/src/main/java/com/smartevent/modules/event/dto/request/VenueRequest.java) (Input từ Client)

```java
public record VenueRequest(
    @NotBlank(message = "Tên địa điểm không được phép để trống!") String name,
    @NotBlank(message = "Địa chỉ không được phép để trống!")      String address,
    @NotBlank(message = "Vui lòng điền thông tin thành phố")      String city,
    BigDecimal latitude,     // Tùy chọn
    BigDecimal longitude,    // Tùy chọn
    @Positive Integer capacity  // Phải > 0 nếu có điền
) {}
```

**Validation chặt chẽ:**
* `name`, `address`, `city` → `@NotBlank`: Bắt buộc phải điền, không được để trống hoặc chỉ có khoảng trắng.
* `capacity` → `@Positive`: Nếu điền thì phải > 0. Nếu không điền (null) thì bỏ qua.
* `latitude`, `longitude` → Không bắt buộc (một số venue có thể chưa có tọa độ GPS).

#### [`VenueResponse.java`](../../ticketing/src/main/java/com/smartevent/modules/event/dto/response/VenueResponse.java) (Output trả về Client)

```java
public record VenueResponse(UUID id, String name, String address, String city,
                             BigDecimal latitude, BigDecimal longitude,
                             Integer capacity, String status, Instant createdAt) {
    public static VenueResponse from(Venue venue) { ... }  // Static Factory Method
}
```

---

### 🔹 4.4. Service Layer — Logic Nghiệp Vụ Chi Tiết

#### Interface — [`VenueService.java`](../../ticketing/src/main/java/com/smartevent/modules/event/service/VenueService.java)

```java
public interface VenueService {
    VenueResponse createVenue(VenueRequest request);
    List<VenueResponse> getAllActiveVenues();
    List<VenueResponse> getVenuesByCity(String city);
    VenueResponse getVenueById(UUID id);
    VenueResponse updateVenue(UUID id, VenueRequest request);
    void deleteVenue(UUID id);
}
```

**So sánh với CategoryService:**
* Venue có thêm 1 phương thức: `getVenuesByCity(String city)` — lọc theo thành phố.
* Venue tìm bằng `UUID id` thay vì `slug` — vì Venue không cần URL SEO-friendly.

#### Implementation — [`VenueServiceImpl.java`](../../ticketing/src/main/java/com/smartevent/modules/event/service/impl/VenueServiceImpl.java)

**Dependencies:**
```java
@Slf4j @Service @RequiredArgsConstructor
public class VenueServiceImpl implements VenueService {
    private final VenueRepository venueRepository;
    // Không cần Slugify — Venue không dùng slug
}
```

---

#### 📌 Nghiệp vụ 1: `createVenue(VenueRequest request)`

```
① Kiểm tra: existsByNameAndCity(name, city)?
   → Nếu đã tồn tại → Ném BUSINESS_RULE_VIOLATION "Địa điểm đã tồn tại"
② Tạo entity Venue(name, address, city, latitude, longitude, capacity)
③ Lưu DB → Trả về VenueResponse.from(savedVenue)
```

**Tại sao không dùng UNIQUE constraint trên DB mà kiểm tra ở tầng Service?**
* Việc kiểm tra ở Service cho phép trả thông báo lỗi rõ ràng bằng tiếng Việt cho client.
* Nếu chỉ dùng DB constraint, client sẽ nhận lỗi `DataIntegrityViolationException` khó hiểu.

---

#### 📌 Nghiệp vụ 2: `getAllActiveVenues()`

```
① venueRepository.findByStatus("ACTIVE")
② Stream → map(VenueResponse::from) → toList()
```

---

#### 📌 Nghiệp vụ 3: `getVenuesByCity(String city)`

```
① venueRepository.findByCityIgnoreCaseAndStatus(city, "ACTIVE")
② Nếu kết quả null → Ném RESOURCE_NOT_FOUND
③ Stream → map → toList()
```

* Tìm kiếm **không phân biệt hoa/thường** (case-insensitive) → "hà nội" = "Hà Nội".

---

#### 📌 Nghiệp vụ 4: `getVenueById(UUID id)`

```
① findById(id) → orElseThrow(RESOURCE_NOT_FOUND)
② Trả về VenueResponse
```

---

#### 📌 Nghiệp vụ 5: `updateVenue(UUID id, VenueRequest request)` — Logic Chống Trùng Thông Minh

```
① findById(id) → orElseThrow(RESOURCE_NOT_FOUND)
② Kiểm tra trùng lặp THÔNG MINH:
   boolean isDuplicate = existsByNameAndCity(newName, newCity);
   boolean isSameVenue = oldName == newName AND oldCity == newCity;
   → Nếu isDuplicate AND !isSameVenue → Ném lỗi trùng
③ Cập nhật TẤT CẢ 6 trường: name, address, city, latitude, longitude, capacity
④ save → Trả về VenueResponse
```

**Bảng minh họa logic chống trùng:**

| Tình huống | isDuplicate | isSameVenue | Kết quả |
|---|:---:|:---:|---|
| Sửa capacity, giữ nguyên name + city | `true` | `true` | ✅ Cho phép (đang sửa chính mình) |
| Đổi tên thành tên mới chưa ai dùng | `false` | `false` | ✅ Cho phép |
| Đổi tên thành tên trùng venue khác ở cùng city | `true` | `false` | ❌ Ném lỗi |

---

#### 📌 Nghiệp vụ 6: `deleteVenue(UUID id)` — Soft Delete

```
① findById(id) → orElseThrow(RESOURCE_NOT_FOUND)
② venue.setStatus("INACTIVE")
③ venueRepository.save(venue)   ← LƯU, KHÔNG XÓA
```

* Giống logic Category: Giữ dữ liệu lịch sử, ẩn khỏi danh sách hiển thị.
* Bảng `events` có khóa ngoại `venue_id REFERENCES venues(id) ON DELETE RESTRICT` → Nếu xóa cứng sẽ bị PostgreSQL chặn lại.

---

### 🔹 4.5. Controller — [`VenueController.java`](../../ticketing/src/main/java/com/smartevent/modules/event/controller/VenueController.java)

```java
@RestController
@RequestMapping("/api/v1/venues")
public class VenueController {
    private final VenueService venueService;
    // ... 6 endpoints
}
```

---

## 🌐 5. Danh Sách REST API Endpoints

| HTTP Method | Endpoint | Phân Quyền | Tham Số | Mô Tả |
|:---:|---|:---:|---|---|
| `POST` | `/api/v1/venues` | `ADMIN`, `ORGANIZER` | Body: VenueRequest JSON | Tạo địa điểm mới |
| `GET` | `/api/v1/venues` | **Công khai** | Không | Lấy tất cả Venue `ACTIVE` |
| `GET` | `/api/v1/venues/{id}` | **Công khai** | Path: `id` (UUID) | Xem chi tiết Venue theo ID |
| `GET` | `/api/v1/venues/city/{city}` | **Công khai** | Path: `city` (String) | Lọc Venue theo thành phố |
| `PUT` | `/api/v1/venues/{id}` | `ADMIN`, `ORGANIZER` | Path: `id` (UUID), Body: VenueRequest | Cập nhật Venue |
| `DELETE` | `/api/v1/venues/{id}` | `ADMIN` | Path: `id` (UUID) | Soft Delete Venue |

### Ví dụ Request / Response:

**POST `/api/v1/venues`** — Tạo địa điểm mới:
```json
// Request Body
{
    "name": "Nhà hát Lớn Hà Nội",
    "address": "1 Tràng Tiền, Hoàn Kiếm",
    "city": "Hà Nội",
    "latitude": 21.024500,
    "longitude": 105.858100,
    "capacity": 598
}

// Response 200
{
    "success": true,
    "data": {
        "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
        "name": "Nhà hát Lớn Hà Nội",
        "address": "1 Tràng Tiền, Hoàn Kiếm",
        "city": "Hà Nội",
        "latitude": 21.024500,
        "longitude": 105.858100,
        "capacity": 598,
        "status": "ACTIVE",
        "createdAt": "2026-08-18T04:00:00Z"
    }
}
```

**GET `/api/v1/venues/city/Hà Nội`** — Lọc theo thành phố:
```json
// Response 200
{
    "success": true,
    "data": [
        {
            "id": "7c9e6679-...",
            "name": "Nhà hát Lớn Hà Nội",
            "city": "Hà Nội",
            ...
        },
        {
            "id": "a1b2c3d4-...",
            "name": "Sân vận động Mỹ Đình",
            "city": "Hà Nội",
            ...
        }
    ]
}
```

---

## 🔐 6. Cấu Hình Bảo Mật (SecurityConfig)

Trong [`SecurityConfig.java`](../../ticketing/src/main/java/com/smartevent/config/SecurityConfig.java):

```java
.requestMatchers(HttpMethod.GET, "/api/v1/venues/**").permitAll()
```

| Hành động | Phân quyền | Annotation |
|---|---|---|
| Tạo / Sửa Venue | Admin + Organizer | `@PreAuthorize("hasAnyRole('ADMIN','ORGANIZER')")` |
| Xóa Venue | Chỉ Admin | `@PreAuthorize("hasRole('ADMIN')")` |
| Xem Venue | Mọi người | Không cần `@PreAuthorize`, được `permitAll()` ở SecurityConfig |

---

## ⚠️ 7. Exception Handling — [`EventException.java`](../../ticketing/src/main/java/com/smartevent/modules/event/exception/EventException.java)

```java
public class EventException extends BusinessException {
    public EventException(ErrorCode errorCode) { super(errorCode); }
    public EventException(ErrorCode errorCode, String detail) { super(errorCode, detail); }
}
```

| Tình huống lỗi | ErrorCode | Chi tiết |
|---|---|---|
| Tạo Venue trùng name + city | `BUSINESS_RULE_VIOLATION` | "Địa điểm đã tồn tại" |
| Update trùng Venue khác | `BUSINESS_RULE_VIOLATION` | "Địa điểm đã tồn tại" |
| Tìm Venue không tồn tại | `RESOURCE_NOT_FOUND` | "Không tìm thấy địa điểm" |

---

## 🔄 8. So Sánh Tổng Quan: Category vs Venue

| Tiêu chí | Category | Venue |
|---|---|---|
| **Mục đích** | Phân loại sự kiện theo chủ đề | Quản lý nơi diễn ra sự kiện |
| **Nhận dạng duy nhất** | `slug` (URL-friendly) | Tổ hợp `name + city` |
| **Thư viện bên ngoài** | `Slugify` (sinh slug) | Không cần |
| **Ai tạo/sửa?** | Chỉ Admin | Admin + Organizer |
| **Ai xóa?** | Chỉ Admin | Chỉ Admin |
| **Ai xem?** | Mọi người | Mọi người |
| **Cơ chế xóa** | Soft Delete (INACTIVE) | Soft Delete (INACTIVE) |
| **Số endpoint** | 5 | 6 (thêm lọc theo city) |
| **FK từ bảng events** | N:N qua `event_categories` | 1:N qua `venue_id` |
| **ON DELETE** | CASCADE (xóa liên kết) | RESTRICT (chặn xóa) |
