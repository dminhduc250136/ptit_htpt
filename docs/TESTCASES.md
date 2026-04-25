# Bộ Testcase từng bước cho 4 Phase

> Tài liệu này hướng dẫn bạn — **kể cả khi chưa nắm nghiệp vụ** — vẫn có thể tự kiểm tra (test) các tính năng đã làm trong từng phase. Mỗi testcase nói rõ:
> - **Cần công cụ gì** (browser, terminal, Postman, Docker...)
> - **Bước 1, 2, 3 cụ thể**
> - **Kết quả mong đợi** ("nếu thấy X nghĩa là PASS")
> - **Nếu sai thì biểu hiện ra sao**

---

## 0. Chuẩn bị môi trường (làm 1 lần)

### 0.1. Cài công cụ cần dùng

| Công cụ | Dùng để | Link |
|---|---|---|
| **Docker Desktop** | Khởi động cùng lúc 7 service backend | https://docs.docker.com/desktop/ |
| **Node.js 20+** | Chạy frontend Next.js | https://nodejs.org |
| **Trình duyệt Chrome / Firefox** | Bấm vào trang web + xem DevTools | có sẵn |
| **PowerShell** (Windows có sẵn) | Gửi request curl-like | có sẵn |
| **Postman** *(tùy chọn)* | GUI thay PowerShell, dễ nhìn hơn | https://www.postman.com/downloads |
| **VSCode** *(tùy chọn)* | Mở source code khi cần đối chiếu | https://code.visualstudio.com |

### 0.2. Khởi động hệ thống

Mở PowerShell tại thư mục dự án `D:\SYP_PROJECT\gsd-learning\tmdt-use-gsd`:

```powershell
# Bước 1: Khởi động toàn bộ backend
docker compose up -d

# Bước 2: Đợi ~30 giây cho service warm-up. Kiểm tra:
docker compose ps
# Mong đợi: 7 dòng status = "running" hoặc "healthy"

# Bước 3: Mở terminal MỚI, khởi động frontend
cd sources\frontend
npm install         # chỉ chạy lần đầu
npm run dev         # mở http://localhost:3000
```

**Sau khi chạy xong các port sau phải truy cập được:**

| URL | Là gì |
|---|---|
| http://localhost:3000 | Frontend Next.js |
| http://localhost:8080 | API Gateway (cửa ngõ) |
| http://localhost:8080/swagger-ui.html | Swagger gom 6 service |
| http://localhost:8081 | user-service (trực tiếp, không qua gateway) |
| http://localhost:8082 | product-service |
| http://localhost:8083 | order-service |
| http://localhost:8084 | payment-service |
| http://localhost:8085 | inventory-service |
| http://localhost:8086 | notification-service |

### 0.3. Mẹo dùng PowerShell để gửi request

```powershell
# GET đơn giản
Invoke-RestMethod -Uri "http://localhost:8080/api/products/products"

# POST với JSON body
$body = @{ email = "test@test.com"; password = "12345678" } | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:8080/api/users/auth/login" `
  -Method Post -Body $body -ContentType "application/json"

# Gửi kèm header X-Request-Id để theo dõi traceId
Invoke-RestMethod -Uri "http://localhost:8081/__contract/ping" `
  -Headers @{ "X-Request-Id" = "my-test-001" }
```

Nếu request bị lỗi 4xx/5xx, PowerShell ném exception. Để **đọc được body lỗi** dùng pattern:

```powershell
try {
  Invoke-RestMethod -Uri "http://localhost:8080/api/users/auth/login" `
    -Method Post -Body '{"email":"x"}' -ContentType "application/json"
} catch {
  $_.ErrorDetails.Message    # ← body lỗi nằm ở đây
}
```

---

## Phase 1 — Hợp đồng API + Swagger

### TC1.1 — Kiểm tra Swagger UI hiển thị

**Mục đích:** Xác nhận mọi service đều public Swagger.

| Bước | Thao tác | Mong đợi |
|---|---|---|
| 1 | Mở browser → vào `http://localhost:8080/swagger-ui.html` | Trang Swagger có dropdown "Select a definition" liệt kê 6 service (users, products, orders, payments, inventory, notifications) |
| 2 | Chọn từng service trong dropdown | Trang load được danh sách endpoints, không lỗi đỏ |
| 3 | Vào trực tiếp `http://localhost:8081/swagger-ui.html` | Hiện Swagger riêng của user-service |
| 4 | Vào `http://localhost:8081/v3/api-docs` | Trả về JSON đặc tả OpenAPI (rất dài, bắt đầu bằng `{"openapi":"3.0..."}`) |

