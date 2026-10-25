# REST API

App bán hàng **nội bộ** của quán: chủ quán (`OWNER`), quản lý (`MANAGER`), nhân viên bán hàng (`STAFF`). Không có API cho khách hàng.

- Backend: **Spring Boot 3 (Java 21)**, địa chỉ gốc `http://localhost:8090`.
- Tài liệu tương tác (Swagger UI): `/swagger-ui.html` · đặc tả OpenAPI 3: `/v3/api-docs` (nhập được vào Postman).
- **47 API, 31 đường dẫn, 10 nhóm** (đúng như các nhóm 01–10 trên trang Swagger).
- Dữ liệu vào/ra: JSON (UTF-8). Ngày giờ dạng ISO `2026-10-25T09:30:00`. Tiền: số nguyên, đơn vị đồng.
- Xác thực: header `Authorization: Bearer <token>` (token lấy từ `/api/auth/login` hoặc `/api/auth/firebase`, hạn 72 giờ).
- Ký hiệu quyền: 🌐 mọi người · 🔑 đã đăng nhập (mọi vai trò) · 👔 `OWNER` hoặc `MANAGER` · 👑 chỉ `OWNER`.

Tài khoản mẫu: `chu / chu123` (chủ quán) · `quanly / quanly123` (quản lý) · `nhanvien / nhanvien123` (nhân viên).

## Quy ước REST

| Phương thức | Ý nghĩa | Mã thành công |
|---|---|---|
| `GET` | đọc | 200 |
| `POST` | tạo mới / thực hiện hành động | 201 (tạo mới) hoặc 200 |
| `PUT` | thay toàn bộ | 200 |
| `PATCH` | sửa một phần (vd chỉ trạng thái) | 200 |
| `DELETE` | xóa | 204 (không có nội dung) hoặc 200 kèm dữ liệu mới |

Lỗi luôn cùng 1 dạng:

```json
{ "status": 409, "error": "Conflict", "message": "Bàn 02 đang có bill, hãy thêm món vào bill đó",
  "details": [], "timestamp": "2026-10-25T09:30:00" }
```

| Mã | Khi nào |
|---|---|
| 400 | dữ liệu sai (`details` liệt kê từng trường, vd `"name: không được để trống"`), tiền khách đưa chưa đủ |
| 401 | chưa đăng nhập / token sai / hết hạn / sai mật khẩu |
| 403 | không đủ quyền (vd nhân viên xem doanh thu, quản lý tạo tài khoản quản lý, tài khoản bị khóa) |
| 404 | không tìm thấy |
| 409 | xung đột: trùng tên, bàn đang có bill, sai luồng trạng thái, hủy bill đã thu tiền, xóa dữ liệu đang được dùng |
| 503 | tính năng cần Firebase nhưng máy chủ chưa cấu hình |

## 01. Xác thực (2 API)

| | API | Mô tả |
|---|---|---|
| 🌐 | `POST /api/auth/login` | `{username, password}` → `{token, expiresAt, user}` |
| 🌐 | `POST /api/auth/firebase` | `{idToken}` (Firebase ID token sau khi đăng nhập Google) → `{token, expiresAt, user}`. Chỉ vào được nếu email Google trùng email của 1 tài khoản đã được cấp, ngược lại 403 |

Không có API đăng ký: tài khoản do chủ quán / quản lý tạo (`POST /api/users`).

```bash
curl -X POST http://localhost:8090/api/auth/login -H "Content-Type: application/json" \
     -d "{\"username\":\"nhanvien\",\"password\":\"nhanvien123\"}"
```

## 02. Người dùng (6 API)

| | API | Mô tả |
|---|---|---|
| 🔑 | `GET /api/users/me` | Hồ sơ của tôi |
| 🔑 | `PUT /api/users/me` | `{fullName, email?, phone?}` |
| 🔑 | `PUT /api/users/me/password` | `{currentPassword, newPassword}` → 204 |
| 👔 | `GET /api/users` | Danh sách tài khoản của quán |
| 👔 | `POST /api/users` | Tạo tài khoản `{username, password, fullName, role, phone?, email?}` → 201. Quản lý chỉ tạo được `STAFF` |
| 👔 | `PUT /api/users/{id}` | `{fullName, phone?, role, active, newPassword?}` – đổi vai trò, khóa / mở khóa, cấp lại mật khẩu. Quản lý chỉ sửa được tài khoản `STAFF`; không ai tự khóa / tự hạ quyền mình |

## 03–05. Danh mục, Món, Bàn (CRUD tổng quát – cùng 5 API cho cả 3 tài nguyên)

| | API | Mô tả |
|---|---|---|
| 🔑 | `GET /api/categories` · `GET /api/categories/{id}` | Danh mục |
| 👔 | `POST` / `PUT /{id}` / `DELETE /{id}` `/api/categories` | `{name, description?, icon?, sortOrder}`; xóa danh mục còn món → 409 |
| 🔑 | `GET /api/products?categoryId=&q=&available=true` | Món, lọc theo danh mục / từ khóa / đang bán. Mỗi món có `bestSeller` = top 3 bán chạy 30 ngày (nhãn "Bán chạy") |
| 👔 | `POST` / `PUT /{id}` / `DELETE /{id}` `/api/products` | `{categoryId, name, description?, price, imageUrl?, available}` |
| 🔑 | `GET /api/tables` · `GET /api/tables/{id}` | Bàn (sơ đồ bàn) |
| 👔 | `POST` / `PUT /{id}` / `DELETE /{id}` `/api/tables` | `{name, area?, seats, active}` |

