-- =============================================================================
-- DỮ LIỆU MẪU - chỉ thêm khi bảng còn TRỐNG (lần chạy đầu tiên)
-- -----------------------------------------------------------------------------
-- Mẫu câu lệnh:  INSERT INTO bang (...) SELECT ... WHERE NOT EXISTS (SELECT 1 FROM bang);
-- => Lần sau khởi động, bảng đã có dữ liệu nên câu lệnh không thêm gì (không ghi đè sửa đổi của chủ quán).
-- Tài khoản người dùng KHÔNG nằm ở đây vì mật khẩu phải băm BCrypt: xem lớp DataSeeder.java.
-- Ảnh món: đường dẫn tương đối /img/... do backend phục vụ từ thư mục frontend/www/img.
-- =============================================================================

-- ---------- Danh mục (id cố định để các món bên dưới tham chiếu) ----------
INSERT INTO categories (id, name, description, icon, sort_order)
SELECT * FROM (
    SELECT 1, 'Cà phê', 'Cà phê pha phin và pha máy', '☕', 1
    UNION ALL SELECT 2, 'Trà & Trà sữa', 'Trà trái cây, trà sữa', '🍵', 2
    UNION ALL SELECT 3, 'Đá xay', 'Đồ uống đá xay mát lạnh', '🧋', 3
    UNION ALL SELECT 4, 'Bánh ngọt', 'Bánh ăn kèm', '🍰', 4
) WHERE NOT EXISTS (SELECT 1 FROM categories);

-- ---------- Món (category_id, tên, mô tả, giá, ảnh) ----------
-- Gộp nhiều dòng trong 1 câu INSERT ... SELECT ... UNION ALL ... để vẫn giữ điều kiện "bảng trống"
INSERT INTO products (category_id, name, description, price, image_url, available)
SELECT * FROM (
    SELECT 1, 'Cà phê sữa đá',   'Cà phê phin truyền thống pha sữa đặc, đậm vị',      29000, '/img/ly-va-hat.jpg', 1
    UNION ALL SELECT 1, 'Cà phê đen đá', 'Phin robusta nguyên chất, không đường hoặc ít đường', 25000, '/img/hat-ca-phe.jpg', 1
    UNION ALL SELECT 1, 'Bạc xỉu',       'Nhiều sữa, ít cà phê, hợp người mới uống',          32000, '/img/latte.jpg', 1
    UNION ALL SELECT 1, 'Latte',         'Espresso với sữa tươi đánh nóng, có latte art',     45000, '/img/latte.jpg', 1
    UNION ALL SELECT 1, 'Cappuccino',    'Espresso, sữa nóng và lớp bọt sữa dày',             45000, '/img/pha-che.jpg', 1
    UNION ALL SELECT 2, 'Trà đào cam sả','Trà đen, đào ngâm, cam tươi và sả, thanh mát',      39000, NULL, 1
    UNION ALL SELECT 2, 'Trà sữa trân châu','Trà sữa đài loan với trân châu đường đen',       35000, NULL, 1
    UNION ALL SELECT 2, 'Trà vải',       'Trà xanh nhài với vải thiều',                       39000, NULL, 1
    UNION ALL SELECT 3, 'Cookie đá xay', 'Đá xay bánh quy socola, kem tươi',                  49000, NULL, 1
    UNION ALL SELECT 3, 'Matcha đá xay', 'Bột trà xanh Nhật, sữa tươi, kem tươi',             52000, NULL, 1
    UNION ALL SELECT 4, 'Bánh tiramisu', 'Bánh mềm vị cà phê và cacao',                       42000, NULL, 1
    UNION ALL SELECT 4, 'Bánh croissant','Bánh sừng bò bơ Pháp nướng giòn',                   30000, NULL, 1
) WHERE NOT EXISTS (SELECT 1 FROM products)
  AND (SELECT COUNT(*) FROM categories WHERE id IN (1, 2, 3, 4)) = 4;  -- chỉ thêm khi còn đủ 4 danh mục mẫu

-- ---------- Bàn ----------
INSERT INTO dining_tables (name, area, seats)
SELECT * FROM (
    SELECT 'Bàn 01', 'Trong nhà', 2
    UNION ALL SELECT 'Bàn 02', 'Trong nhà', 4
    UNION ALL SELECT 'Bàn 03', 'Trong nhà', 4
    UNION ALL SELECT 'Bàn 04', 'Sân vườn', 6
    UNION ALL SELECT 'Bàn 05', 'Sân vườn', 4
    UNION ALL SELECT 'Bàn 06', 'Ban công', 2
) WHERE NOT EXISTS (SELECT 1 FROM dining_tables);

-- ---------- Cấu hình mặc định ----------
INSERT OR IGNORE INTO app_settings (setting_key, setting_value) VALUES ('shop.name', 'AICF Coffee');
INSERT OR IGNORE INTO app_settings (setting_key, setting_value) VALUES ('shop.address', '123 Đường Cà Phê, Quận 1, TP.HCM');
INSERT OR IGNORE INTO app_settings (setting_key, setting_value) VALUES ('shop.hours', '7:00 - 22:00 hằng ngày');
