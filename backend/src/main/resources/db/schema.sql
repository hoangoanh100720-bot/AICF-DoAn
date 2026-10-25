-- =============================================================================
-- LƯỢC ĐỒ CƠ SỞ DỮ LIỆU SQLITE - AICF Coffee Mobile
-- -----------------------------------------------------------------------------
-- File này chạy MỖI LẦN backend khởi động (spring.sql.init.mode=always).
-- Mọi lệnh đều là "CREATE ... IF NOT EXISTS" nên chạy lại nhiều lần vẫn an toàn,
-- dữ liệu cũ không bị xóa.
--
-- Quy ước chung:
--   * Khóa chính: id INTEGER PRIMARY KEY AUTOINCREMENT (SQLite tự tăng, không dùng lại id đã xóa).
--   * Ngày giờ: lưu dạng TEXT 'YYYY-MM-DD HH:MM:SS' (giờ Việt Nam). SQLite không có kiểu DATETIME
--     riêng; lưu chuỗi theo định dạng này thì so sánh / sắp xếp theo chữ cũng đúng theo thời gian.
--     Phía Java đổi qua lại bằng lớp LocalDateTimeConverter.
--   * Đúng/Sai (boolean): INTEGER 0 = sai, 1 = đúng (SQLite không có kiểu BOOLEAN).
--   * Tiền: INTEGER, đơn vị đồng (VND không có số lẻ nên không cần kiểu thập phân).
--   * Giá trị liệt kê (vai trò, trạng thái...): TEXT + ràng buộc CHECK để chỉ nhận giá trị hợp lệ.
--   * Khóa ngoại cần PRAGMA foreign_keys=ON (đã bật trên URL kết nối trong application.yml).
--
-- Sơ đồ quan hệ (ERD rút gọn):
--
--   users 1───n orders n───1 dining_tables      (users = nhân viên tạo đơn: orders.created_by)
--     │            │
--     │            1
--     │            │
--     │            n
--     │       order_items n───1 products n───1 categories
--     │
--     ├──1───n chat_messages     (lịch sử chat AI của từng người)
--     ├──1───n device_tokens     (mã FCM của từng điện thoại để gửi thông báo đẩy)
--     └──1───n notifications     (hộp thư thông báo trong app)
--
--   app_settings: bảng khóa - giá trị, lưu cấu hình đổi được lúc chạy (khóa API AI...)
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. USERS - Tài khoản người dùng của quán (chủ quán, quản lý, nhân viên bán hàng).
--    Không có tài khoản khách: tài khoản do chủ quán / quản lý tạo trong app.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    username      TEXT    NOT NULL UNIQUE,              -- tên đăng nhập
    password_hash TEXT,                                 -- mật khẩu đã băm BCrypt; NULL nếu chỉ đăng nhập bằng Google
    full_name     TEXT    NOT NULL,                     -- họ tên hiển thị
    email         TEXT    UNIQUE,                       -- email; trùng email tài khoản Google thì đăng nhập Google được
    phone         TEXT,                                 -- số điện thoại liên hệ
    avatar_url    TEXT,                                 -- ảnh đại diện (Google trả về khi đăng nhập Firebase)
    role          TEXT    NOT NULL DEFAULT 'STAFF'
                  CHECK (role IN ('OWNER', 'MANAGER', 'STAFF')),
                                                        -- OWNER: chủ quán; MANAGER: quản lý; STAFF: nhân viên bán hàng
    firebase_uid  TEXT    UNIQUE,                       -- mã người dùng bên Firebase Authentication (đăng nhập Google)
    active        INTEGER NOT NULL DEFAULT 1,           -- 0 = bị khóa, không đăng nhập được
    created_at    TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    updated_at    TEXT    NOT NULL DEFAULT (datetime('now', 'localtime'))
);


-- -----------------------------------------------------------------------------
-- 2. CATEGORIES - Danh mục món (Cà phê, Trà, Bánh...)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS categories (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT    NOT NULL UNIQUE,                -- tên danh mục, không trùng
    description TEXT,                                   -- mô tả ngắn
    icon        TEXT,                                   -- biểu tượng (emoji) hiển thị trên app, ví dụ '☕'
    sort_order  INTEGER NOT NULL DEFAULT 0,             -- thứ tự hiển thị, số nhỏ đứng trước
    created_at  TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    updated_at  TEXT    NOT NULL DEFAULT (datetime('now', 'localtime'))
);


