# 🌐 CẨM NANG THIẾT KẾ REST CONTROLLER & CÁC CẠM BẪY ĐIỀU HƯỚNG TRONG SPRING BOOT
## Smart Event Ticketing Platform — Controller Architecture, URL Hierarchies & Pitfalls Guide

**Phiên bản:** 1.0  
**Tác giả:** Backend Engineering Team  
**Mục đích:** Giải thích toàn diện tư duy thiết kế REST Controller, cấu trúc phân cấp đường dẫn URL chuẩn RESTful quốc tế, lý do tại sao có Controller dùng `@RequestMapping` ở cấp class còn Controller khác thì không, và các cạm bẫy thực chiến thường gặp.

---

## 🧭 MỤC LỤC

1. [Tư Duy Thiết Kế URL RESTful: Phẳng (Flat) vs Lồng Nhau (Nested)](#1-tư-duy-thiết-kế-url-restful-phẳng-flat-vs-lồng-nhau-nested)
2. [Tại Sao Có Class Dùng `@RequestMapping`, Có Class Lại Không?](#2-tại-sao-có-class-dùng-requestmapping-có-class-lại-không)
3. [6 Cạm Bẫy Thực Chiến Khi Xây Dựng Controller (Common Pitfalls)](#3-6-cạm-bẫy-thực-chiến-khi-xây-dựng-controller-common-pitfalls)
4. [Nguyên Tắc Phân Biệt Các HTTP Annotations (@PathVariable, @RequestParam, @RequestBody)](#4-nguyên-tắc-phân-biệt-các-http-annotations-pathvariable-requestparam-requestbody)
5. [Chuẩn Phân Quyền Bảo Mật Tại Controller (RBAC & Privilege Escalation Prevention)](#5-chuẩn-phân-quyền-bảo-mật-tại-controller-rbac--privilege-escalation-prevention)

---

## 🏛️ 1. TƯ DUY THIẾT KẾ URL RESTFUL: PHẲNG (FLAT) VS LỒNG NHAU (NESTED)

Một câu hỏi cốt lõi trong thiết kế REST API: *"Một khán đài (Area) thuộc về một sự kiện (Event). Vậy URL của nó nên là `/events/{eventId}/areas/{areaId}` hay `/areas/{areaId}`?"*

```
                             ┌──────────────────────────────────────┐
                             │  QUAN HỆ CHA - CON (Event ──> Area)  │
                             └──────────────────┬───────────────────┘
                                                │
                 ┌──────────────────────────────┴──────────────────────────────┐
                 ▼                                                             ▼
     [ 1. KHI TẠO HOẶC LẤY DANH SÁCH ]                             [ 2. KHI ĐÃ CÓ ID CỤ THỂ ]
     (Phải biết Cha là ai)                                         (Thao tác trực tiếp trên Con)
                 │                                                             │
                 ▼                                                             ▼
         NESTED ROUTE (Lồng nhau)                                      FLAT ROUTE (Đường dẫn phẳng)
  POST /api/v1/events/{eventId}/areas                           GET    /api/v1/areas/{id}
  GET  /api/v1/events/{eventId}/areas                           PUT    /api/v1/areas/{id}
                                                                DELETE /api/v1/areas/{id}
```

### 💡 Quy tắc thiết kế chuẩn quốc tế (GitHub / Stripe / Spotify REST Standard):
1. **Dùng URL Lồng Nhau (Nested):** Khi thực hiện hành động **Tạo mới (POST)** hoặc **Liệt kê (GET Collection)** một tài nguyên con phụ thuộc vào cha.
   * `POST /api/v1/events/{eventId}/areas` $\rightarrow$ *"Tạo một khán đài bên trong sự kiện `{eventId}`"*.
   * `GET  /api/v1/events/{eventId}/areas` $\rightarrow$ *"Lấy toàn bộ khán đài thuộc sự kiện `{eventId}`"*.
2. **Dùng URL Phẳng (Flat):** Khi **Sửa (PUT)**, **Xóa (DELETE)**, hoặc **Xem chi tiết (GET One)** một tài nguyên đã có ID duy nhất.
   * `GET    /api/v1/areas/{id}` (Thay vì `/events/{eventId}/areas/{id}` dài dòng và thừa thãi, vì `areaId` đã là khóa chính duy nhất).
   * `DELETE /api/v1/areas/{id}`.

---

## ❓ 2. TẠI SAO CÓ CLASS DÙNG `@RequestMapping`, CÓ CLASS LẠI KHÔNG?

Hãy nhìn vào sự khác biệt giữa `EventController` và `EventAreaController`:

### 🔹 Trường hợp 1: Dùng `@RequestMapping` ở cấp Class (Ví dụ: `EventController`)
Tất cả các endpoint trong `EventController` đều bắt đầu bằng tiền tố chung `/api/v1/events`:
```java
@RestController
@RequestMapping("/api/v1/events") // ✅ Gom tiền tố chung cho cả class
public class EventController {

    @PostMapping         // Đường dẫn thực tế: POST /api/v1/events
    @GetMapping          // Đường dẫn thực tế: GET  /api/v1/events
    @GetMapping("/{id}") // Đường dẫn thực tế: GET  /api/v1/events/{id}
}
```

---

### 🔹 Trường hợp 2: KHÔNG DÙNG `@RequestMapping` ở cấp Class (Ví dụ: `EventAreaController`)
Trong `EventAreaController`, chúng ta có 2 nhóm đường dẫn có tiền tố **hoàn toàn khác nhau**:
1. Nhóm 1: `/api/v1/events/{eventId}/areas` (Tiền tố `/events`)
2. Nhóm 2: `/api/v1/areas/{id}` (Tiền tố `/areas`)

👉 **Điều gì xảy ra nếu cố tình đặt `@RequestMapping("/api/v1/areas")` ở cấp Class?**
```java
// ❌ NẾU ĐẶT Ở ĐÂY:
@RestController
@RequestMapping("/api/v1/areas")
public class EventAreaController {

    // Đường dẫn bị sai thành: POST /api/v1/areas/events/{eventId}/areas (Rất xấu và sai nghĩa!)
    @PostMapping("/events/{eventId}/areas") 
    public ApiResponse<EventAreaResponse> createArea(...) { ... }
}
```

> 🎯 **Quy tắc đúc kết:**
> * Nếu **100% các endpoint** trong class có cùng tiền tố $\rightarrow$ Đặt `@RequestMapping("/api/v1/...")` ở đầu class để tránh lặp code.
> * Nếu class chứa cả **Nested Route** (`/events/{id}/areas`) và **Flat Route** (`/areas/{id}`) $\rightarrow$ **Không đặt `@RequestMapping` ở đầu class**, mà ghi rõ đường dẫn đầy đủ trên từng hàm!

---

## ⚠️ 3. SÁU CẠM BẪY THỰC CHIẾN KHI XÂY DỰNG CONTROLLER

---

### 🚨 Cạm bẫy 1: Trùng Lặp Đường Dẫn Gây Nhầm Kiểu (Path Ambiguity / Type Mismatch)

Hãy nhìn 2 hàm sau trong Controller:
```java
@GetMapping("/{id}")        // Mong muốn nhận UUID: ví dụ 7c9e6679-...
public EventResponse getById(@PathVariable UUID id) { ... }

@GetMapping("/my-events")  // Chuỗi tĩnh: "my-events"
public List<EventResponse> getMyEvents() { ... }
```

* **Hiểm họa:** Nếu bạn đặt `/{id}` nằm **TRƯỚC** `/my-events`, Spring MVC có thể cố gắng phân tích chuỗi `"my-events"` thành kiểu `UUID` $\rightarrow$ Ném lỗi `MethodArgumentTypeMismatchException (Failed to convert String to UUID)`.
* **Cách khắc phục:** Luôn đặt các URL có từ khóa tĩnh (như `/my-events`, `/slug/{slug}`, `/available`, `/generate`) **trước** hoặc tách biệt rõ ràng với `/{id}`.

---

### 🚨 Cạm bẫy 2: Lệch Tên Biến `@PathVariable`

```java
// ❌ LỖI:
@GetMapping("/{id}")
public EventResponse getEvent(@PathVariable UUID eventId) // Tên biến 'eventId' khác chữ '{id}' trong URL!
```
* Nếu không chỉ định tên, Spring Boot sẽ tìm `{eventId}` trong URL và ném lỗi vì không tìm thấy.
* **Cách khắc phục:** `@PathVariable UUID id` hoặc `@PathVariable("id") UUID eventId`.

---

### 🚨 Cạm bẫy 3: Lỗ Hổng Nhận `isAdmin` Hoặc `userId` Qua Request

```java
// ❌ LỖ HỔNG BẢO MẬT:
@PutMapping("/{id}")
public EventResponse updateEvent(
    @PathVariable UUID id, 
    boolean isAdmin,        // Client có thể hack qua query param ?isAdmin=true!
    UUID currentUserId      // Client có thể truyền ID của người khác để sửa trộm!
)
```
* **Cách khắc phục:** Luôn lấy danh tính và quyền hạn từ JWT qua `@CurrentUser UserPrincipal currentUser`.

---

### 🚨 Cạm bẫy 4: Quên Gắn `@Valid` Trước `@RequestBody`

```java
// ❌ VALIDATION BỊ VÔ HIỆU HÓA:
@PostMapping
public ApiResponse<EventResponse> createEvent(@RequestBody CreateEventRequest request) // Quên @Valid!
```
* Dù trong `CreateEventRequest` bạn có viết `@NotBlank`, `@Positive`... nhưng nếu quên `@Valid` ở Controller, Spring Boot sẽ **không kích hoạt validation** và dữ liệu rác vẫn lọt vào Service!

---

### 🚨 Cạm bẫy 5: Gắn `@Valid` Trên `@PathVariable`

```java
// ❌ THỪA THÃI / LỖI:
@GetMapping("/{id}")
public ApiResponse<EventResponse> getById(@Valid @PathVariable UUID id)
```
* `@Valid` chỉ dùng cho Request Body (Java Object/Record), không áp dụng cho kiểu nguyên thủy/UUID trên `@PathVariable`.

---

### 🚨 Cạm bẫy 6: Trả Về Trực Tiếp List Mà Quên Phân Trang (Out of Memory)

```java
// ❌ NGUY HIỂM KHI CÓ 50,000 GHẾ:
@GetMapping("/seats")
public List<EventSeatResponse> getAllSeats() { ... }
```
* Đối với dữ liệu lớn như Ghế ngồi, Đơn hàng, Lịch sử giao dịch: Bắt buộc dùng `Pageable` và bọc trong `PageResponse<T>`.

---

## 📊 4. BẢNG PHÂN BIỆT CÁC ANNOTATION NHẬN DỮ LIỆU

| Annotation | Nguồn lấy dữ liệu | Khi nào dùng? | Ví dụ |
|---|---|---|---|
| `@PathVariable` | Nằm ngay trong đường dẫn URL | Định danh tài nguyên duy nhất | `/api/v1/areas/{id}` $\rightarrow$ `@PathVariable UUID id` |
| `@RequestParam` | Query string sau dấu `?` | Lọc, tìm kiếm, phân trang | `/events?city=hanoi&status=PUBLISHED` |
| `@RequestBody` | Payload JSON trong Body request | Dữ liệu phức tạp khi POST / PUT | `@Valid @RequestBody EventAreaRequest request` |
| `@CurrentUser` | Custom annotation bóc tách từ JWT SecurityContext | Lấy danh tính người đang đăng nhập | `@CurrentUser UserPrincipal currentUser` |

---

## 🛡️ 5. CHUẨN PHÂN QUYỀN TẠI CONTROLLER (METHOD SECURITY)

```java
// 1. Chỉ Admin
@PreAuthorize("hasRole('ADMIN')")

// 2. Chỉ Ban tổ chức
@PreAuthorize("hasRole('ORGANIZER')")

// 3. Cả Admin hoặc Ban tổ chức đều được
@PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")

// 4. Công khai cho mọi người
// Không gắn @PreAuthorize + Khai báo permitAll() trong SecurityConfig
```
