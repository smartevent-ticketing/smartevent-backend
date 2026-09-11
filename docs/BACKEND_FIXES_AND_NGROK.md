# Backend: sửa lỗi, kiểm thử và dùng ngrok với VNPay

Cập nhật 11/09/2026. Hướng dẫn dành cho demo trên máy Windows: frontend cổng 3000, backend cổng 8080, VNPay Sandbox gọi backend qua ngrok.

## Các sửa lỗi đã có trong mã

- Đợt bán không giới hạn mỗi người vẫn ghi nhận số vé giữ/mua; kiểm tra tổng số lượng theo đợt bán trong một yêu cầu.
- Tạo đơn khóa phiên giữ vé và có ràng buộc một đơn trên một reservation; hạn link VNPay lấy từ hạn thanh toán của đơn.
- Callback kiểm tra chữ ký, merchant, số tiền và trạng thái; Return/IPN dùng cùng logic chống xử lý lặp.
- Hủy sự kiện giải phóng phiên giữ, hủy đơn chờ, vô hiệu vé còn dùng được và ghi nhận nghĩa vụ đối soát cho khoản đã thu.
- Chuyển vé/đổi QR/check-in phối hợp khóa vé; đổi chủ thu hồi QR và mã vé cũ.
- Phát hiện refresh-token đã dùng lại vẫn commit việc thu hồi phiên; sửa giới hạn cấu hình khu vực và việc chuyển loại vé đã có đợt bán sang khu khác.
- Organizer đọc được vé của khách trong sự kiện của mình; admin có API danh sách/chi tiết sự kiện và danh sách cần đối soát.
- V17 mở rộng cột chữ ký webhook từ 64 lên 128 ký tự, khắc phục lỗi rollback khi lưu chữ ký VNPay hợp lệ.
- Luồng tạo cấu hình, kiểm tra sức chứa và xử lý hủy sự kiện nằm trong module `event`; controller, DTO và service theo cấu trúc chung của backend. API và transaction được giữ nguyên. Xem [cấu trúc luồng cấu hình sự kiện](01-architecture/system-architecture.md#cấu-trúc-luồng-cấu-hình-sự-kiện).

Đây chưa phải trạng thái hết lỗi. Báo cáo quét toàn dự án đặt tại `D:\SmartEventRepos\REVIEW_TOAN_DU_AN_2026-09-10.md`; đặc biệt còn khoảng trống giao diện, đồng bộ các thao tác submit/reject sự kiện, outbox và quy trình hoàn tiền. Bản ghi `payment_refund_reviews.status=REQUIRED` chỉ có nghĩa cần xử lý đối soát, **không phải tiền đã được hoàn**.

## Kiểm thử đã chạy

166 unit/regression test backend đạt; 12 test tích hợp PostgreSQL đạt. Bộ PostgreSQL chạy migration thật và transaction thật, bao gồm tạo đơn đồng thời, Return/IPN trùng, chuyển vé/đổi QR đồng thời, hủy sự kiện trong lúc callback về, rollback khi phát hành hóa đơn lỗi, thu hồi refresh token và kiểm tra sức chứa. Sau refactor ngày 11/09, thêm hai ca: tạo sự kiện có cả vé ngồi/đứng và gửi duyệt thành công; lỗi khi tạo hạng vé thứ hai phải rollback toàn bộ sự kiện, khu vực, ghế, loại vé, đợt bán và tồn kho. Không gọi VNPay, SMTP hoặc dịch vụ hoàn tiền bên ngoài. Test khởi động toàn bộ ứng dụng chưa nằm trong nhóm 166 này.

Chạy lại nhóm unit/regression từ thư mục backend:

```powershell
Set-Location 'D:\SmartEventRepos\smartevent-backend'
.\gradlew.bat test --tests 'com.smartevent.modules.*' --tests 'com.smartevent.config.*' --tests 'com.smartevent.infrastructure.*' --tests 'com.smartevent.review.*'
```

Chạy bộ PostgreSQL trên database riêng, không dùng database nghiệp vụ:

```powershell
docker run --detach --rm --name smartevent-postgres-test -e POSTGRES_PASSWORD=review-only-password -e POSTGRES_DB=backend_review -p 127.0.0.1:55439:5432 postgres:16-alpine
docker exec smartevent-postgres-test pg_isready -U postgres -d backend_review
```

Đợi lệnh kiểm tra báo nhận kết nối rồi chạy:

```powershell
$env:BACKEND_TEST_JDBC_URL = 'jdbc:postgresql://127.0.0.1:55439/backend_review'
$env:BACKEND_TEST_DB_PASSWORD = 'review-only-password'
.\gradlew.bat postgresTest --rerun-tasks
docker stop smartevent-postgres-test
```

Kết quả ở `build/reports/tests/test/index.html` và `build/reports/tests/postgresTest/index.html`. Test chỉ chấp nhận URL loopback tới database tên `backend_review`. `postgresTest` tách khỏi `test` thông thường để không bắt buộc Docker cho mọi lần chạy unit test. Mật khẩu ví dụ này chỉ dành cho container kiểm thử tạm.

## Lưu ý nâng cấp dữ liệu

Flyway V15 thêm unique index cho đơn theo reservation và một QR ACTIVE trên mỗi vé. V16 thêm hàng đợi đối soát và lý do hủy sự kiện. V17 mở rộng chữ ký webhook. Không sửa migration cũ đã chạy.

V15 chủ động dừng nếu có nhiều đơn cho một reservation. Trước khi nâng cấp database đang sử dụng, sao lưu và chạy truy vấn chỉ đọc:

```sql
SELECT reservation_id, COUNT(*) AS order_count
FROM orders
WHERE reservation_id IS NOT NULL
GROUP BY reservation_id
HAVING COUNT(*) > 1;
```

Nếu có kết quả, cần đối soát từng đơn và giao dịch trước khi xử lý; không tự xóa đơn hoặc thanh toán để vượt qua migration. V15 cũng thu hồi các QR ACTIVE cũ bị trùng, giữ token mới nhất. Trong lượt kiểm tra này các migration chỉ chạy trên PostgreSQL kiểm thử riêng.

## 1. Chuẩn bị môi trường local

Mở Docker Desktop, kiểm tra cấu hình `.env` của infra đã khớp cấu hình backend, rồi chạy:

```powershell
Set-Location 'D:\SmartEventRepos\smartevent-infra'
docker compose up -d
docker compose ps
```

Backend cần cấu hình PostgreSQL, Redis, RabbitMQ, MinIO và JWT theo `.env.example`. VNPay cần TmnCode/HashSecret sandbox do nhà cung cấp cấp; các giá trị mẫu không thay thế thông tin merchant của anh. Không đưa khóa bí mật hoặc authtoken vào Git hay chia sẻ trong ảnh chụp.

## 2. Cài ngrok và lấy địa chỉ HTTPS

Trong PowerShell, cài ngrok rồi cấu hình authtoken lấy từ tài khoản ngrok của anh:

```powershell
winget install ngrok -s msstore
ngrok config add-authtoken '<AUTHTOKEN_CUA_ANH>'
ngrok http 8080
```

Nếu vừa cài mà PowerShell chưa nhận lệnh, mở terminal mới. Giữ terminal ngrok chạy và sao chép địa chỉ HTTPS nó hiển thị. Cổng 8080 là cổng backend của dự án. Cách cài và các lệnh cơ bản được đối chiếu với [hướng dẫn Windows chính thức của ngrok](https://ngrok.com/download/windows).

Trong lượt làm việc này chưa cài ngrok hoặc mở tunnel công khai; các lệnh trên để anh chạy khi muốn demo.

## 3. Gắn địa chỉ ngrok vào backend

Mở một terminal khác. Thay địa chỉ ví dụ bằng HTTPS thực tế từ ngrok, không thêm dấu `/` cuối:

```powershell
Set-Location 'D:\SmartEventRepos\smartevent-backend'
$demoPublicOrigin = 'https://TEN-MIEN-NGROK-CUA-ANH'
$env:VNPAY_RETURN_URL = "$demoPublicOrigin/api/v1/payments/vnpay/return"
$env:VNPAY_IPN_URL = "$demoPublicOrigin/api/v1/payments/vnpay/ipn"
$env:APP_FRONTEND_URL = 'http://localhost:3000'
$env:CORS_ALLOWED_ORIGINS = 'http://localhost:3000'
```

Chạy backend trong chính terminal đã đặt biến. Nếu đang dùng IDE, đặt các biến tương ứng trong Run Configuration và khởi động lại backend.

Spring Boot hiện không tự đọc `.env` của repo. Nếu file này dùng dạng `KEY=value` như `.env.example`, có thể yêu cầu đọc như file properties khi chạy:

```powershell
.\gradlew.bat bootRun --args='--spring.config.import=optional:classpath:application-jwt.yml,optional:file:.env[.properties]'
```

Không dùng `export`, dấu nháy bao giá trị hay cú pháp shell trong file được đọc theo cách này. Nếu đã cấu hình đầy đủ environment variables qua terminal/IDE, chỉ cần `.\gradlew.bat bootRun`. Spring hỗ trợ import cấu hình với gợi ý phần mở rộng; xem [Externalized Configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html).

Chỉ đặt `VNPAY_IPN_URL` trong máy **không đăng ký callback với VNPay**: provider hiện đưa Return URL vào link thanh toán, còn địa chỉ IPN phải được cung cấp/cấu hình cho merchant ở phía VNPay. Gửi địa chỉ `https://<domain>/api/v1/payments/vnpay/ipn` cho đầu mối tích hợp theo quy trình tài khoản sandbox của anh. [Tài liệu tích hợp VNPay](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html) phân biệt rõ Return đưa trình duyệt về ứng dụng và IPN thông báo kết quả giữa hai máy chủ.

## 4. Chạy frontend trên cùng máy

Đặt hai biến trong `D:\SmartEventRepos\smartevent-web\.env.local` theo `.env.example`:

```dotenv
API_BASE_URL=http://localhost:8080
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
```

Sau đó mở terminal riêng:

```powershell
Set-Location 'D:\SmartEventRepos\smartevent-web'
npm run dev
```

Truy cập `http://localhost:3000`, đăng nhập và đi qua luồng chọn vé → giữ vé → tạo đơn → thanh toán VNPay Sandbox. Cách này chỉ cần public backend để nhận callback; frontend vẫn dùng địa chỉ local trên máy của anh.

Nếu mở frontend từ điện thoại hoặc máy khác, `localhost` sẽ trỏ về thiết bị đó. Khi đó cần địa chỉ frontend/backend mà thiết bị truy cập được, cập nhật `APP_FRONTEND_URL`, `CORS_ALLOWED_ORIGINS`, hai biến API phù hợp và khởi động/build lại. Không dùng cấu hình local ở trên để kết luận demo từ xa đã hoạt động.

## 5. Kiểm tra kết nối và xử lý lỗi

Kiểm tra API backend local trước, sau đó kiểm tra cùng đường dẫn qua địa chỉ ngrok:

```powershell
Invoke-RestMethod 'http://localhost:8080/api/v1/events'
Invoke-RestMethod "$demoPublicOrigin/api/v1/events"
```

Một phép thử không có chữ ký tới IPN phải bị từ chối, không tạo thanh toán:

```powershell
Invoke-RestMethod "$demoPublicOrigin/api/v1/payments/vnpay/ipn"
```

Theo code hiện tại, kết quả mong đợi là `RspCode: 97`. Điều này chỉ chứng minh request tới được backend và chữ ký thiếu bị chặn; chưa chứng minh giao dịch thật hoặc callback từ VNPay đã hoạt động.

| Triệu chứng | Kiểm tra |
| --- | --- |
| ngrok báo không kết nối được upstream | Backend đã chạy và trả lời ở localhost:8080 chưa? |
| Return quay về được nhưng không thấy IPN | Địa chỉ IPN đã được cấu hình đúng ở phía VNPay chưa? Tunnel còn chạy không? |
| IPN trả 97 | TmnCode/HashSecret đúng cặp sandbox chưa? Query có bị thay đổi không? |
| IPN trả 04 | Số tiền callback không khớp đơn; không sửa số tiền trong callback để vượt kiểm tra. |
| IPN trả 02 | Thông báo đã được xử lý trước đó; kiểm tra trạng thái đơn/vé trong backend. |
| Thanh toán đến sau hạn hoặc sau hủy sự kiện | Đối chiếu payment và hồ sơ REQUIRED; không tự kết luận vé đã được cấp hoặc tiền đã hoàn. |
| Ngrok đổi tên miền | Cập nhật hai URL callback, cấu hình phía VNPay và khởi động lại backend; tạo link thanh toán mới. |
| Browser báo CORS | CORS phải chứa origin frontend thực tế. IPN server-to-server không được sửa bằng cách mở CORS rộng. |

Giữ các terminal hoạt động trong suốt demo. Dừng tunnel bằng Ctrl+C khi hoàn tất.
