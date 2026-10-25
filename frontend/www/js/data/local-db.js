/**
 * CƠ SỞ DỮ LIỆU CỤC BỘ TRÊN THIẾT BỊ (offline storage).
 *
 * - App Android (Capacitor): dùng SQLITE thật qua plugin @capacitor-community/sqlite, file "aicf_localSQLite.db"
 *   trong bộ nhớ riêng của app.
 * - PWA trên trình duyệt: trình duyệt không có SQLite -> dùng localStorage với CÙNG giao diện hàm.
 *
 * Áp dụng mẫu <Adapter / Repository>: phần còn lại của app chỉ gọi các hàm của LocalDb
 * (kvGet, cartList, cacheProducts...) mà không cần biết bên dưới là SQLite hay localStorage.
 * Nếu SQLite lỗi (thiếu plugin...) app tự chuyển sang localStorage, không bị dừng.
 *
 * Dữ liệu lưu cục bộ:
 *   kv              : cặp khóa - giá trị (token đăng nhập, người dùng, địa chỉ máy chủ...)
 *   cart_items      : phiếu gọi món đang chọn dở (vẫn còn khi tắt app / mất mạng)
 *   cached_products : bản sao thực đơn để vẫn chọn món được khi mạng chập chờn
 */

const DB_NAME = 'aicf_local';

/** LƯỢC ĐỒ SQLITE TRÊN THIẾT BỊ (tạo nếu chưa có). */
const SCHEMA = `
  -- Khóa / giá trị dùng chung: 'session.token', 'session.user', 'server_url'...
  CREATE TABLE IF NOT EXISTS kv (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL              -- lưu dạng JSON
  );
  -- Phiếu gọi món nháp: mỗi món 1 dòng, cộng dồn số lượng khi chạm lại cùng món
  CREATE TABLE IF NOT EXISTS cart_items (
    product_id INTEGER PRIMARY KEY,  -- id món bên máy chủ
    name       TEXT    NOT NULL,     -- tên món (hiển thị khi mất mạng)
    price      INTEGER NOT NULL,     -- giá lúc chọn (máy chủ tính lại khi gửi đơn)
    image_url  TEXT,
    quantity   INTEGER NOT NULL CHECK (quantity > 0),
    note       TEXT                  -- ghi chú: ít đá, không đường...
  );
  -- Thực đơn lưu tạm để mở app khi mất mạng
  CREATE TABLE IF NOT EXISTS cached_products (
    id        INTEGER PRIMARY KEY,
    json      TEXT NOT NULL,         -- nguyên đối tượng món dạng JSON
    cached_at TEXT NOT NULL          -- thời điểm lưu
  );
`;

/* ============================== SQLite (app Android) ============================== */

class SqliteLocalDb {
  constructor(plugin) {
    this.s = plugin;
    this.kind = 'SQLite';
  }

  async init() {
    try {
      await this.s.createConnection({ database: DB_NAME, version: 1, encrypted: false, mode: 'no-encryption', readonly: false });
    } catch { /* kết nối đã tồn tại (vd. mở lại app) */ }
    try {
      await this.s.open({ database: DB_NAME, readonly: false });
    } catch { /* đã mở */ }
    await this.s.execute({ database: DB_NAME, statements: SCHEMA, transaction: true, readonly: false });
  }

  /** Câu lệnh thay đổi dữ liệu (INSERT/UPDATE/DELETE), dùng tham số ? để tránh SQL injection. */
  run(statement, values = []) {
    return this.s.run({ database: DB_NAME, statement, values, transaction: true, readonly: false });
  }

  /** Câu lệnh SELECT, trả mảng dòng. */
  async query(statement, values = []) {
    const res = await this.s.query({ database: DB_NAME, statement, values, readonly: false });
    // iOS trả thêm 1 dòng đầu chứa tên cột (ios_columns) -> bỏ đi
    return (res.values || []).filter((r) => !('ios_columns' in r));
  }

