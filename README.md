# AICF Coffee

App di động **dành cho chủ quán cà phê** mua về dùng trong quán: nhân viên bán hàng gọi món – pha chế – thu tiền, quản lý sửa thực đơn và xem doanh thu, chủ quán quản lý tài khoản và cài đặt. **Không có phần cho khách.**

Phát triển lại từ dự án **AICF** (bản 2.0, lấy từ GitHub `NhatT4n21/AICF`), gói gọn cho đồ án:

- **Giao diện giữ nguyên design và trải nghiệm của app nhân viên AICF** (`web-customer/nhan-vien.html`): bảng màu "cà phê rang", phông Be Vietnam Pro, thanh trên + thanh điều hướng dưới theo vai trò, mọi thao tác trong hộp thoại kéo lên, sơ đồ bàn theo khu vực, màn chọn món lưới ảnh, màn Pha chế 3 cột, thanh toán tiền mặt có tiền thối, Tổng quan dạng KPI, Trợ lý AI dạng bong bóng chat. CSS `style.css` + `staff.css` chép từ AICF.
- **Frontend** (`frontend/`): HTML/CSS/JavaScript thuần (ES Modules, không cần build), chạy được:
  - **PWA**: mở trên trình duyệt điện thoại → "Thêm vào màn hình chính", chạy toàn màn hình.
  - **App Android** đóng gói bằng **Capacitor** (`frontend/android`), lưu dữ liệu cục bộ bằng **SQLite**.
- **Backend** (`backend/`): **REST API Spring Boot 3 (Java 21)** dùng chung cho mọi thiết bị trong quán – **47 API, 10 nhóm**, CSDL **SQLite**, xác thực **JWT**, **Firebase** (đăng nhập Google + thông báo đẩy FCM), **Trợ lý AI** dùng lại từ AICF (Gemini / Claude, có chế độ cơ bản khi không có khóa API).

```
AICF-DoAn/
├── backend/                     Spring Boot REST API
│   ├── src/main/java/vn/aicf/mobile/
│   │   ├── common/              phần dùng lại: CrudService, CrudController, BaseEntity, xử lý lỗi
│   │   ├── config/              bảo mật JWT + phân quyền 3 vai trò, CORS, Swagger, tài khoản ban đầu, phục vụ PWA
│   │   ├── domain/              Entity (ánh xạ bảng SQLite), Role, OrderStatus (luật chuyển trạng thái)
│   │   ├── repository/          truy vấn Spring Data JPA
│   │   ├── service/             nghiệp vụ (bán hàng, thống kê, thông báo, tài khoản…)
│   │   │   ├── ai/              Trợ lý AI: Gemini, Claude, công cụ dữ liệu, chế độ cơ bản
│   │   │   └── firebase/        Firebase Admin SDK: xác thực Google, gửi FCM
│   │   └── web/                 Controller REST + DTO
│   ├── src/main/resources/
│   │   ├── application.yml      cấu hình (có chú thích)
│   │   └── db/schema.sql        LƯỢC ĐỒ SQLITE (chú thích từng bảng, từng cột)
│   │       db/data.sql          dữ liệu mẫu (thực đơn, bàn)
│   ├── src/test/…/ApiFlowTest   kiểm thử tích hợp toàn bộ API + Swagger
│   └── run.cmd
├── frontend/
│   ├── www/                     giao diện app (PWA)
│   │   ├── index.html, manifest.webmanifest, sw.js (service worker)
│   │   ├── css/                 style.css + staff.css (design AICF), app.css (phần riêng)
│   │   ├── fonts/               Be Vietnam Pro (đóng gói sẵn, giấy phép OFL)
│   │   └── js/
│   │       ├── core/            HTTP client, router, store, DOM, định dạng
│   │       ├── data/            repository REST, SQLite cục bộ, phiên đăng nhập, phiếu gọi món nháp
│   │       ├── firebase/        cấu hình, đăng nhập Google, thông báo đẩy
│   │       ├── ui/              component dùng chung (sheet, form tự sinh…), trang CRUD tổng quát
│   │       └── pages/           từng màn hình + hộp thoại bill / chọn món / thanh toán
│   ├── android/                 dự án Android (Capacitor) – mở bằng Android Studio
│   ├── tests/e2e.mjs            kiểm thử giao diện đầu-cuối (Chrome headless)
│   ├── capacitor.config.json
│   └── package.json
└── docs/
    ├── Phan-cong-nhiem-vu.docx  phân công 5 thành viên theo nhóm REST API, file code từng người
    ├── Huong-dan-REST-API-Spring.docx  hướng dẫn đọc / viết REST API bằng Spring Boot
    ├── flows.md                 sơ đồ luồng hoạt động các chức năng (Mermaid)
    ├── database.md              mô tả cơ sở dữ liệu (ERD, từng bảng)
    └── api.md                   danh sách 47 REST API + ví dụ
```