## 06. Đơn hàng – bán hàng tại quán (8 API)

| | API | Mô tả |
|---|---|---|
| 🔑 | `POST /api/orders` | Mở bill cho bàn (`DINE_IN` + `tableId`) hoặc mang đi (`TAKEAWAY`) → 201, trạng thái `NEW`. Bàn đang có bill → 409 |
| 🔑 | `GET /api/orders/active` | Các bill đang mở (sơ đồ bàn, màn pha chế) |
| 🔑 | `GET /api/orders?date=2026-10-25` | Mọi đơn trong ngày (lịch sử) |
| 🔑 | `GET /api/orders/{id}` | Chi tiết 1 bill |
| 🔑 | `POST /api/orders/{id}/items` | Gọi thêm món `{items:[…]}`; bill đã pha xong quay về `NEW` để quầy thấy món mới |
| 🔑 | `DELETE /api/orders/{id}/items/{itemId}` | Bớt món gọi nhầm (chỉ khi bill còn `NEW`, chưa thu tiền) |
| 🔑 | `PATCH /api/orders/{id}/status` | `{status, cancelReason?}`: `NEW → PREPARING → READY → SERVED`, hoặc `CANCELLED`. Nhân viên chỉ hủy bill chưa pha; quản lý / chủ quán hủy mọi bill chưa thu tiền |
| 🔑 | `POST /api/orders/{id}/pay` | Thu tiền `{paymentMethod: CASH\|BANK_TRANSFER\|E_WALLET, amountReceived?}` → `changeAmount` (tiền thối) |

Vòng đời: bill **hoàn tất** (`COMPLETED`, bàn trống lại) khi đã phục vụ (`SERVED`) **và** đã thu tiền – máy chủ tự chuyển, không ai chuyển tay.

```json
POST /api/orders
{ "orderType": "DINE_IN", "tableId": 2,
  "items": [ { "productId": 1, "quantity": 2, "note": "ít đá" }, { "productId": 4, "quantity": 1 } ] }
→ 201
{ "id": 7, "code": "DH20261025-0007", "createdByName": "Nhân viên bán hàng", "tableName": "Bàn 02",
  "status": "NEW", "statusLabel": "Chờ pha", "totalAmount": 103000, "itemCount": 3, "paid": false,
  "items": [ { "productName": "Cà phê sữa đá", "unitPrice": 29000, "quantity": 2, "lineTotal": 58000, "note": "ít đá" }, … ] }

POST /api/orders/7/pay   { "paymentMethod": "CASH", "amountReceived": 200000 }
→ 200  { …, "paid": true, "paymentMethod": "CASH", "amountReceived": 200000, "changeAmount": 97000 }
```

Mở bill / gọi thêm → thông báo cho cả quán (quầy pha chế); bill pha xong → báo người mở bill mang ra; hủy bill → báo chủ quán / quản lý.

## 07. Thông báo & FCM (6 API)

| | API | Mô tả |
|---|---|---|
| 🔑 | `GET /api/notifications?limit=30` | Hộp thư |
| 🔑 | `GET /api/notifications/unread-count` | `{unread}` |
| 🔑 | `POST /api/notifications/{id}/read` | Đánh dấu đã đọc |
| 🔑 | `POST /api/notifications/read-all` | Đánh dấu tất cả |
| 🔑 | `POST /api/devices` | Lưu mã FCM: `{token, platform: WEB\|ANDROID\|IOS}` → 204 |
| 🔑 | `DELETE /api/devices?token=…` | Gỡ mã khi đăng xuất → 204 |

## 08. Chat AI (4 API)

| | API | Mô tả |
|---|---|---|
| 🔑 | `GET /api/chat/status` | `{mode: LLM\|OFFLINE, provider, suggestions[]}` |
| 🔑 | `POST /api/chat` | `{message}` → `{reply, mode, notice?, suggestions[]}` |
| 🔑 | `GET /api/chat?limit=50` | Lịch sử |
| 🔑 | `DELETE /api/chat` | Xóa lịch sử → 204 |

Công cụ dữ liệu của AI: `get_menu`, `get_shop_info`, `get_open_orders`, `get_table_status` (mọi vai trò), `get_sales_summary` (chỉ chủ quán / quản lý).

## 09. Thống kê (1 API)

| | API | Mô tả |
|---|---|---|
| 👔 | `GET /api/stats/dashboard?date=` | `{revenue, cashRevenue, transferRevenue, paidCount, orderCount, completedCount, cancelledCount, activeCount, topProducts[], last7Days[]}` – doanh thu tính theo giờ thu tiền |

## 10. Hệ thống & cài đặt (5 API)

| | API | Mô tả |
|---|---|---|
| 🌐 | `GET /api/health` | `{status: "UP"}` – app Android dùng để kiểm tra địa chỉ máy chủ |
| 🌐 | `GET /api/shop` | `{name, address, hours, openNow, firebaseEnabled, aiMode}` (`openNow`: đang trong giờ mở cửa) |
| 👑 | `PUT /api/settings/shop` | `{name, address, hours}` |
| 👑 | `GET /api/settings/ai` | `{provider, geminiModel, geminiKeySet, claudeModel, claudeKeySet}` (không trả khóa) |
| 👑 | `PUT /api/settings/ai` | `{provider, geminiApiKey?, geminiModel?, claudeApiKey?, claudeModel?}` – khóa để trống = giữ nguyên |