**FAIL nếu:** trang trắng, lỗi 404, hoặc dropdown không có service nào.
RESULT: OK
---

### TC1.2 — Kiểm tra envelope thành công

**Mục đích:** Xác nhận response success bọc đúng `{timestamp, status, message, data}`.

**Công cụ:** PowerShell hoặc Postman.

```powershell
Invoke-RestMethod -Uri "http://localhost:8081/__contract/ping"
```

**Mong đợi response (giả lập):**
```json
{
  "timestamp": "2026-04-25T10:00:00Z",
  "status": 200,
  "message": "OK",
  "data": { "service": "user-service" }
}
```

**FAIL nếu:** response chỉ là `{"service": "user-service"}` (thiếu wrapper) → ResponseBodyAdvice không hoạt động.
RESULT:
PS C:\WINDOWS\system32> Invoke-RestMethod -Uri "http://localhost:8081/__contract/ping"

timestamp                      status message data
---------                      ------ ------- ----
2026-04-25T04:07:36.128723587Z    200 OK      @{service=user-service}

---

### TC1.3 — Kiểm tra envelope lỗi validation

**Mục đích:** Xác nhận lỗi 400 trả đúng schema có `code`, `fieldErrors[]`.

```powershell
try {
  Invoke-RestMethod -Uri "http://localhost:8081/__contract/validate" `
    -Method Post -Body '{}' -ContentType "application/json"
} catch {
  $_.ErrorDetails.Message
}
```

**Mong đợi:**
```json
{
  "timestamp": "...",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_ERROR",
  "message": "Validation failed",
  "path": "/__contract/validate",
  "traceId": "...",
  "fieldErrors": [
    { "field": "name", "rejectedValue": null, "message": "must not be blank" }
  ]
}
```

**Check list:** ✓ status=400 ✓ code="VALIDATION_ERROR" ✓ `fieldErrors` không rỗng ✓ mỗi field error có 3 key `field/rejectedValue/message`.
RESULT: 
PS C:\WINDOWS\system32> try {
>>   $res = Invoke-WebRequest -Uri "http://localhost:8081/__contract/validate" `
>>     -Method Post -Body '{}' -ContentType "application/json" `
>>     -Headers @{ "X-Request-Id" = "TC1-4-trace-xyz" }
>>
>>   $res.StatusCode
>>   $res.Content
>> } catch {
>>   $_ | Format-List * -Force
>> }


PSMessageDetails      :
Exception             : System.Net.WebException: The remote server returned an error: (400) Bad Request.
                           at Microsoft.PowerShell.Commands.WebRequestPSCmdlet.GetResponse(WebRequest request)
                           at Microsoft.PowerShell.Commands.WebRequestPSCmdlet.ProcessRecord()
TargetObject          : System.Net.HttpWebRequest
CategoryInfo          : InvalidOperation: (System.Net.HttpWebRequest:HttpWebRequest) [Invoke-WebRequest], WebException
FullyQualifiedErrorId : WebCmdletWebResponseException,Microsoft.PowerShell.Commands.InvokeWebRequestCommand
ErrorDetails          :
InvocationInfo        : System.Management.Automation.InvocationInfo
ScriptStackTrace      : at <ScriptBlock>, <No file>: line 2
PipelineIterationInfo : {}



PS C:\WINDOWS\system32>
=> TÔI PHẢI NHỜ CHATGPT XEM LẠI LỆNH, THÌ CÓ RA RESULT NHƯ TRÊN.
---

### TC1.4 — Kiểm tra `X-Request-Id` được giữ nguyên

```powershell
try {
  Invoke-RestMethod -Uri "http://localhost:8081/__contract/validate" `
    -Method Post -Body '{}' -ContentType "application/json" `
    -Headers @{ "X-Request-Id" = "TC1-4-trace-xyz" }
} catch {
  $_.ErrorDetails.Message
}
```

**Mong đợi:** Response body có `"traceId": "TC1-4-trace-xyz"` (đúng giá trị bạn gửi).

RESULT:
PS C:\WINDOWS\system32> try {
>>   Invoke-WebRequest -Uri "http://localhost:8081/__contract/validate" 
>>     -Method Post -Body '{}' -ContentType "application/json" 
>>     -Headers @{ "X-Request-Id" = "TC1-4-trace-xyz" }
>>
>> } catch {
>>   $response = $_.Exception.Response
>>   if ($response -ne $null) {
>>     $reader = New-Object System.IO.StreamReader($response.GetResponseStream())
>>     $reader.ReadToEnd()
>>   } else {
>>     $_ | Format-List * -Force
>>   }
>> }
{"timestamp":"2026-04-25T04:15:08.937250036Z","status":400,"error":"Bad Request","message":"Validation failed","code":"VALIDATION_ERROR","path":"/__contract/validate","traceId":"TC1-4-trace-xyz","fieldErrors":[{"field":"name","rejectedValue":null,"message":"must not be blank"}]}

✅ Kết luận cuối
❌ Lý do ban đầu “không thấy gì” → do 400 + catch không in
❌ Lý do fail hiện tại → thiếu name
✅ Nhưng:

🔥 TraceId propagation đã đúng → PASS TC1.4


---

### TC1.5 — Kiểm tra route không tồn tại qua gateway

```powershell
try {
  Invoke-RestMethod -Uri "http://localhost:8080/api/does-not-exist/foo"
} catch {
  $_.ErrorDetails.Message
}
```

**Mong đợi:** Status 404, body có `"code": "NOT_FOUND"`, content-type là `application/json` (KHÔNG phải HTML lỗi mặc định).

RESULT:
PS C:\WINDOWS\system32> try {
>>   Invoke-RestMethod -Uri "http://localhost:8080/api/does-not-exist/foo"
>> } catch {
>>   $_.ErrorDetails.Message
>> }
{"timestamp":"2026-04-25T04:17:01.342824408Z","status":404,"error":"Not Found","message":"No static resource api/does-not-exist/foo.","code":"NOT_FOUND","path":"/api/does-not-exist/foo","traceId":null,"fieldErrors":[]}

---

## Phase 2 — CRUD Completeness

### TC2.1 — Liệt kê sản phẩm, kiểm tra phân trang

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/products/products?page=0&size=5"
```