  async kvGet(key) {
    const rows = await this.query('SELECT value FROM kv WHERE key = ?', [key]);
    return rows.length ? JSON.parse(rows[0].value) : null;
  }
  kvSet(key, value) {
    return this.run('INSERT OR REPLACE INTO kv (key, value) VALUES (?, ?)', [key, JSON.stringify(value)]);
  }
  kvRemove(key) {
    return this.run('DELETE FROM kv WHERE key = ?', [key]);
  }

  async cartList() {
    const rows = await this.query('SELECT product_id, name, price, image_url, quantity, note FROM cart_items ORDER BY rowid');
    return rows.map((r) => ({ productId: r.product_id, name: r.name, price: r.price, imageUrl: r.image_url, quantity: r.quantity, note: r.note }));
  }
  cartPut(item) {
    return this.run(
      'INSERT OR REPLACE INTO cart_items (product_id, name, price, image_url, quantity, note) VALUES (?, ?, ?, ?, ?, ?)',
      [item.productId, item.name, item.price, item.imageUrl ?? null, item.quantity, item.note ?? null]);
  }
  cartRemove(productId) {
    return this.run('DELETE FROM cart_items WHERE product_id = ?', [productId]);
  }
  cartClear() {
    return this.run('DELETE FROM cart_items');
  }

  async cacheProducts(products) {
    const now = new Date().toISOString();
    await this.run('DELETE FROM cached_products');
    for (const p of products) {
      await this.run('INSERT INTO cached_products (id, json, cached_at) VALUES (?, ?, ?)', [p.id, JSON.stringify(p), now]);
    }
  }
  async cachedProducts() {
    const rows = await this.query('SELECT json FROM cached_products ORDER BY id');
    return rows.map((r) => JSON.parse(r.json));
  }
}

/* ============================== localStorage (PWA trên trình duyệt) ============================== */

class WebLocalDb {
  constructor() {
    this.kind = 'localStorage';
    this.prefix = DB_NAME + '.';
  }

  async init() { /* không cần tạo bảng */ }

  read(name, fallback) {
    try {
      const raw = localStorage.getItem(this.prefix + name);
      return raw === null ? fallback : JSON.parse(raw);
    } catch {
      return fallback; // chế độ ẩn danh / bị chặn lưu trữ
    }
  }
  write(name, value) {
    try {
      localStorage.setItem(this.prefix + name, JSON.stringify(value));
    } catch { /* hết dung lượng / bị chặn: bỏ qua, app vẫn chạy */ }
  }

  async kvGet(key) { return this.read('kv.' + key, null); }
  async kvSet(key, value) { this.write('kv.' + key, value); }
  async kvRemove(key) {
    try { localStorage.removeItem(this.prefix + 'kv.' + key); } catch { /* bỏ qua */ }
  }

  async cartList() { return this.read('cart_items', []); }
  async cartPut(item) {
    const list = this.read('cart_items', []);
    const i = list.findIndex((x) => x.productId === item.productId);
    if (i >= 0) list[i] = item; else list.push(item);
    this.write('cart_items', list);
  }
  async cartRemove(productId) {
    this.write('cart_items', this.read('cart_items', []).filter((x) => x.productId !== productId));
  }
  async cartClear() { this.write('cart_items', []); }

  async cacheProducts(products) { this.write('cached_products', products); }
  async cachedProducts() { return this.read('cached_products', []); }
}

/* ============================== Chọn cách lưu ============================== */

/** Đối tượng LocalDb dùng trong toàn app; gọi await initLocalDb() 1 lần lúc khởi động. */
export let localDb = new WebLocalDb();

export async function initLocalDb() {
  const plugin = window.Capacitor?.Plugins?.CapacitorSQLite;
  if (plugin && window.Capacitor.isNativePlatform?.()) {
    const sqlite = new SqliteLocalDb(plugin);
    try {
      await sqlite.init();
      localDb = sqlite;
    } catch (e) {
      console.warn('Không mở được SQLite, dùng localStorage:', e);
    }
  }
  await localDb.init();
  console.info('LocalDb:', localDb.kind);
  return localDb;
}
