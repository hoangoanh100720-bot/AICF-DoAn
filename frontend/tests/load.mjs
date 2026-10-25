// =============================================================================
// KIỂM THỬ TẢI ĐỒNG THỜI với CSDL SQLite - chỉ cần Node.js 22+ (dùng fetch và node:sqlite có sẵn).
//
// Kịch bản:
//   1. N khách đăng ký CÙNG LÚC.
//   2. Mỗi khách đặt K đơn CÙNG LÚC (N x K yêu cầu gửi một lượt) -> mọi đơn phải 201, tổng tiền đúng, mã không trùng.
//   3. Nhiều người cùng sửa 1 đơn: 30 nhân viên cùng xác nhận (đúng 1 lần thành công, còn lại 409);
//      khách hủy đúng lúc quán xác nhận (chỉ 1 bên thắng).
//   4. Đọc + ghi xen kẽ (khách xem đơn, nhân viên xem bảng đơn, khách khác đặt đơn).
//   5. Mở THẲNG file SQLite đối chiếu: số tài khoản/đơn khớp với API, tổng tiền = tổng các dòng món.
//
// Chạy (backend đang chạy; nên dùng thư mục dữ liệu riêng để không làm bẩn CSDL thật):
//   set APP_DATA_DIR=C:\tam\aicf-load  &&  backend\run.cmd
//   node tests/load.mjs http://localhost:8090 C:\tam\aicf-load\aicf-mobile.db 200 3
// =============================================================================
import { DatabaseSync } from 'node:sqlite';

const BASE = process.argv[2] || 'http://localhost:8090';
const DB = process.argv[3];
const N = Number(process.argv[4] || 200);
const K = Number(process.argv[5] || 3);
const RUN = Date.now().toString(36);

const api = async (path, method = 'GET', body, token) => {
  try {
    const r = await fetch(BASE + path, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) }, body: body ? JSON.stringify(body) : undefined });
    const t = await r.text();
    let data = null; try { data = t ? JSON.parse(t) : null; } catch { data = t; }
    return { status: r.status, data };
  } catch (e) { return { status: 'NETWORK', data: String(e.cause?.code || e.message) }; }
};
const tally = (rs) => rs.reduce((m, r) => (m[r.status] = (m[r.status] || 0) + 1, m), {});
const errSamples = (rs) => [...new Set(rs.filter((r) => r.status >= 300 || r.status === 'NETWORK').map((r) => JSON.stringify(r.data).slice(0, 160)))].slice(0, 5);
const report = [];
const check = (name, cond, detail = '') => { report.push(cond); console.log(`${cond ? 'OK  ' : 'FAIL'} ${name}${detail !== '' ? '  ' + JSON.stringify(detail) : ''}`); };

const products = (await api('/api/products')).data.filter((p) => p.available);
const tables = (await api('/api/tables')).data.filter((t) => t.active);
const staff = (await api('/api/auth/login', 'POST', { username: 'staff', password: 'staff123' })).data.token;
const admin = (await api('/api/auth/login', 'POST', { username: 'admin', password: 'admin123' })).data.token;
console.log(`== ${N} khách x ${K} đơn, ${products.length} món, ${tables.length} bàn ==`);

// ---- 1. N khách đăng ký cùng lúc ----
let t0 = Date.now();
const regs = await Promise.all(Array.from({ length: N }, (_, i) =>
  api('/api/auth/register', 'POST', { username: `lt${RUN}_${i}`, password: 'matkhau123', fullName: `Khách tải ${i}` })));
console.log(`Đăng ký: ${Date.now() - t0} ms`, tally(regs), errSamples(regs));
check(`${N} khách đăng ký đồng thời đều thành công`, regs.every((r) => r.status === 201 || r.status === 200), tally(regs));
const tokens = regs.map((r) => r.data?.token).filter(Boolean);

// ---- 2. Mọi khách đặt K đơn cùng một lúc ----
const expected = []; // tổng tiền mong đợi theo từng request
const reqs = [];
tokens.forEach((tk, i) => { for (let k = 0; k < K; k++) {
  const items = [0, 1, 2].map((j) => ({ productId: products[(i + k + j * 3) % products.length].id, quantity: 1 + ((i + j) % 3) }));
  const total = items.reduce((s, it) => s + products.find((p) => p.id === it.productId).price * it.quantity, 0);
  const dine = (i + k) % 2 === 0;
  reqs.push({ tk, total, body: { orderType: dine ? 'DINE_IN' : 'TAKEAWAY', tableId: dine ? tables[i % tables.length].id : null, note: `tai ${RUN}`, items } });
} });
t0 = Date.now();
const orders = await Promise.all(reqs.map((q) => api('/api/orders', 'POST', q.body, q.tk)));
const ms = Date.now() - t0;
console.log(`Đặt ${reqs.length} đơn đồng thời: ${ms} ms (~${Math.round(reqs.length / (ms / 1000))} đơn/giây)`, tally(orders), errSamples(orders));
const okOrders = orders.filter((r) => r.status === 201);
check(`${reqs.length} đơn đồng thời đều được lưu (201)`, okOrders.length === reqs.length, `${okOrders.length}/${reqs.length}`);
check('Tổng tiền mỗi đơn do server tính đúng', orders.every((r, i) => r.status !== 201 || r.data.totalAmount === reqs[i].total));
check('Mã đơn không trùng', new Set(okOrders.map((r) => r.data.code)).size === okOrders.length);