**Mong đợi `data` chứa:**
```json
{
  "content": [...],
  "totalElements": ...,
  "totalPages": ...,
  "currentPage": 0,
  "pageSize": 5,
  "isFirst": true,
  "isLast": ...
}

RESULT:
{
  "timestamp": "2026-04-25T04:20:33.030633527Z",
  "status": 500,
  "error": "Internal Server Error",
  "message": "Internal server error",
  "code": "INTERNAL_ERROR",
  "path": "/products",
  "traceId": "ae66948c-7af4-42fc-8efe-48440142868b",
  "fieldErrors": []
}

❌ Lỗi 500 không phải do request mà do backend bị crash
🔥 Nguyên nhân gốc: chưa có database / bảng products
👉 JPA query vào bảng không tồn tại → ném exception
✅ Tạo DB hoặc bật ddl-auto=update là chạy lại bình thường

```

**Cách kiểm tra trong Swagger UI** (cách dễ hơn):
1. Mở `http://localhost:8080/swagger-ui.html` → chọn `products`.
2. Tìm endpoint `GET /products`.
3. Bấm **"Try it out"** → điền `page=0`, `size=5` → **"Execute"**.
4. Section "Response body" hiện đúng cấu trúc trên = PASS.
=> CHƯA CÓ API CHO GET /products

---

### TC2.2 — Tạo, sửa, xoá soft-delete một sản phẩm

> Lưu ý: nhiều endpoint admin yêu cầu auth. Bạn có thể thử route public hoặc xem trong Swagger endpoint nào public.

**Cách an toàn nhất là dùng Swagger UI:**