-- -----------------------------------------------------------------------------
-- 3. PRODUCTS - Món trong thực đơn
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS products (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    category_id INTEGER NOT NULL
                REFERENCES categories (id) ON DELETE RESTRICT,
                                                        -- thuộc danh mục nào; KHÔNG cho xóa danh mục còn món
    name        TEXT    NOT NULL,                       -- tên món
    description TEXT,                                   -- mô tả (AI dùng để tư vấn món)
    price       INTEGER NOT NULL CHECK (price >= 0),    -- giá bán, đơn vị đồng
    image_url   TEXT,                                   -- link ảnh món
    available   INTEGER NOT NULL DEFAULT 1,             -- 1 = đang bán, 0 = tạm hết (vẫn giữ trong đơn cũ)
    created_at  TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    updated_at  TEXT    NOT NULL DEFAULT (datetime('now', 'localtime'))
);
-- Chỉ mục giúp lọc món theo danh mục nhanh hơn
CREATE INDEX IF NOT EXISTS idx_products_category ON products (category_id);


-- -----------------------------------------------------------------------------
-- 4. DINING_TABLES - Bàn trong quán (sơ đồ bàn trên app: bàn trống / đang có khách)
--    Tên bảng không dùng "tables" vì dễ nhầm với từ khóa / khái niệm "bảng" của CSDL.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS dining_tables (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    name       TEXT    NOT NULL UNIQUE,                 -- tên bàn, ví dụ 'Bàn 01'
    area       TEXT,                                    -- khu vực: 'Trong nhà', 'Sân vườn'...
    seats      INTEGER NOT NULL DEFAULT 4 CHECK (seats > 0), -- số ghế
    active     INTEGER NOT NULL DEFAULT 1,              -- 0 = bàn tạm không dùng
    created_at TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    updated_at TEXT    NOT NULL DEFAULT (datetime('now', 'localtime'))
);


-- -----------------------------------------------------------------------------
-- 5. ORDERS - Đơn hàng = "bill" của 1 bàn hoặc 1 đơn mang đi, do NHÂN VIÊN tạo khi khách gọi món.
--    Chi tiết từng món nằm ở order_items.
--    Vòng đời trạng thái:
--      NEW (chờ pha) -> PREPARING (đang pha) -> READY (xong, chờ mang ra) -> SERVED (đã phục vụ)
--      Đã phục vụ + đã thanh toán -> COMPLETED (hoàn tất, bàn trống trở lại)
--      Chưa thanh toán thì có thể -> CANCELLED (hủy)
--    Mỗi bàn chỉ có tối đa 1 đơn đang mở (chưa COMPLETED / CANCELLED): kiểm tra ở OrderService.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS orders (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    code            TEXT    NOT NULL UNIQUE,            -- mã đơn cho người đọc, ví dụ 'DH20261015-0007'
    created_by      INTEGER NOT NULL
                    REFERENCES users (id) ON DELETE RESTRICT,   -- nhân viên tạo đơn
    table_id        INTEGER
                    REFERENCES dining_tables (id) ON DELETE SET NULL, -- bàn (NULL nếu mang đi)
    order_type      TEXT    NOT NULL DEFAULT 'TAKEAWAY'
                    CHECK (order_type IN ('DINE_IN', 'TAKEAWAY')), -- tại bàn / mang đi
    status          TEXT    NOT NULL DEFAULT 'NEW'
                    CHECK (status IN ('NEW', 'PREPARING', 'READY', 'SERVED', 'COMPLETED', 'CANCELLED')),
    total_amount    INTEGER NOT NULL DEFAULT 0 CHECK (total_amount >= 0), -- tổng tiền = tổng (đơn giá x số lượng) các món
    payment_method  TEXT
                    CHECK (payment_method IN ('CASH', 'BANK_TRANSFER', 'E_WALLET')), -- NULL khi chưa thanh toán
    paid            INTEGER NOT NULL DEFAULT 0,         -- 1 = đã thanh toán
    paid_at         TEXT,                               -- lúc thu tiền (doanh thu tính theo ngày thu tiền)
    amount_received INTEGER CHECK (amount_received >= 0), -- tiền mặt khách đưa (tính tiền thối)
    note            TEXT,                               -- ghi chú cho cả đơn (vd 'khách ngồi ngoài ban công')
    cancel_reason   TEXT,                               -- lý do hủy (nếu có)
    created_at      TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    updated_at      TEXT    NOT NULL DEFAULT (datetime('now', 'localtime'))
);
CREATE INDEX IF NOT EXISTS idx_orders_creator ON orders (created_by);
CREATE INDEX IF NOT EXISTS idx_orders_status  ON orders (status);
CREATE INDEX IF NOT EXISTS idx_orders_created ON orders (created_at);  -- lịch sử đơn theo ngày
CREATE INDEX IF NOT EXISTS idx_orders_paid    ON orders (paid_at);     -- thống kê doanh thu theo ngày