---

## 1. Chạy thử nhanh (5 phút)

**Cần:** JDK 21+ (đã có sẵn trên máy: `C:\Program Files\Java\jdk-21.0.11`). Không cần cài Maven, không cần cài CSDL.

```bat
cd backend
run.cmd
```

Lần đầu Maven tải thư viện mất 1–3 phút. Khi thấy `Started MobileBackendApplication`:

| Mở | Để làm gì |
|---|---|
| <http://localhost:8090> | **App (PWA)**. Trên Chrome máy tính: F12 → bật chế độ điện thoại (Ctrl+Shift+M) cho giống app |
| <http://localhost:8090/swagger-ui.html> | **Tài liệu REST API** tự sinh, gọi thử từng API (bấm *Authorize*, dán token) |
| `backend/data/aicf-ban-hang.db` | **File SQLite** – mở bằng [DB Browser for SQLite](https://sqlitebrowser.org/) để xem bảng |

Tài khoản ban đầu (tạo tự động lần đầu chạy; màn đăng nhập có nút bấm để điền nhanh):

| Tên đăng nhập | Mật khẩu | Vai trò | Vào app thấy |
|---|---|---|---|
| `chu` | `chu123` | Chủ quán | Tổng quan, Bàn, Pha chế, Đơn hàng · Thêm: Trợ lý AI, Thực đơn & bàn, Nhân viên, **Cài đặt quán & AI** |
| `quanly` | `quanly123` | Quản lý | Tổng quan, Bàn, Pha chế, Đơn hàng · Thêm: Trợ lý AI, Thực đơn & bàn, Nhân viên (chỉ tài khoản nhân viên) |
| `nhanvien` | `nhanvien123` | Nhân viên bán hàng | Bàn, Pha chế, Đơn hàng, Trợ lý AI, Tôi |

`APP_SEED_DEMO=false`: lần đầu chỉ tạo tài khoản chủ quán `chu / chu123`, chủ quán tự tạo tài khoản cho nhân viên ở mục **Nhân viên**. Nhớ đổi mật khẩu khi dùng thật.

**Dùng trên điện thoại thật (PWA):** điện thoại và máy tính cùng Wi-Fi → mở `http://<IP máy tính>:8090`.

> **Đã chạy bản cũ (có phần khách)?** Bản này dùng CSDL mới `aicf-ban-hang.db` (cấu trúc bảng đơn hàng khác). Tắt máy chủ cũ rồi chạy lại `run.cmd`; file cũ `aicf-mobile.db` không còn dùng, xóa được.

### Kịch bản demo gợi ý
1. Đăng nhập **nhanvien** → *Bàn*: sơ đồ bàn theo khu vực. Chạm **Bàn 03** → màn chọn món mở ngay: tìm "latte", lọc nhóm, chạm món để thêm (góc ảnh hiện ×2).
2. **Tắt hẳn app rồi mở lại, chạm lại Bàn 03: các món chọn dở vẫn còn** (phiếu nháp trong SQLite / LocalDb).
3. *Xem món đã chọn* → ghi chú "ít đá" → *Gửi pha chế* → bill Bàn 03 (Chờ pha), ô bàn chuyển cam.
4. Mở cửa sổ ẩn danh (hoặc điện thoại thứ 2) đăng nhập **quanly** → chuông có thông báo "Đơn mới · Bàn 03".
5. *Pha chế* → *Bắt đầu* → *Xong* → sơ đồ bàn báo đỏ "Đồ đã xong · mang ra" → chạm bàn → *Đã mang ra*.
6. *Thu tiền* → Tiền mặt → bấm "200.000" → app hiện **tiền thối** → *Xác nhận* → bàn trống lại.
7. *Trợ lý AI*: nhân viên hỏi "Còn bàn trống không?", quản lý hỏi "Doanh thu hôm nay?" (nhân viên hỏi thì bị từ chối).
8. **quanly** → *Tổng quan* (doanh thu tách tiền mặt / chuyển khoản, biểu đồ 7 ngày, món bán chạy) → *Thực đơn & bàn* thêm món / bàn → *Nhân viên* tạo tài khoản mới.
9. **chu** → *Cài đặt quán & AI*: sửa giờ mở cửa (nhãn "Đang mở cửa" trên thanh trên đổi theo), nhập khóa API AI.

---

## 2. App Android (Capacitor)

File cài: `frontend/android/app/build/outputs/apk/debug/app-debug.apk`.

Build lại sau khi sửa giao diện:

```bat
cd frontend
npm install               (lần đầu)
npx cap sync android      (chép www/ vào app Android + cập nhật plugin)
npx cap open android      (mở Android Studio → Run)
:: hoặc build APK bằng dòng lệnh:
cd android && gradlew.bat assembleDebug
```

- **Địa chỉ máy chủ:** app trên máy ảo mặc định gọi `http://10.0.2.2:8090` (= máy tính chạy máy ảo). Điện thoại thật: ở màn Đăng nhập bấm *Địa chỉ máy chủ* → nhập `http://<IP máy tính>:8090` → *Kiểm tra & lưu*.
- **SQLite trên điện thoại:** plugin `@capacitor-community/sqlite`, CSDL `aicf_local` gồm 3 bảng `kv`, `cart_items` (phiếu gọi món nháp), `cached_products` (xem `frontend/www/js/data/local-db.js`). Trên trình duyệt (PWA) không có SQLite nên dùng localStorage với cùng giao diện hàm.
- Plugin dùng: `@capacitor-community/sqlite`, `@capacitor/push-notifications`, `@capacitor/app` (nút Back cứng: đóng hộp thoại đang mở trước, rồi mới lùi màn).

---

## 3. Firebase (tùy chọn – app vẫn chạy đầy đủ khi chưa cấu hình)

| Tính năng Firebase | Dùng ở đâu |
|---|---|
| **Authentication (Google)** | Nút *Đăng nhập bằng Google* (PWA). App lấy ID token → `POST /api/auth/firebase` → backend kiểm tra bằng Firebase Admin SDK → cấp JWT riêng. **Chỉ email đã được chủ quán nhập vào tài khoản** mới vào được |
| **Cloud Messaging (FCM)** | Đơn mới / gọi thêm → cả quán nhận (quầy pha chế); pha xong → người mở bill nhận "mang ra"; đơn hủy → chủ quán, quản lý nhận. Backend gửi bằng Admin SDK (`PushSender`), app nhận qua service worker (PWA) hoặc plugin Push (Android) |

Các bước:
1. Tạo dự án tại <https://console.firebase.google.com>.
2. **Backend:** *Project settings → Service accounts → Generate new private key* → lưu thành `backend/data/firebase-service-account.json` → khởi động lại backend (log báo `Firebase: đã kết nối dự án …`).
3. **App web:** *Project settings → General → Add app (Web)* → chép cấu hình vào `frontend/www/js/firebase/firebase-config.js`.
   *Authentication → Sign-in method* → bật **Google**. *Cloud Messaging → Web Push certificates* → tạo khóa → dán vào `VAPID_KEY`.
4. **App Android:** *Add app (Android)*, package `vn.aicf.mobile` → tải `google-services.json` vào `frontend/android/app/` → đặt `NATIVE_PUSH_ENABLED = true` → `npx cap sync android` và build lại.
5. Trong app: *Thêm / Tôi → Bật thông báo đẩy*.

> File service account là **bí mật** (đã có trong `.gitignore`). Cấu hình web trong `firebase-config.js` là khóa công khai phía client, đưa vào app được.

---

## 4. Trợ lý AI (tận dụng từ dự án AICF)

Giữ nguyên kiến trúc Trợ lý AI của AICF, rút gọn cho đồ án:

- `LlmProvider` (mẫu **Strategy**): `GeminiProvider` (REST) và `ClaudeProvider` (SDK chính thức `anthropic-java`, model mặc định `claude-opus-5-5`). Thêm hãng AI mới = thêm 1 lớp.
- `AiToolRegistry`: AI **không tự bịa số liệu** mà gọi "công cụ" đọc dữ liệu thật: `get_menu`, `get_shop_info`, `get_open_orders`, `get_table_status` (mọi vai trò), `get_sales_summary` (chỉ chủ quán / quản lý – kiểm tra quyền 2 lớp).
- `OfflineAssistant`: **chế độ cơ bản** trả lời theo từ khóa khi chưa có khóa API hoặc AI đang lỗi → demo không cần Internet.
- Lịch sử chat lưu trong bảng `chat_messages`.

Bật AI thật: đăng nhập **chu** → *Thêm → Cài đặt quán & AI* → chọn dịch vụ, nhập khóa API (lưu trong bảng `app_settings`, không bao giờ trả lại cho app). Hoặc đặt biến môi trường trước khi chạy: `set GEMINI_API_KEY=...` (hoặc `set AI_PROVIDER=claude` + `set ANTHROPIC_API_KEY=...`).

---

## 5. Kiến trúc & tái sử dụng code

### Backend – phân lớp
```
Controller (web)  →  Service  →  Repository  →  Entity  ⇄  SQLite
     DTO + @Valid      nghiệp vụ    Spring Data JPA   @Entity    schema.sql
```
| Thành phần tái sử dụng | Dùng lại ở |
|---|---|
| `common/CrudService<E, REQ, RES>` (Template Method): khung thêm/đọc/sửa/xóa + móc `validate`, `beforeDelete`, `findAll(filters)` | `CategoryService`, `ProductService`, `TableService` |
| `common/CrudController<REQ, RES>`: sinh sẵn 5 API REST chuẩn | `CategoryController`, `ProductController`, `TableController` (chỉ khai báo đường dẫn) |
| `common/BaseEntity`: `id`, `created_at`, `updated_at` tự điền | mọi Entity có 3 cột này |
| `common/GlobalExceptionHandler` + `ApiException`: mọi lỗi trả cùng 1 dạng JSON | toàn bộ API |
| `OrderStatus.canMoveTo()`: luật chuyển trạng thái bill đặt ở 1 chỗ | API đơn hàng, Trợ lý AI |
| `Role.isManager()` + `SecurityConfig`: phân quyền 3 vai trò tập trung | toàn bộ API |
| `NotificationService`: lưu hộp thư + đẩy FCM (`notifyTeam`, `notifyManagers`, `notifyUsers`) | mở bill, gọi thêm, pha xong, hủy đơn |

### Frontend – các lớp
| Thư mục | Vai trò | Ví dụ tái sử dụng |
|---|---|---|
| `core/` | hạ tầng không phụ thuộc nghiệp vụ | `HttpClient` (gắn JWT, đổi lỗi), `Router` (route + phân quyền), `createStore` (Observer) |
| `data/` | truy cập dữ liệu (Repository) | `createCrudRepository('categories')` cho mọi tài nguyên CRUD; `localDb` (Adapter: SQLite ↔ localStorage); `cart` (phiếu gọi món nháp) |
| `ui/` | component dùng chung (cùng ngôn ngữ thiết kế AICF) | `sheet()` hộp thoại kéo lên; `formSheet()` form tự sinh từ cấu hình trường; `poll()` tự làm mới; **`crudPage()`** dựng cả màn quản lý từ 1 đoạn cấu hình (Món, Danh mục, Bàn) |
| `pages/` | màn hình | `tables.js` sơ đồ bàn (State: mỗi trạng thái bàn 1 màu + dòng chữ), `bill.js`, `picker.js`, `payment.js` (Strategy: mỗi cách trả 1 đối tượng), `kitchen.js`, `dashboard.js`… |

---

## 6. Kiểm thử

| Lệnh | Kiểm tra |
|---|---|
| `cd backend && run.cmd test` | 9 bài kiểm thử tích hợp (JUnit + MockMvc trên SQLite thật): phân quyền 3 vai trò, vòng bán hàng tại bàn đầy đủ (mở bill, gọi thêm, bớt món, pha chế, thu tiền + tiền thối), mang đi trả trước, luật hủy đơn, quản lý tài khoản, CRUD, Trợ lý AI, giờ mở cửa, **Swagger đủ 47 API / 10 nhóm** |
| `cd frontend && node tests/e2e.mjs http://localhost:8090` | 48 bước kiểm thử giao diện trên Chrome ẩn (cỡ màn hình điện thoại), chụp ảnh từng màn vào `tests/ket-qua/` – dùng làm hình minh họa báo cáo |

---

## 7. Công nghệ

| Phần | Công nghệ |
|---|---|
| Backend | Java 21, Spring Boot 3.5 (Web, Data JPA, Security, Validation), Hibernate + SQLite dialect, sqlite-jdbc, JJWT, Firebase Admin SDK 9, anthropic-java, springdoc-openapi (Swagger), Lombok |
| CSDL | SQLite (máy chủ: `aicf-ban-hang.db`; điện thoại: `aicf_local`) |
| Frontend | HTML5, CSS (design AICF: biến màu, chế độ tối tự động, phông Be Vietnam Pro), JavaScript ES Modules, Service Worker, Web App Manifest, Firebase JS SDK 10 |
| Di động | Capacitor 8 (Android), @capacitor-community/sqlite, @capacitor/push-notifications |

Tài liệu cho nhóm: [docs/Phan-cong-nhiem-vu.docx](docs/Phan-cong-nhiem-vu.docx) (phân công 5 thành viên) · [docs/Huong-dan-REST-API-Spring.docx](docs/Huong-dan-REST-API-Spring.docx) (hướng dẫn code REST API bằng Spring)

Tài liệu chi tiết: [docs/flows.md](docs/flows.md) (sơ đồ luồng hoạt động) · [docs/database.md](docs/database.md) · [docs/api.md](docs/api.md)