// ---- 3. Nhiều người cùng thao tác trên CÙNG 1 đơn ----
const target = okOrders[0]?.data;
const C = 30;
const race1 = await Promise.all(Array.from({ length: C }, () => api(`/api/orders/${target.id}/status`, 'PATCH', { status: 'CONFIRMED' }, staff)));
console.log(`${C} nhân viên cùng xác nhận đơn #${target.id}:`, tally(race1), errSamples(race1));
check(`${C} lần xác nhận cùng lúc -> đúng 1 lần thành công, còn lại 409`, race1.filter((r) => r.status === 200).length === 1 && race1.filter((r) => r.status === 409).length === C - 1, tally(race1));

// khách hủy đúng lúc quán xác nhận
const idx2 = orders.findIndex((r, i) => r.status === 201 && r.data.id !== target.id);
const owner = reqs[idx2].tk;
const t2 = orders[idx2].data;
const race2 = await Promise.all([
  ...Array.from({ length: 10 }, () => api(`/api/orders/${t2.id}/cancel`, 'POST', { reason: 'đổi ý' }, owner)),
  ...Array.from({ length: 10 }, () => api(`/api/orders/${t2.id}/status`, 'PATCH', { status: 'CONFIRMED' }, staff)),
]);
const final2 = (await api(`/api/orders/${t2.id}`, 'GET', null, staff)).data;
const cancelOk = race2.slice(0, 10).filter((r) => r.status === 200).length, confirmOk = race2.slice(10).filter((r) => r.status === 200).length;
console.log(`Khách hủy vs quán xác nhận đơn #${t2.id}: hủy OK=${cancelOk}, xác nhận OK=${confirmOk}, cuối cùng=${final2.status}`, errSamples(race2));
check('Hủy/xác nhận cùng lúc -> chỉ 1 bên thắng', cancelOk + confirmOk === 1 && race2.every((r) => r.status === 200 || r.status === 409), { cancelOk, confirmOk, final: final2.status });

// nhiều khách xem đơn + staff đọc bảng đơn trong lúc ghi
const mixed = await Promise.all([
  ...tokens.slice(0, 100).map((tk) => api('/api/orders/my', 'GET', null, tk)),
  ...Array.from({ length: 20 }, () => api('/api/orders/active', 'GET', null, staff)),
  ...tokens.slice(100, 150).map((tk, i) => api('/api/orders', 'POST', { orderType: 'TAKEAWAY', items: [{ productId: products[i % products.length].id, quantity: 1 }] }, tk)),
]);
console.log('Đọc + ghi lẫn lộn:', tally(mixed), errSamples(mixed));
check('Đọc và ghi xen kẽ không lỗi', mixed.every((r) => r.status === 200 || r.status === 201), tally(mixed));
const extra = mixed.slice(120).filter((r) => r.status === 201).length;

// ---- 4. Đối chiếu trực tiếp file SQLite ----
const db = new DatabaseSync(DB, { readOnly: true });
const users = db.prepare(`SELECT COUNT(*) c FROM users WHERE username LIKE ?`).get(`lt${RUN}_%`).c;
const dbOrders = db.prepare(`SELECT o.id, o.code, o.total_amount t, o.status, (SELECT COALESCE(SUM(unit_price*quantity),0) FROM order_items i WHERE i.order_id=o.id) s, (SELECT COUNT(*) FROM order_items i WHERE i.order_id=o.id) n
  FROM orders o JOIN users u ON u.id=o.user_id WHERE u.username LIKE ?`).all(`lt${RUN}_%`);
console.log(`SQLite: ${users} user, ${dbOrders.length} đơn của đợt test`);
check('Số tài khoản trong SQLite = số đăng ký thành công', users === tokens.length, { users, tokens: tokens.length });
check('Số đơn trong SQLite = số đơn API báo thành công', dbOrders.length === okOrders.length + extra, { db: dbOrders.length, api: okOrders.length + extra });
check('Mọi đơn có đủ dòng món, tổng tiền khớp tổng các dòng', dbOrders.every((o) => o.n > 0 && o.t === o.s));
check('Không còn mã đơn tạm TMP-', dbOrders.every((o) => !o.code.startsWith('TMP-')));
const st = db.prepare('SELECT status FROM orders WHERE id=?');
check('Trạng thái đơn bị tranh chấp lưu đúng trong SQLite', st.get(target.id).status === 'CONFIRMED' && st.get(t2.id).status === final2.status, { a: st.get(target.id).status, b: st.get(t2.id).status });
const notif = db.prepare(`SELECT COUNT(*) c FROM notifications WHERE title LIKE 'Đơn mới%'`).get().c;
console.log(`Thông báo "Đơn mới" trong DB: ${notif}`);
db.close();

console.log(`\n${report.filter(Boolean).length}/${report.length} kiểm tra đạt`);