| Bước | Thao tác | Endpoint |
|---|---|---|
| 1 | Tạo product | `POST /admin/products` (body theo schema Swagger gợi ý) |
| 2 | Lấy `id` từ response, gọi GET | `GET /products/{id}` |
| 3 | Sửa | `PUT /admin/products/{id}` |
| 4 | Xoá soft | `DELETE /admin/products/{id}` |
| 5 | GET lại bằng public | `GET /products/{id}` → mong đợi 404 |
| 6 | GET admin có cờ includeDeleted | `GET /admin/products?includeDeleted=true` → vẫn thấy |

**Mong đợi:** Bước 5 trả 404 (public không thấy đã soft-delete) nhưng bước 6 vẫn liệt kê.

---

### TC2.3 — Smoke gateway prefix bằng script có sẵn

Dự án đã có sẵn script PowerShell:
- `.planning/phases/02-crud-completeness-across-services/scripts/02-01-gateway-smoke.ps1`
- `.planning/phases/02-crud-completeness-across-services/scripts/02-02-gateway-smoke.ps1`
- `.planning/phases/02-crud-completeness-across-services/scripts/02-03-gateway-smoke.ps1`

**Chạy:**
```powershell
powershell -ExecutionPolicy Bypass -File .planning\phases\02-crud-completeness-across-services\scripts\02-02-gateway-smoke.ps1
```

**Mong đợi:** Script in ra "OK" cho từng route, không có "FAIL"/"ERROR".

---

## Phase 3 — Validation + Error Handling

### TC3.1 — Lỗi validation field — mock luồng đăng ký

Giả lập gửi body thiếu/sai cho endpoint đăng ký:

```powershell
$body = '{"email":"khongphaiemail","password":"abc"}'
try {
  Invoke-RestMethod -Uri "http://localhost:8080/api/users/auth/register" `
    -Method Post -Body $body -ContentType "application/json"
} catch {
  $_.ErrorDetails.Message
}
```

**Mong đợi:**
- Status 400.
- `code: "VALIDATION_ERROR"`.
- `fieldErrors` chứa ít nhất 1 entry với `field: "email"` (và/hoặc `password`).

> Nếu backend chưa expose `/auth/register` (theo Phase 4 deviation), endpoint sẽ 404 — chuyển sang dùng `/__contract/validate` để test schema.

---

### TC3.2 — Mask trường nhạy cảm

**Cách 1 — Đọc test**: Mở file `sources/backend/user-service/src/test/java/com/ptit/htpt/userservice/api/GlobalExceptionHandlerTest.java` và chạy:

```powershell
docker run --rm -v "${PWD}/sources/backend/user-service:/work" -w /work `
  maven:3.9-eclipse-temurin-17 mvn -q test -Dtest=GlobalExceptionHandlerTest
```

**Mong đợi:** `Tests run: 8, Failures: 0, Errors: 0`.

**Cách 2 — Test trực tiếp** (cần tìm endpoint nhận field tên `password`):

