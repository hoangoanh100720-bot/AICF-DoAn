# Luồng hoạt động các chức năng

App bán hàng **nội bộ** của quán cà phê: chủ quán mua về cho nhân viên bán hàng và quản lý dùng. Không có phần cho khách.

Các sơ đồ viết bằng **Mermaid**: GitHub và khung xem trước Markdown của VS Code tự vẽ thành hình.
Muốn chèn vào Word / PowerPoint: dán từng khối vào <https://mermaid.live> → *Actions* → tải PNG/SVG.

| # | Sơ đồ | Loại |
|---|---|---|
| 1 | [Kiến trúc tổng thể](#1-kiến-trúc-tổng-thể) | thành phần |
| 2 | [Điều hướng màn hình theo vai trò](#2-điều-hướng-màn-hình-theo-vai-trò) (2a vai trò, 2b router) | flowchart |
| 3 | [Xử lý 1 request REST (bảo mật JWT)](#3-xử-lý-1-request-rest-bảo-mật-jwt) | sequence |
| 4 | [Đăng nhập bằng mật khẩu](#4-đăng-nhập-bằng-mật-khẩu) | sequence |
| 5 | [Đăng nhập Google (Firebase)](#5-đăng-nhập-google-firebase-authentication) | sequence |
| 6 | [Một vòng bán hàng tại bàn](#6-một-vòng-bán-hàng-tại-bàn) | flowchart |
| 7 | [Mở bill (bên trong máy chủ)](#7-mở-bill-bên-trong-máy-chủ) | sequence |
| 8 | [Vòng đời trạng thái bill](#8-vòng-đời-trạng-thái-bill) | state |
| 9 | [Pha chế → phục vụ nhận thông báo](#9-pha-chế--phục-vụ-nhận-thông-báo) | sequence |
| 10 | [Thu tiền](#10-thu-tiền) | sequence |
| 11 | [Thông báo trong app + đẩy FCM](#11-thông-báo-trong-app--thông-báo-đẩy-fcm) | sequence |
| 12 | [Trợ lý AI](#12-trợ-lý-ai) | flowchart |
| 13 | [Quản lý thực đơn & bàn (CRUD tổng quát)](#13-quản-lý-thực-đơn--bàn-crud-tổng-quát) | sequence |
| 14 | [Quản lý tài khoản nhân viên](#14-quản-lý-tài-khoản-nhân-viên) | flowchart |
| 15 | [Tổng quan doanh thu](#15-tổng-quan-doanh-thu) | sequence |
| 16 | [Phiếu gọi món nháp + mạng chập chờn (SQLite trên máy)](#16-phiếu-gọi-món-nháp--mạng-chập-chờn-sqlite-trên-điện-thoại) | flowchart |

---

## 1. Kiến trúc tổng thể

Một máy chủ Spring Boot (cổng 8090) vừa phục vụ giao diện PWA vừa cung cấp REST API dùng chung cho mọi thiết bị trong quán.
App Android chứa sẵn bản sao giao diện, chỉ gọi REST API qua Wi-Fi của quán.

```mermaid
flowchart TB
    subgraph DT["📱 ĐIỆN THOẠI / MÁY TÍNH BẢNG CỦA QUÁN (frontend)"]
        direction LR
        APP["App AICF Coffee · Bán hàng<br/>PWA trên trình duyệt hoặc app Android (Capacitor)<br/>giao diện theo app nhân viên AICF"]
        LDB[("Lưu trên máy<br/>SQLite aicf_local (Android)<br/>localStorage (PWA)<br/>phiên · phiếu gọi món nháp · thực đơn tạm")]
        APP --- LDB
    end

    subgraph SV["🖥️ MÁY CHỦ SPRING BOOT :8090 (backend)"]
        direction LR
        WEB["Phục vụ giao diện PWA<br/>+ Swagger UI"]
        API["REST API /api/** (47 API)<br/>Controller → Service → Repository"]
        DB[("SQLite<br/>aicf-ban-hang.db")]
        API --> DB
    end

    subgraph EXT["☁️ DỊCH VỤ NGOÀI (tùy chọn)"]
        direction LR
        FA["Firebase Auth<br/>đăng nhập Google"]
        FCM["Firebase Cloud Messaging<br/>thông báo đẩy"]
        AI["Gemini / Claude<br/>Trợ lý AI"]
    end

    APP -- "① tải giao diện (PWA)" --> WEB
    APP -- "② gọi API: HTTP + JSON + JWT" --> API
    APP -. "③ đăng nhập Google → ID token" .-> FA
    API -. "④ kiểm tra ID token" .-> FA
    API -. "⑤ gửi thông báo" .-> FCM
    FCM -. "⑥ đẩy tới điện thoại" .-> APP
    API -. "⑦ câu hỏi + công cụ dữ liệu" .-> AI
```

---

## 2. Điều hướng màn hình theo vai trò

**2a. Mỗi vai trò thấy gì.** Bố cục giống app nhân viên AICF: 4 màn đầu nằm trên thanh dưới, phần còn lại vào mục "Thêm".

```mermaid
flowchart TD
    START(["Mở app"]) --> HAS{"Đã đăng nhập?<br/>(token lưu trên máy)"}
    HAS -- "Chưa" --> L["🔐 Đăng nhập<br/>(tài khoản do chủ quán cấp)"]
    L -- "thành công" --> ROLE
    HAS -- "Rồi" --> ROLE{"Vai trò?"}
    ROLE -- "STAFF" --> S["👷 NHÂN VIÊN BÁN HÀNG<br/>Trang đầu: Sơ đồ bàn<br/>Thanh dưới: Bàn · Pha chế · Đơn hàng · Trợ lý AI · Tôi"]
    ROLE -- "MANAGER" --> M["🧑‍💼 QUẢN LÝ<br/>Trang đầu: Tổng quan<br/>Thanh dưới: Tổng quan · Bàn · Pha chế · Đơn hàng · Thêm<br/>Thêm: Trợ lý AI · Thực đơn & bàn · Nhân viên"]
    ROLE -- "OWNER" --> O["👑 CHỦ QUÁN<br/>như Quản lý<br/>+ Cài đặt quán & AI, quản lý mọi tài khoản"]
    S -. "Đăng xuất" .-> L
    M -. "Đăng xuất" .-> L
    O -. "Đăng xuất" .-> L
```

**2b. Router kiểm tra mỗi lần đổi màn** (`js/core/router.js`):

```mermaid
flowchart LR
    N(["Đổi màn hình<br/>vd #/dashboard"]) --> R1{"Có màn này?"}
    R1 -- "Không" --> HOME["Về trang đầu<br/>theo vai trò"]
    R1 -- "Có" --> R2{"Chưa đăng nhập?"}
    R2 -- "Đúng" --> LOGIN["#/login"]
    R2 -- "Không" --> R3{"Vai trò được phép?<br/>(vd /dashboard: OWNER, MANAGER)"}
    R3 -- "Không" --> HOME
    R3 -- "Có" --> OK["Đóng hộp thoại đang mở,<br/>tải code màn hình (import động) và vẽ"]
```

---

## 3. Xử lý 1 request REST (bảo mật JWT)

Mọi API đi qua cùng một đường: bộ lọc JWT → luật quyền → Controller → Service → Repository → SQLite. Lỗi ở bất kỳ bước nào đều được đổi thành cùng một dạng JSON.

```mermaid
sequenceDiagram
    autonumber
    participant App as App (HttpClient)
    participant F as JwtAuthFilter
    participant SC as SecurityConfig<br/>(luật theo đường dẫn)
    participant C as Controller
    participant S as Service
    participant R as Repository (JPA)
    participant DB as SQLite
    participant EH as GlobalExceptionHandler

    App->>F: GET /api/stats/dashboard<br/>Authorization: Bearer <JWT>
    F->>F: kiểm tra chữ ký + hạn token
    F->>R: tìm người dùng theo id trong token
    R->>DB: SELECT * FROM users WHERE id = ?
    DB-->>F: người dùng (active?)
    F->>SC: đặt người đang gọi + ROLE_STAFF / ROLE_MANAGER / ROLE_OWNER
    alt Thiếu / sai token
        SC-->>App: 401 {"message": "Bạn cần đăng nhập..."}
    else Không đủ vai trò (vd nhân viên xem doanh thu)
        SC-->>App: 403 {"message": "Bạn không có quyền..."}
    else Hợp lệ (quản lý / chủ quán)
        SC->>C: StatsController.dashboard()
        C->>S: statsService.dashboard(date)
        S->>R: revenueBetween(), topProducts()...
        R->>DB: SELECT SUM(total_amount) ... WHERE paid = 1 ...
        DB-->>S: số liệu
        S-->>C: Dashboard (DTO)
        C-->>App: 200 JSON
    end
    Note over S,EH: Service ném ApiException (403, 404, 409...) hoặc dữ liệu vi phạm @Valid (400)<br/>→ GlobalExceptionHandler trả {status, message, details}
```

---

## 4. Đăng nhập bằng mật khẩu

Không có đăng ký tự do: chủ quán / quản lý tạo tài khoản ở màn Nhân viên (sơ đồ 14).

```mermaid
sequenceDiagram
    autonumber
    actor U as Nhân viên
    participant App as Màn Đăng nhập
    participant C as AuthController
    participant S as AuthService
    participant DB as SQLite (users)
    participant J as JwtService

    U->>App: nhập tên + mật khẩu (hoặc bấm tài khoản mẫu)
    App->>C: POST /api/auth/login {username, password}
    C->>S: login()
    S->>DB: tìm theo username
    S->>S: BCrypt.matches(mật khẩu, password_hash) (ngoài giao dịch)
    alt Sai tên hoặc sai mật khẩu
        S-->>App: 401 "Sai tên đăng nhập hoặc mật khẩu" (cùng 1 câu, không lộ tên nào tồn tại)
    else Tài khoản bị khóa (nhân viên đã nghỉ)
        S-->>App: 403 "Tài khoản đã bị khóa..."
    else Đúng
        S->>J: issue(user) → JWT (id, vai trò, hạn 72 giờ)
        S-->>App: {token, expiresAt, user}
        App->>App: lưu token + user vào LocalDb, vẽ thanh dưới theo vai trò, mở trang đầu
    end
```

---

## 5. Đăng nhập Google (Firebase Authentication)

Chỉ người đã được chủ quán cấp tài khoản (có nhập email Google) mới vào được.

```mermaid
sequenceDiagram
    autonumber
    actor U as Nhân viên
    participant App as App (PWA)
    participant G as Firebase Auth<br/>(cửa sổ Google)
    participant API as AuthController<br/>POST /api/auth/firebase
    participant FS as FirebaseService<br/>(Admin SDK)
    participant DB as SQLite (users)

    U->>App: bấm "Đăng nhập bằng Google"
    App->>G: signInWithPopup(GoogleAuthProvider)
    G-->>App: Firebase ID token
    App->>API: {idToken}
    API->>FS: verifyIdToken(idToken)
    alt Máy chủ chưa cấu hình Firebase
        FS-->>App: 503 "Máy chủ chưa cấu hình Firebase"
    else Token sai / hết hạn
        FS-->>App: 401
    else Hợp lệ
        FS-->>API: uid, email, ảnh
        API->>DB: tìm theo firebase_uid, chưa có thì theo email
        alt Không có tài khoản nào khớp
            API-->>App: 403 "Tài khoản Google ... chưa được chủ quán cấp quyền"
        else Có
            API->>DB: lưu firebase_uid, ảnh đại diện
            API-->>App: {token JWT của hệ thống, user}
        end
    end
```

---

## 6. Một vòng bán hàng tại bàn

```mermaid
flowchart TD
    A(["Khách vào ngồi Bàn 03"]) --> B["Nhân viên mở Sơ đồ bàn<br/>GET /api/tables + GET /api/orders/active"]
    B --> C{"Bàn 03 đang có bill?"}
    C -- "Trống" --> D["Chạm bàn → mở thẳng màn Chọn món<br/>(lưới ảnh, tìm, lọc nhóm)"]
    C -- "Có khách" --> E["Chạm bàn → mở bill đang có"]
    E -- "Khách gọi thêm" --> D
    D --> F["Chạm món để thêm (×số lượng ở góc ảnh)<br/>phiếu nháp lưu trong SQLite trên máy"]
    F --> G["Món đã chọn: sửa số lượng, ghi chú 'ít đá'<br/>→ Gửi pha chế"]
    G -- "bill mới" --> H["POST /api/orders (DINE_IN, tableId)"]
    G -- "gọi thêm" --> H2["POST /api/orders/{id}/items"]
    H --> K["Quầy pha chế: Bắt đầu → Xong<br/>PATCH /status PREPARING → READY"]
    H2 --> K
    K --> L["Sơ đồ bàn báo đỏ 'Đồ đã xong · mang ra'<br/>+ thông báo cho người mở bill"]
    L --> M["Phục vụ mang ra → 'Đã mang ra'<br/>PATCH /status SERVED"]
    M --> N["Khách tính tiền → Thu tiền<br/>POST /api/orders/{id}/pay"]
    N --> O(["Bill HOÀN TẤT, Bàn 03 trống lại"])
```

---

## 7. Mở bill (bên trong máy chủ)

```mermaid
sequenceDiagram
    autonumber
    participant App as App (bill.js)
    participant C as OrderController
    participant S as OrderService (@Transactional)
    participant DB as SQLite
    participant N as NotificationService

    App->>C: POST /api/orders {orderType: DINE_IN, tableId: 3, items:[{productId, quantity, note}]}
    C->>C: @Valid: có món? số lượng 1–99?
    C->>S: create(me, request)
    S->>DB: bàn 3 tồn tại, đang dùng?
    S->>DB: bàn 3 có bill đang mở? (existsByTableIdAndStatusIn)
    alt Đã có bill
        S-->>App: 409 "Bàn 03 đang có bill, hãy thêm món vào bill đó"
    else Trống
        loop từng món
            S->>DB: lấy món; tạm hết → 409 (cả giao dịch hủy, không lưu gì)
            S->>S: chụp lại tên + giá lúc gọi món
        end
        S->>S: tổng tiền = Σ đơn giá × số lượng (không tin giá app gửi)
        S->>DB: INSERT orders (created_by = nhân viên) + order_items
        S->>DB: mã đơn DH<ngày>-<id>
        S->>N: notifyTeam("Đơn mới · Bàn 03", ...) trừ người vừa tạo
        S-->>App: 201 OrderResponse (status NEW "Chờ pha")
    end
```

---

## 8. Vòng đời trạng thái bill

Luật chuyển nằm ở **một chỗ** (`OrderStatus.canMoveTo`), API và Trợ lý AI đều theo.

```mermaid
stateDiagram-v2
    [*] --> NEW: mở bill / gọi thêm món
    NEW --> PREPARING: Bắt đầu pha
    NEW --> READY: Xong (pha nhanh)
    PREPARING --> READY: Xong
    READY --> SERVED: Đã mang ra
    READY --> NEW: khách gọi thêm món
    SERVED --> NEW: khách gọi thêm món
    SERVED --> COMPLETED: đã thu tiền (máy chủ tự chuyển)
    NEW --> CANCELLED: hủy (nhân viên được)
    PREPARING --> CANCELLED: hủy (chỉ quản lý / chủ quán)
    READY --> CANCELLED: hủy (chỉ quản lý / chủ quán)
    SERVED --> CANCELLED: hủy (chỉ quản lý / chủ quán)
    COMPLETED --> [*]
    CANCELLED --> [*]
    note right of SERVED
        Thu tiền trước (đơn mang đi) cũng được:
        paid = true, bill vẫn mở tới khi SERVED
        rồi mới COMPLETED. Đã thu tiền thì không hủy được.
    end note
```

---

## 9. Pha chế → phục vụ nhận thông báo

```mermaid
sequenceDiagram
    autonumber
    actor B as Pha chế
    participant K as Màn Pha chế (kitchen.js)<br/>tự làm mới 5 giây
    participant API as OrderController / OrderService
    participant N as NotificationService
    actor P as Nhân viên mở bill
    participant T as Sơ đồ bàn (tables.js)

    K->>API: GET /api/orders/active
    API-->>K: bill NEW / PREPARING / READY → 3 cột (bill chờ > 10 phút viền đỏ)
    B->>K: bấm "Bắt đầu"
    K->>API: PATCH /api/orders/12/status {PREPARING}
    B->>K: bấm "Xong"
    K->>API: PATCH /api/orders/12/status {READY}
    API->>N: notifyUsers([người mở bill], "Bàn 03 đã xong", "Mang ra cho khách: ...")
    N-->>P: thông báo trong app (+ FCM nếu bật)
    T->>API: GET /api/orders/active (5 giây sau)
    T-->>P: ô Bàn 03 chuyển đỏ "Đồ đã xong · mang ra"
    P->>T: chạm Bàn 03 → "Đã mang ra"
    T->>API: PATCH /api/orders/12/status {SERVED}
```

---

## 10. Thu tiền

Mẫu **Strategy** ở app (`payment.js`): mỗi cách trả là 1 đối tượng tự vẽ phần nhập và tạo dữ liệu gửi đi.

```mermaid
sequenceDiagram
    autonumber
    actor P as Nhân viên
    participant UI as Hộp Thanh toán (payment.js)
    participant API as OrderService.pay()
    participant DB as SQLite

    P->>UI: bill Bàn 03 → "Thu tiền 103.000đ"
    UI->>UI: chọn Tiền mặt / Chuyển khoản / Ví điện tử
    P->>UI: Tiền mặt: bấm nhanh "200.000"
    UI->>UI: hiện "Tiền thối: 97.000đ" (tính ngay trên máy)
    P->>UI: "Xác nhận đã thu"
    UI->>API: POST /api/orders/12/pay {paymentMethod: CASH, amountReceived: 200000}
    API->>API: đã thu rồi? → 409 · tiền đưa < tổng? → 400
    API->>DB: paid = 1, paid_at = bây giờ, payment_method, amount_received
    API->>API: đã SERVED → COMPLETED (bàn trống lại)
    API-->>UI: {changeAmount: 97000, status: COMPLETED}
    UI-->>P: màn "Đã thu tiền" + tiền thối chữ to
```

---

## 11. Thông báo trong app + thông báo đẩy (FCM)

```mermaid
sequenceDiagram
    autonumber
    actor U as Người dùng
    participant App as App
    participant FB as Firebase (JS SDK /<br/>plugin Push Android)
    participant API as Máy chủ
    participant DB as SQLite
    participant FCM as Firebase Cloud Messaging

    Note over U,FCM: ① Đăng ký thiết bị (1 lần, sau khi đăng nhập)
    U->>App: Thêm / Tôi → "Bật thông báo đẩy"
    App->>FB: xin quyền thông báo + lấy mã thiết bị
    FB-->>App: FCM token
    App->>API: POST /api/devices {token, platform: WEB | ANDROID}
    API->>DB: lưu device_tokens (mã đã có thì chuyển sang người dùng hiện tại)

    Note over U,FCM: ② Khi có sự kiện: đơn mới / gọi thêm (cả quán), đồ đã xong (người mở bill), đơn hủy (chủ quán, quản lý)
    API->>DB: INSERT notifications (luôn lưu, kể cả chưa bật Firebase)
    API->>DB: lấy các mã thiết bị của người nhận
    API->>FCM: sendEachForMulticast (chạy nền)
    FCM-->>App: thông báo hiện trên điện thoại / trình duyệt
    FCM-->>API: mã nào đã hết hiệu lực
    API->>DB: xóa các mã hết hiệu lực

    Note over U,FCM: ③ Bấm vào thông báo (hoặc chuông trên thanh trên)
    U->>App: bấm thông báo (data.orderId)
    App->>App: mở #/history?order={orderId} → mở bill
    App->>API: POST /api/notifications/{id}/read

    Note over U,FCM: ④ Đăng xuất
    App->>API: DELETE /api/devices?token=... (không nhận thông báo của tài khoản cũ)
```

---

## 12. Trợ lý AI

Tận dụng thiết kế Trợ lý AI của dự án AICF: chọn nhà cung cấp (Strategy), AI chỉ lấy số liệu qua **công cụ** (function calling), lỗi thì tự chuyển sang chế độ cơ bản.

```mermaid
flowchart TD
    Q(["Người dùng gửi câu hỏi<br/>POST /api/chat"]) --> H["Lấy 12 lượt chat gần nhất<br/>+ lưu câu hỏi vào chat_messages"]
    H --> K{"Dịch vụ AI đang chọn<br/>đã có khóa API?"}
    K -- "Chưa" --> OFF
    K -- "Có" --> P{"ai.provider"}
    P -- "gemini" --> GEM["GeminiProvider<br/>(REST)"]
    P -- "claude" --> CLA["ClaudeProvider<br/>(SDK anthropic-java)"]
    GEM --> L1
    CLA --> L1
    L1["① Gửi cho AI: lời dặn hệ thống (tên, vai trò người hỏi) + lịch sử + câu hỏi<br/>+ công cụ được phép theo vai trò"] --> L2{"② AI muốn<br/>gọi công cụ?"}
    L2 -- "Có" --> L3["③ AiToolRegistry chạy công cụ<br/>(kiểm tra quyền lần 2), lấy số liệu thật"]
    L3 -- "④ gửi kết quả JSON, lặp lại<br/>(tối đa 6 vòng)" --> L1
    L2 -- "Không" --> L5["Câu trả lời bằng chữ<br/>mode = LLM"]
    L1 -- "lỗi mạng / khóa sai / hết lượt" --> NOTICE["Kèm ghi chú:<br/>'AI đang gặp sự cố...'"]
    NOTICE --> OFF["OfflineAssistant — chế độ cơ bản<br/>trả lời theo từ khóa, vẫn dùng số liệu thật<br/>mode = OFFLINE"]
    L5 --> SAVE["Lưu câu trả lời vào chat_messages"]
    OFF --> SAVE
    SAVE --> R(["Trả về {reply, mode, notice, suggestions}"])
    L3 -.- TOOLS["Công cụ:<br/>get_menu · get_shop_info · get_open_orders · get_table_status (mọi vai trò)<br/>get_sales_summary (chủ quán, quản lý)"]
```

---

## 13. Quản lý thực đơn & bàn (CRUD tổng quát)

Món, Danh mục, Bàn dùng **chung một khung** ở cả hai phía: `crudPage()` + `createCrudRepository()` trên app, `CrudController` + `CrudService` trên máy chủ.

```mermaid
sequenceDiagram
    autonumber
    actor QL as Quản lý / Chủ quán
    participant UI as crudPage() + formSheet()<br/>(form tự sinh từ cấu hình)
    participant R as createCrudRepository('products')
    participant C as CrudController<br/>(ProductController)
    participant S as CrudService<br/>(ProductService)
    participant DB as SQLite

    QL->>UI: Thêm → Thực đơn & bàn → tab Món
    UI->>R: list()
    R->>C: GET /api/products
    C->>S: findAll()
    S->>DB: SELECT products JOIN categories (+ top bán chạy → bestSeller)
    DB-->>UI: danh sách thẻ có ảnh, nhãn "Bán chạy" / "Tạm hết"
    QL->>UI: "Thêm món" → điền form → Lưu
    UI->>UI: kiểm tra required / min trên máy
    UI->>R: create(data)
    R->>C: POST /api/products (OWNER, MANAGER)
    C->>S: create() — khung chung của lớp cha
    S->>S: validate() [lớp con: danh mục tồn tại? tên trùng?]
    S->>S: newEntity() + apply() [lớp con chép trường]
    S->>DB: INSERT
    S-->>UI: 201 → "Đã thêm món" → tải lại danh sách
    QL->>UI: mở danh mục → "Xóa danh mục"
    UI->>QL: hộp xác nhận
    UI->>C: DELETE /api/categories/{id}
    C->>S: delete() → beforeDelete() [còn món? → 409]
    S-->>UI: 204 hoặc 409 "Danh mục còn 5 món..."
```

---

## 14. Quản lý tài khoản nhân viên

```mermaid
flowchart TD
    A(["Màn Nhân viên<br/>GET /api/users"]) --> B{"Ai đang dùng?"}
    B -- "Chủ quán" --> O["Tạo / sửa mọi tài khoản<br/>gán vai trò Chủ quán, Quản lý, Nhân viên"]
    B -- "Quản lý" --> M["Chỉ tạo / sửa tài khoản Nhân viên bán hàng<br/>(ô vai trò chỉ có 1 lựa chọn; máy chủ kiểm tra lại → 403)"]
    O --> C["POST /api/users {username, password, fullName, role, email?}"]
    M --> C
    C --> D{"Trùng tên đăng nhập / email?"}
    D -- "Có" --> E["409"]
    D -- "Không" --> F["Tạo tài khoản (mật khẩu băm BCrypt) → 201<br/>nhân viên đăng nhập được ngay"]
    O --> G["PUT /api/users/{id}<br/>đổi vai trò · khóa khi nghỉ việc · cấp lại mật khẩu"]
    M --> G
    G --> H{"Tự khóa / tự hạ quyền mình?"}
    H -- "Có" --> I["400 (tránh quán mất người quản trị)"]
    H -- "Không" --> J["Lưu; tài khoản bị khóa đăng nhập → 403,<br/>token cũ hết tác dụng ngay (JwtAuthFilter kiểm tra active)"]
```

---

## 15. Tổng quan doanh thu

```mermaid
sequenceDiagram
    autonumber
    actor QL as Quản lý / Chủ quán
    participant UI as Tổng quan (dashboard.js)
    participant C as StatsController
    participant S as StatsService
    participant DB as SQLite

    QL->>UI: mở app (trang đầu) / chọn ngày
    UI->>C: GET /api/stats/dashboard?date=YYYY-MM-DD (OWNER, MANAGER)
    UI->>UI: song song GET /api/orders/active (bàn có khách, bill chờ pha / chưa thu)
    C->>S: dashboard(date)
    S->>DB: SUM(total_amount) bill đã thu tiền trong ngày (theo paid_at), tách tiền mặt
    S->>DB: COUNT đơn trong ngày (tổng / hoàn tất / hủy), số bill đã thu
    loop 7 ngày gần nhất
        S->>DB: SUM doanh thu từng ngày
    end
    S->>DB: TOP 5 món: GROUP BY product_name (tên đã chụp lại lúc gọi món)
    S-->>UI: {revenue, cashRevenue, transferRevenue, paidCount, ..., topProducts, last7Days}
    UI->>UI: ô KPI lớn + biểu đồ cột 7 ngày + món bán chạy + "Cần chú ý"
```

---

## 16. Phiếu gọi món nháp + mạng chập chờn (SQLite trên điện thoại)

```mermaid
flowchart TD
    S(["Mở app"]) --> I["initLocalDb()"]
    I --> N{"Đang chạy trong app Android<br/>và có plugin SQLite?"}
    N -- "Có" --> SQ[("SQLite aicf_local<br/>kv · cart_items · cached_products")]
    N -- "Không (PWA)" --> WS[("localStorage<br/>cùng các hàm kvGet, cartList...")]
    SQ --> M
    WS --> M
    M["Chạm Bàn 03 → màn Chọn món: productRepo.menu()"] --> NET{"Gọi GET /api/products được?"}
    NET -- "Được" --> OK["Hiện thực đơn mới<br/>+ lưu bản sao vào cached_products"]
    NET -- "Mất mạng (lỗi status 0)" --> C{"Có bản sao?"}
    C -- "Có" --> OLD["Hiện thực đơn đã lưu<br/>+ dải báo 'Đang mất mạng'"]
    C -- "Không" --> ERR["Báo 'Mất kết nối' + thử lại"]
    OK --> D{"Phiếu nháp trong máy<br/>cùng đích 'table:3'?"}
    OLD --> D
    D -- "Có" --> R["Khôi phục món đã chọn dở<br/>(app bị tắt / có khách gọi giữa chừng)"]
    D -- "Không" --> NEW["Phiếu mới (bỏ phiếu của bàn khác)"]
    R --> CART["Chạm món → ghi cart_items (không cần mạng)"]
    NEW --> CART
    CART --> SEND["Gửi pha chế: cần mạng<br/>(máy chủ tính giá thật) → xong thì xóa phiếu"]

    subgraph PWA["PWA: service worker (sw.js)"]
        W1["Tải giao diện: có mạng lấy bản mới + cập nhật cache,<br/>mất mạng dùng bản trong cache"]
        W2["/api/**: luôn đi mạng"]
    end
```