-- -----------------------------------------------------------------------------
-- 6. ORDER_ITEMS - Từng dòng món trong đơn
--    Lưu lại product_name và unit_price TẠI THỜI ĐIỂM ĐẶT: sau này quán đổi giá / đổi tên món
--    thì đơn cũ vẫn đúng số tiền đã bán (nguyên tắc "chụp lại dữ liệu" - snapshot).
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS order_items (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    order_id     INTEGER NOT NULL
                 REFERENCES orders (id) ON DELETE CASCADE,   -- xóa đơn thì xóa luôn các dòng món
    product_id   INTEGER
                 REFERENCES products (id) ON DELETE SET NULL, -- món gốc (NULL nếu món đã bị xóa khỏi thực đơn)
    product_name TEXT    NOT NULL,                     -- tên món lúc đặt
    unit_price   INTEGER NOT NULL CHECK (unit_price >= 0), -- đơn giá lúc đặt
    quantity     INTEGER NOT NULL CHECK (quantity > 0),  -- số lượng
    note         TEXT                                  -- ghi chú riêng cho món: 'ít đá', 'không đường'...
);
CREATE INDEX IF NOT EXISTS idx_order_items_order ON order_items (order_id);


-- -----------------------------------------------------------------------------
-- 7. CHAT_MESSAGES - Lịch sử trò chuyện với Trợ lý AI (mỗi người dùng một luồng riêng)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chat_messages (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id    INTEGER NOT NULL
               REFERENCES users (id) ON DELETE CASCADE,
    role       TEXT    NOT NULL CHECK (role IN ('USER', 'ASSISTANT')), -- ai nói: người dùng hay AI
    content    TEXT    NOT NULL,                       -- nội dung tin nhắn
    mode       TEXT,                                   -- với tin của AI: 'LLM' (AI thật) | 'OFFLINE' (chế độ cơ bản)
    created_at TEXT    NOT NULL DEFAULT (datetime('now', 'localtime'))
);
CREATE INDEX IF NOT EXISTS idx_chat_user ON chat_messages (user_id, id);


-- -----------------------------------------------------------------------------
-- 8. DEVICE_TOKENS - Mã thiết bị Firebase Cloud Messaging (FCM)
--    Mỗi điện thoại / trình duyệt cài app sẽ có 1 mã; backend gửi thông báo đẩy tới các mã của người dùng.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS device_tokens (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id    INTEGER NOT NULL
               REFERENCES users (id) ON DELETE CASCADE,
    token      TEXT    NOT NULL UNIQUE,                -- mã FCM (1 mã chỉ thuộc 1 người: đăng nhập tài khoản khác thì chuyển chủ)
    platform   TEXT    NOT NULL DEFAULT 'WEB'
               CHECK (platform IN ('WEB', 'ANDROID', 'IOS')),
    created_at TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    updated_at TEXT    NOT NULL DEFAULT (datetime('now', 'localtime'))
);


-- -----------------------------------------------------------------------------
-- 9. NOTIFICATIONS - Hộp thư thông báo trong app
--    Luôn được lưu kể cả khi chưa cấu hình Firebase -> app vẫn xem được thông báo.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notifications (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id    INTEGER NOT NULL
               REFERENCES users (id) ON DELETE CASCADE,  -- người nhận
    title      TEXT    NOT NULL,                       -- tiêu đề, ví dụ 'Đơn DH...-0007 đã xong'
    body       TEXT    NOT NULL,                       -- nội dung
    order_id   INTEGER
               REFERENCES orders (id) ON DELETE SET NULL, -- đơn liên quan (để bấm vào mở đơn)
    is_read    INTEGER NOT NULL DEFAULT 0,             -- 1 = đã đọc
    created_at TEXT    NOT NULL DEFAULT (datetime('now', 'localtime'))
);
CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications (user_id, is_read);


-- -----------------------------------------------------------------------------
-- 10. APP_SETTINGS - Cấu hình dạng khóa / giá trị, chủ quán đổi được ngay trên app
--     Ví dụ: 'shop.name' = 'AICF Coffee', 'ai.provider' = 'gemini', 'ai.gemini_api_key' = '...'
--     Khóa nào chưa có ở đây thì backend dùng giá trị mặc định trong application.yml.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS app_settings (
    setting_key   TEXT PRIMARY KEY,                    -- tên cấu hình
    setting_value TEXT,                                -- giá trị (luôn lưu dạng chữ)
    updated_at    TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))
);