```powershell
$body = '{"email":"x@x.com","password":"123"}'    # password quá ngắn
try {
  Invoke-RestMethod -Uri "http://localhost:8080/api/users/auth/register" `
    -Method Post -Body $body -ContentType "application/json"
} catch {
  $_.ErrorDetails.Message
}
```

**Mong đợi:** Trong `fieldErrors`, entry của `password` có `rejectedValue: "***"` (không lộ giá trị `"123"`).

---

### TC3.3 — Truncate giá trị dài

```powershell
$longName = "a" * 200    # chuỗi 200 ký tự
$body = "{`"name`":`"$longName`"}"
try {
  Invoke-RestMethod -Uri "http://localhost:8081/__contract/validate" `
    -Method Post -Body $body -ContentType "application/json"
} catch {
  $_.ErrorDetails.Message
}
```

**Mong đợi:** `fieldErrors[].rejectedValue` dài đúng 123 ký tự (120 chữ `a` + `...`).

---

### TC3.4 — Pass-through lỗi qua gateway

Gửi cùng request validation tới **service trực tiếp** rồi tới **qua gateway**, so sánh body:

```powershell
# Trực tiếp
try { Invoke-RestMethod -Uri "http://localhost:8081/__contract/validate" -Method Post -Body '{}' -ContentType "application/json" } catch { $direct = $_.ErrorDetails.Message }

# Qua gateway
try { Invoke-RestMethod -Uri "http://localhost:8080/api/users/__contract/validate" -Method Post -Body '{}' -ContentType "application/json" } catch { $thru = $_.ErrorDetails.Message }

$direct
$thru
```

**Mong đợi:** Hai response có cùng `code`, cùng `fieldErrors[]`, cùng `traceId` — gateway KHÔNG đè/làm méo lỗi của service.

---

### TC3.5 — Auth error tách biệt 401 vs 403

Truy cập route admin không kèm token:

```powershell
try {
  Invoke-RestMethod -Uri "http://localhost:8080/api/users/admin/users"
} catch {
  $_.Exception.Response.StatusCode      # mong đợi: Unauthorized
  $_.ErrorDetails.Message               # body có code: UNAUTHORIZED
}
```

**Mong đợi:**
- Không token → `401 / UNAUTHORIZED` (không phải 403, không phải 500).
- Có token user thường → `403 / FORBIDDEN`.

---

## Phase 4 — Frontend + E2E (UI Testing)

### Công cụ chính: **Browser DevTools** (F12)

| Tab DevTools | Dùng để |
|---|---|
| **Console** | Xem lỗi JS, log |
| **Network** | Xem từng request HTTP, body request/response, status code, header |
| **Application → Storage → Local Storage** | Kiểm tra `accessToken`, `refreshToken`, `userProfile`, `cart` |
| **Application → Cookies** | Kiểm tra cookie `auth_present=1` |

### TC4.1 — Trang chủ load sản phẩm thật

| Bước | Thao tác | Mong đợi |
|---|---|---|
| 1 | Mở `http://localhost:3000/` | Trang chủ hiện, có 2 section sản phẩm (Featured + Latest) |
| 2 | Mở DevTools → Network → filter `Fetch/XHR` | Có request đi đến `/api/products/products?...` (status 200) |
| 3 | Click vào 1 card sản phẩm | Chuyển trang `/products/<slug>`, hiện chi tiết sản phẩm thật |

**FAIL biểu hiện:** Hiện loading mãi không xong → kiểm tra docker compose còn chạy không, hoặc backend trả 5xx.

---

### TC4.2 — Bảo vệ route bằng middleware

| Bước | Thao tác | Mong đợi |
|---|---|---|
| 1 | Đảm bảo CHƯA login (xoá Local Storage + Cookies trên `localhost:3000`) | DevTools → Application → "Clear site data" |
| 2 | Truy cập trực tiếp `http://localhost:3000/checkout` | Bị **redirect** sang `/login?returnTo=%2Fcheckout` |
| 3 | URL bar phải hiện `?returnTo=%2Fcheckout` | PASS |
| 4 | Lặp lại với `/profile`, `/admin/users` | Đều redirect, returnTo encode đúng path |
| 5 | Truy cập `/products`, `/` | KHÔNG bị chặn (200 OK) |

---

### TC4.3 — Mock login + giữ session

> Backend `/auth/*` chưa có thật — login dùng mock submit nhưng vẫn set token + cookie.

| Bước | Thao tác | Mong đợi |
|---|---|---|
| 1 | Vào `/login` | Form login hiển thị |
| 2 | Điền email + password bất kỳ (ví dụ `a@a.com` / `12345678`), submit | Sau ~1s, redirect về `/` |
| 3 | DevTools → Application → Local Storage | Có 3 key: `accessToken`, `refreshToken`, `userProfile` |
| 4 | DevTools → Application → Cookies | Có cookie `auth_present=1` |
| 5 | Bấm vào `/checkout` | KHÔNG bị redirect → vào được trang checkout (PASS) |

---

### TC4.4 — Open-redirect bị chặn (T-04-03)

| Bước | Thao tác | Mong đợi |
|---|---|---|
| 1 | Trong DevTools → "Clear site data" | Reset trạng thái |
| 2 | Truy cập `http://localhost:3000/login?returnTo=https://evil.example.com/` | Trang login load bình thường |
| 3 | Submit form login (mock) | Sau khi login thành công, redirect đến `/` (KHÔNG đến evil.example.com) |

**Logic:** Code chỉ chấp nhận `returnTo` bắt đầu bằng `/` và không bắt đầu `//`.

---

### TC4.5 — Thêm sản phẩm vào giỏ và đi qua giỏ

| Bước | Thao tác | Mong đợi |
|---|---|---|
| 1 | Đăng nhập (TC4.3) | OK |
| 2 | Vào 1 trang sản phẩm, bấm "Thêm vào giỏ hàng" | Toast "Đã thêm vào giỏ hàng" hiện ra ở góc phải |
| 3 | DevTools → Application → Local Storage → key `cart` | Có 1 entry với productId, name, price, quantity |
| 4 | Vào `/cart` | Hiện đúng item vừa thêm |
| 5 | Đổi quantity, nhấn xoá → state đồng bộ | Local Storage `cart` thay đổi tương ứng |

---

### TC4.6 — Validation error trên checkout (B1)

| Bước | Thao tác | Mong đợi |
|---|---|---|
| 1 | Có ≥1 item trong giỏ, vào `/checkout` | Form hiện |
| 2 | **Để trống ít nhất 1 trường bắt buộc** (ví dụ "Họ tên") rồi submit | KHÔNG redirect; trang vẫn ở `/checkout` |
| 3 | Banner đỏ ở đầu trang xuất hiện với chữ *"Vui lòng kiểm tra các trường bị lỗi"* | PASS |
| 4 | Field bỏ trống có dòng đỏ inline ngay dưới input | PASS |
| 5 | DevTools → Network → tab response của POST order | status 400, body có `code: "VALIDATION_ERROR"` |

---

### TC4.7 — Stock conflict modal (B2)

> Cần "ép" backend trả 409 stock-shortage. Cách dễ nhất: vào Swagger admin của inventory-service giảm `availableQuantity` của 1 product < số lượng trong giỏ.

| Bước | Thao tác | Mong đợi |
|---|---|---|
| 1 | Trong giỏ có 5 cái laptop X | OK |
| 2 | Mở `http://localhost:8085/swagger-ui.html` → tìm endpoint update inventory item → đặt `availableQuantity` của laptop X = 3 | OK |
| 3 | Quay về `/checkout`, submit | Modal hiện với title **"Một số sản phẩm không đủ hàng"** |
| 4 | Modal liệt kê item lỗi, có 2 nút: **"Cập nhật số lượng"** và **"Xóa khỏi giỏ"** | PASS |
| 5 | Bấm "Cập nhật số lượng" | Quantity trong giỏ chuyển từ 5 → 3, modal đóng |
| 6 | Submit lại | Đặt hàng thành công, hiện modal "Đặt hàng thành công!" với mã đơn |

---

### TC4.8 — Payment failure modal (B3)

> Trigger lỗi payment phụ thuộc vào cách payment-service mock fail. Đọc source `sources/backend/payment-service/.../service/...` để biết.

| Bước | Thao tác | Mong đợi |
|---|---|---|
| 1 | Tìm cơ chế force fail (vd: amount = 0, hoặc env flag), submit checkout | Backend trả 409 từ payment path |
| 2 | Modal **"Thanh toán thất bại"** hiện | PASS |
| 3 | 2 nút: **"Thử lại"** và **"Đổi phương thức thanh toán"** | PASS |
| 4 | Bấm "Thử lại" → thực hiện lại submit | Network hiện request POST order mới |
| 5 | Bấm "Đổi phương thức thanh toán" | Modal đóng, user chọn lại radio thanh toán → submit lại OK |

---

### TC4.9 — 401 silent redirect (B4a)

| Bước | Thao tác | Mong đợi |
|---|---|---|
| 1 | Đã login, đang ở `/profile` | OK |
| 2 | DevTools → Application → Local Storage → xoá key `accessToken` | Cookie `auth_present` GIỮ NGUYÊN |
| 3 | Reload trang `/profile` (F5) | Trang gọi `listMyOrders` → backend trả 401 |
| 4 | Frontend tự xoá cả localStorage + cookie, redirect `/login?returnTo=%2Fprofile` | PASS |
| 5 | Login lại → redirect về `/profile` đúng | PASS |

---

### TC4.10 — RetrySection khi backend chết (B5)

| Bước | Thao tác | Mong đợi |
|---|---|---|
| 1 | Tắt 1 service backend: `docker compose stop product-service` | OK |
| 2 | Vào `/products` | Trang load, sau vài giây thấy **RetrySection** với chữ *"Không tải được dữ liệu"* + nút *"Thử lại"* |
| 3 | Khởi động lại: `docker compose start product-service` | OK |
| 4 | Bấm "Thử lại" | Trang load sản phẩm thật (PASS) |

---

### TC4.11 — Accessibility nhanh (a11y)

| Test | Cách kiểm tra | Mong đợi |
|---|---|---|
| Banner đọc được bằng screen reader | DevTools → Elements → tìm Banner → kiểm tra attr | có `role="alert"` và `aria-live="assertive"` |
| Modal có thể đóng bằng phím Esc | Mở modal bất kỳ → bấm Esc | Modal đóng |
| Click backdrop (vùng xám) đóng modal | Click ra ngoài modal | Modal đóng |
| Toast close button có aria-label | DevTools → Elements → nút X của toast | có `aria-label="Đóng thông báo"` |

---

## Phụ lục — Chạy unit test backend

Nếu máy không cài Maven, dùng Docker container:

```powershell
# user-service
docker run --rm -v "${PWD}/sources/backend/user-service:/work" -w /work `
  maven:3.9-eclipse-temurin-17 mvn -q test -Dtest=GlobalExceptionHandlerTest

# order-service
docker run --rm -v "${PWD}/sources/backend/order-service:/work" -w /work `
  maven:3.9-eclipse-temurin-17 mvn -q test -Dtest=GlobalExceptionHandlerTest

# api-gateway
docker run --rm -v "${PWD}/sources/backend/api-gateway:/work" -w /work `
  maven:3.9-eclipse-temurin-17 mvn -q test -Dtest=GlobalGatewayErrorHandlerTest
```

**Mong đợi:**
- user-service: 8 tests, 0 fail
- order-service: 6 tests, 0 fail
- api-gateway: 6 tests, 0 fail

---

## Quick Smoke Checklist (5 phút)

Khi muốn kiểm tra nhanh "hệ thống còn sống không", chạy theo thứ tự:

- [ ] `docker compose ps` → 7 service running
- [ ] `http://localhost:8080/swagger-ui.html` → load OK
- [ ] `Invoke-RestMethod http://localhost:8081/__contract/ping` → có wrapper `{timestamp,status,message,data}`
- [ ] `Invoke-RestMethod http://localhost:8080/api/products/products` → trả pagination envelope
- [ ] `http://localhost:3000` → trang chủ load có sản phẩm thật
- [ ] Cleanup browser, vào `/checkout` → bị redirect login với `returnTo`
- [ ] Login mock + thêm 1 sản phẩm vào giỏ + checkout với form trống → thấy Banner đỏ
- [ ] `docker compose stop product-service` rồi reload `/products` → thấy RetrySection

Đủ 8 dấu tích = hệ thống ổn cho UAT chính thức.

---

## Mẫu báo cáo testcase

Dùng mẫu này khi điền `04-UAT.md` hoặc gửi report cho mentor:

```markdown
| Test ID | Mô tả ngắn | Bước rút gọn | Pass/Fail | Ghi chú |
|---------|------------|--------------|-----------|---------|
| TC1.1   | Swagger UI hiển thị | Mở 8080/swagger-ui.html | PASS | 6 service đều liệt kê |
| TC1.3   | Validation envelope | POST /__contract/validate {} | PASS | code=VALIDATION_ERROR |
| TC4.6   | Banner trên checkout | Submit form trống | PASS | Banner đỏ + inline error |
| ...     | ... | ... | ... | ... |
```


** Các phase 2 3 4 cũng do chưa có db nên kết quả test trả về là:
{"timestamp":"2026-04-25T04:22:48.392714561Z","status":500,"error":"Internal Server Error","message":"Internal server error","code":"INTERNAL_ERROR","path":"/auth/register","traceId":"48c2ee82-81c5-40bb-8ce3-cdd09b45e87e","fieldErrors":[]}