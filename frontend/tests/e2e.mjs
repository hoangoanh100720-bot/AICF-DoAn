// =============================================================================
// KIỂM THỬ ĐẦU-CUỐI (end-to-end) GIAO DIỆN APP bằng Chrome chạy ẩn (headless), điều khiển qua
// Chrome DevTools Protocol - không cần cài thêm thư viện, chỉ cần Node.js 22+ và Google Chrome.
//
// Kịch bản bán hàng 1 ca: nhân viên đăng nhập -> chạm bàn trống, chọn món (phiếu nháp còn sau khi đóng)
// -> gửi pha chế -> màn Pha chế: Bắt đầu / Xong -> mang ra -> thu tiền mặt, tính tiền thối -> bàn trống lại
// -> đơn mang về rồi hủy -> Trợ lý AI -> quản lý xem Tổng quan, sửa thực đơn / bàn, tạo tài khoản nhân viên
// -> chủ quán mở Cài đặt.
// Mỗi bước chụp ảnh màn hình vào thư mục kết quả để đưa vào báo cáo.
//
// Chạy (backend đang chạy với dữ liệu mẫu):
//   node tests/e2e.mjs [http://localhost:8090] [thu-muc-anh]
// =============================================================================
import { spawn } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const BASE = process.argv[2] || 'http://localhost:8090';
const OUT = process.argv[3] || 'tests/ket-qua';
const CHROME = process.env.CHROME || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
mkdirSync(OUT, { recursive: true });

// ---------- Mở Chrome ẩn + kết nối DevTools ----------
const port = 9400 + Math.floor(Math.random() * 50);
const chrome = spawn(CHROME, ['--headless=new', `--remote-debugging-port=${port}`, `--user-data-dir=${join(OUT, 'profile')}`,
  '--window-size=412,915', 'about:blank'], { stdio: 'ignore' });
process.on('exit', () => chrome.kill());
await sleep(2500);
const page = (await (await fetch(`http://127.0.0.1:${port}/json`)).json()).find((t) => t.type === 'page');
const ws = new WebSocket(page.webSocketDebuggerUrl);
await new Promise((r) => ws.addEventListener('open', r));
let seq = 0;
const pending = new Map();
const jsErrors = [];
ws.addEventListener('message', (e) => {
  const m = JSON.parse(e.data);
  if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); }
  if (m.method === 'Runtime.exceptionThrown') jsErrors.push(m.params.exceptionDetails.exception?.description || m.params.exceptionDetails.text);
});
const cdp = (method, params = {}) => new Promise((r) => { const id = ++seq; pending.set(id, r); ws.send(JSON.stringify({ id, method, params })); });
await cdp('Runtime.enable');
await cdp('Page.enable');
await cdp('Emulation.setDeviceMetricsOverride', { width: 412, height: 915, deviceScaleFactor: 1, mobile: true });

/** Chạy JS trong trang, trả kết quả. */
const ev = async (expr) => {
  const r = await cdp('Runtime.evaluate', { expression: expr, awaitPromise: true, returnByValue: true });
  if (r.result?.exceptionDetails) throw new Error(r.result.exceptionDetails.exception?.description || r.result.exceptionDetails.text);
  return r.result?.result?.value;
};
let shotNo = 0;
const shot = async (name) => {
  const r = await cdp('Page.captureScreenshot', { format: 'png' });
  writeFileSync(join(OUT, `${String(++shotNo).padStart(2, '0')}-${name}.png`), Buffer.from(r.result.data, 'base64'));
};
const results = [];
const ok = (name, cond, detail = '') => results.push({ name, ok: !!cond, detail: String(detail ?? '') });
const go = async (hash, wait = 1500) => { await ev(`location.hash = ${JSON.stringify(hash)}; true`); await sleep(wait); };
const text = () => ev('document.body.innerText');
const count = (sel) => ev(`document.querySelectorAll(${JSON.stringify(sel)}).length`);
const click = (sel) => ev(`(() => { const b = document.querySelector(${JSON.stringify(sel)}); if (b) b.click(); return !!b; })()`);
/** Bấm phần tử khớp selector có chứa chữ t (lấy cái CUỐI CÙNG = hộp thoại trên cùng). */
const clickText = (sel, t) => ev(`(() => { const b = [...document.querySelectorAll(${JSON.stringify(sel)})].filter((x) => x.textContent.includes(${JSON.stringify(t)})).pop(); if (b) b.click(); return !!b; })()`);
const fill = (sel, v) => ev(`(() => { const i = [...document.querySelectorAll(${JSON.stringify(sel)})].pop(); i.value = ${JSON.stringify(v)}; i.dispatchEvent(new Event('input', {bubbles:true})); i.dispatchEvent(new Event('change', {bubbles:true})); return true; })()`);
const topSheet = () => ev(`[...document.querySelectorAll('.sheet')].pop()?.innerText || ''`);
const closeSheets = async () => { await ev(`[...document.querySelectorAll('.sheet .sheet-head .icon-btn')].reverse().forEach((b) => b.click()); true`); await sleep(600); };
const api = async (path, method = 'GET', body, token) => {
  const r = await fetch(BASE + path, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) }, body: body ? JSON.stringify(body) : undefined });
  const t = await r.text();
  return { status: r.status, data: t ? JSON.parse(t) : null };
};
/** Đăng nhập bằng giao diện. */
const uiLogin = async (user, pass) => {
  await go('/login', 1200);
  await fill('input[name=username]', user);
  await fill('input[name=password]', pass);
  await ev(`document.querySelector('.st-login form button[type=submit]').click(); true`);
  await sleep(2000);
};
/** Đăng xuất: Thêm / Tôi -> Đăng xuất -> xác nhận. */
const uiLogout = async () => {
  await go('/more', 1200);
  await click('[data-act=logout]');
  await sleep(600);
  await click('.sheet [data-act=confirm]');
  await sleep(1500);
  ok('Đăng xuất -> về màn Đăng nhập', (await ev('location.hash')) === '#/login');
};
/** Chạm vào món trong màn chọn món. */
const tapProduct = (name) => click(`.sheet .st-prod[data-product="${name}"]`);

// Bàn dùng cho kịch bản: chọn 1 bàn đang trống (chạy test nhiều lần vẫn đúng)
const staffToken = (await api('/api/auth/login', 'POST', { username: 'nhanvien', password: 'nhanvien123' })).data.token;
const openNow = (await api('/api/orders/active', 'GET', null, staffToken)).data;
const tables = (await api('/api/tables', 'GET', null, staffToken)).data.filter((t) => t.active);
const TABLE = tables.find((t) => !openNow.some((o) => o.tableId === t.id)).name;

// =============================== KỊCH BẢN ===============================
// --- Đăng nhập ---
await cdp('Page.navigate', { url: BASE + '/' });
await sleep(2500);
ok('Mở app chưa đăng nhập -> màn Đăng nhập', (await ev('location.hash')) === '#/login');
ok('Màn đăng nhập không có thanh điều hướng, có tài khoản mẫu', await ev(`document.getElementById('tabbar').hidden && !!document.querySelector('.st-demo .st-chip')`));
await shot('dang-nhap');
await fill('input[name=username]', 'nhanvien');
await fill('input[name=password]', 'sai-mat-khau');
await ev(`document.querySelector('.st-login form button[type=submit]').click(); true`);
await sleep(1200);
ok('Sai mật khẩu -> báo lỗi', (await text()).includes('Sai tên đăng nhập hoặc mật khẩu'));

await uiLogin('nhanvien', 'nhanvien123');
ok('Nhân viên đăng nhập -> Sơ đồ bàn', (await ev('location.hash')) === '#/tables');
ok('Thanh dưới của nhân viên: Bàn, Pha chế, Đơn hàng, Trợ lý AI, Tôi (không có Tổng quan)',
  (await ev(`[...document.querySelectorAll('.st-nav-btn')].map((b) => b.dataset.tab).join(',')`)) === 'tables,kitchen,history,chat,more');
ok('Sơ đồ bàn nhóm theo khu vực', (await count('.st-area')) >= 2 && (await count('.st-table')) >= 6);
await shot('so-do-ban');
ok('Màn trên thanh dưới (Bàn) không có nút quay lại', (await count('[data-act=back]')) === 0);
await click('.st-header .st-bell');
await sleep(1200);
ok('Mở Thông báo từ chuông -> có nút quay lại', (await ev('location.hash')) === '#/notifications' && (await count('[data-act=back]')) === 1);
await click('[data-act=back]');
await sleep(1200);
ok('Bấm quay lại -> về đúng Sơ đồ bàn', (await ev('location.hash')) === '#/tables');

// --- Chạm bàn trống -> chọn món; phiếu nháp còn sau khi đóng (LocalDb) ---
await click(`.st-table[data-table="${TABLE}"]`);
await sleep(1500);
ok('Chạm bàn trống -> mở thẳng màn chọn món', (await count('.sheet .st-prod')) >= 10);
await fill('#pick-search', 'latte');
await sleep(300);
ok('Tìm "latte" lọc đúng 1 món', (await ev(`[...document.querySelectorAll('.sheet .st-prod')].filter((p) => p.offsetParent).length`)) === 1);
await fill('#pick-search', '');
await sleep(300);
await tapProduct('Cà phê sữa đá'); await sleep(300);
await tapProduct('Cà phê sữa đá'); await sleep(300);
await tapProduct('Latte'); await sleep(500);
ok('Chạm món -> góc ảnh hiện ×2, nút giỏ "3 món"', (await ev(`document.querySelector('.st-prod[data-product="Cà phê sữa đá"] .st-badge').textContent`)) === '×2'
  && (await ev(`document.querySelector('[data-act=view-cart]').textContent`)).includes('3 món'));
await shot('chon-mon');
await closeSheets();
ok('Đóng mà chưa gửi -> về sơ đồ bàn, bàn vẫn trống', (await count('.sheet')) === 0);
await click(`.st-table[data-table="${TABLE}"]`);
await sleep(1500);
ok('Mở lại cùng bàn -> phiếu nháp vẫn còn 3 món (lưu trong LocalDb)', (await ev(`document.querySelector('[data-act=view-cart]').textContent`)).includes('3 món'));

await click('[data-act=view-cart]');
await sleep(600);
await fill('.sheet .st-cart-line[data-line="Latte"] .st-note-input', 'ít ngọt');
await shot('mon-da-chon');
await click('[data-act=send]');
await sleep(2000);
const bill = await topSheet();
ok('Gửi pha chế -> bill: tổng 103.000đ (2×29.000 + 45.000), trạng thái Chờ pha', bill.includes('103.000đ') && bill.includes('Chờ pha'), bill.slice(0, 120));
ok('Ghi chú món hiện trên bill', bill.includes('ít ngọt'));
await shot('bill-ban');
await closeSheets();
await sleep(800);
ok('Bàn chuyển sang "có khách" và hiện tổng tiền', await ev(`(() => { const t = document.querySelector('.st-table[data-table="${TABLE}"]'); return t.classList.contains('busy') && t.textContent.includes('103.000đ'); })()`));

// --- Pha chế ---
await go('/kitchen', 1800);
ok('Pha chế: bill mới nằm ở cột Chờ pha', (await text()).includes(TABLE + ' · 3 ly'));
await shot('pha-che');
const code = await ev(`[...document.querySelectorAll('.st-card')].find((c) => c.textContent.includes(${JSON.stringify(TABLE)})).dataset.order`);
await click(`.st-card[data-order="${code}"] [data-act=start]`);
await sleep(1500);
await click('.st-seg-btn[data-tab=PREPARING]');
await sleep(300);
await click(`.st-card[data-order="${code}"] [data-act=done]`);
await sleep(1500);
const ready = (await api('/api/orders/active', 'GET', null, staffToken)).data.find((o) => o.code === code);
ok('Bắt đầu -> Xong: bill sang "Đã xong, chờ mang ra"', ready?.status === 'READY', ready?.status);

// --- Mang ra + thu tiền mặt ---
await go('/tables', 1800);
ok('Sơ đồ bàn báo "Đồ đã xong · mang ra"', await ev(`document.querySelector('.st-table[data-table="${TABLE}"]').classList.contains('pending')`));
await click(`.st-table[data-table="${TABLE}"]`);
await sleep(1500);
await click('.sheet [data-act=next]');
await sleep(1500);
ok('Bấm "Đã mang ra" -> Đã phục vụ', (await topSheet()).includes('Đã phục vụ'));
await click('.sheet [data-act=pay]');
await sleep(800);
await clickText('.sheet .st-chip', '200.000');
await sleep(300);
ok('Thanh toán: chọn 200.000 -> hiện tiền thối 97.000đ', (await topSheet()).includes('Tiền thối: 97.000đ'));
await shot('thanh-toan');
await click('[data-act=do-pay]');
await sleep(1500);
ok('Thu tiền xong -> màn "Đã thu tiền", bill hoàn tất', (await topSheet()).includes('Bill đã hoàn tất'));
await shot('da-thu-tien');
await click('[data-act=pay-done]');
await sleep(500);
await closeSheets();
await sleep(1000);
ok('Bàn trống trở lại', await ev(`document.querySelector('.st-table[data-table="${TABLE}"]').textContent.includes('Trống')`));

// --- Đơn mang về rồi hủy ---
await click('[data-act=takeaway]');
await sleep(1500);
await tapProduct('Bạc xỉu'); await sleep(300);
await click('[data-act=view-cart]'); await sleep(500);
await click('[data-act=send]'); await sleep(2000);
ok('Đơn mang về -> bill mới', (await topSheet()).includes('Bạc xỉu'));
await click('.sheet [data-act=cancel]');
await sleep(500);
await click('.sheet [data-act=confirm]');
await sleep(1500);
ok('Nhân viên hủy được đơn chưa pha', (await topSheet()).includes('Đã hủy'));
await closeSheets();

// --- Lịch sử đơn ---
await go('/history', 1800);
ok('Đơn hàng hôm nay có bill vừa thu tiền', await ev(`!!document.querySelector('.st-card[data-order="${code}"]')`));
await shot('don-hang');

// --- Trợ lý AI (chế độ cơ bản khi chưa có khóa API) ---
await go('/chat', 2000);
await fill('.as-input', 'Còn bàn trống không?');
await click('.as-send');
await sleep(2500);
ok('Trợ lý trả lời về bàn trống', (await ev(`[...document.querySelectorAll('.as-msg.bot')].pop().textContent`)).includes('bàn trống'));
await shot('tro-ly-ai');

// Nhân viên không vào được màn của quản lý
await go('/dashboard', 1500);
ok('Nhân viên bị chặn khỏi Tổng quan', (await ev('location.hash')) === '#/tables');
ok('Nhân viên gọi API thống kê bị từ chối 403', (await api('/api/stats/dashboard', 'GET', null, staffToken)).status === 403);

// --- QUẢN LÝ ---
await uiLogout();
await uiLogin('quanly', 'quanly123');
ok('Quản lý đăng nhập -> Tổng quan', (await ev('location.hash')) === '#/dashboard');
await sleep(1000);
const dash = await text();
ok('Tổng quan có doanh thu (tách tiền mặt) và món bán chạy', dash.includes('Doanh thu hôm nay') && dash.includes('Tiền mặt') && dash.includes('Cà phê sữa đá'));
await shot('tong-quan');
ok('Chuông thông báo có số chưa đọc (đơn mới từ nhân viên)', !!(await ev(`document.querySelector('.st-bell .st-badge')?.textContent`)));

await go('/manage', 2000);
ok('Thực đơn & bàn: danh sách món có ảnh', (await count('.crud-item')) >= 10);
await shot('quan-ly-mon');
await click('.st-seg-btn[data-tab=tables]');
await sleep(1500);
await click('[data-act=add]');
await sleep(600);
await fill('.sheet input[name=name]', 'Bàn E2E');
await fill('.sheet input[name=area]', 'Sân thượng');
await fill('.sheet input[name=seats]', '3');
await shot('them-ban');
await ev(`document.querySelector('.sheet form').requestSubmit(); true`);
await sleep(1500);
ok('Thêm bàn mới qua form CRUD tổng quát', (await text()).includes('Bàn E2E'));
await click('.crud-item[data-row="Bàn E2E"]');
await sleep(600);
await click('.sheet [data-act=delete]');
await sleep(500);
await click('.sheet [data-act=confirm]');
await sleep(1500);
ok('Xóa bàn (có hộp xác nhận)', !(await text()).includes('Bàn E2E'));

await go('/staff', 1800);
const newUser = 'nv' + Date.now().toString().slice(-6);
await click('[data-act=add-user]');
await sleep(500);
await fill('.sheet input[name=fullName]', 'Nhân viên E2E');
await fill('.sheet input[name=username]', newUser);
await fill('.sheet input[name=password]', 'matkhau1');
ok('Quản lý chỉ được chọn vai trò Nhân viên bán hàng', (await ev(`[...document.querySelectorAll('.sheet select[name=role] option')].map((o) => o.value).join(',')`)) === 'STAFF');
await ev(`document.querySelector('.sheet form').requestSubmit(); true`);
await sleep(1500);
ok('Tạo tài khoản nhân viên mới', await ev(`!!document.querySelector('.st-card[data-user="${newUser}"]')`));
ok('Tài khoản mới đăng nhập được', (await api('/api/auth/login', 'POST', { username: newUser, password: 'matkhau1' })).status === 200);
await shot('nhan-vien');

await go('/settings', 1500);
ok('Quản lý không vào được Cài đặt (chỉ chủ quán)', (await ev('location.hash')) === '#/dashboard');

// --- CHỦ QUÁN ---
await uiLogout();
await uiLogin('chu', 'chu123');
await go('/more', 1200);
const moreItems = await ev(`[...document.querySelectorAll('.st-menu-item[data-menu]')].map((b) => b.dataset.menu)`);
ok('Chủ quán: mục Thêm có Trợ lý AI, Thực đơn & bàn, Nhân viên, Cài đặt',
  ['Trợ lý AI', 'Thực đơn & bàn', 'Nhân viên', 'Cài đặt quán & AI'].every((t) => moreItems.includes(t)), moreItems.join(', '));
ok('Không còn chữ "null" / "undefined" lọt ra màn hình', !/\bnull\b|\bundefined\b/.test(await text()));
await shot('them-chu-quan');
await click('.st-menu-item[data-menu="Cài đặt quán & AI"]');
await sleep(2000);
ok('Mở Cài đặt từ mục Thêm -> có nút quay lại', (await ev('location.hash')) === '#/settings' && (await count('[data-act=back]')) === 1);
await shot('cai-dat-nut-quay-lai');
await click('[data-act=back]');
await sleep(1200);
ok('Bấm quay lại -> về mục Thêm', (await ev('location.hash')) === '#/more');
await click('.st-menu-item[data-menu="Nhân viên"]');
await sleep(1500);
ok('Nhân viên (mở từ Thêm) cũng có nút quay lại', (await count('[data-act=back]')) === 1);
await go('/settings', 2000);
ok('Trang cài đặt quán & AI', (await text()).includes('Trợ lý AI') && (await text()).includes('Thông tin quán'));
await shot('cai-dat');

// =============================== KẾT QUẢ ===============================
console.log('== Kiểm thử giao diện AICF Coffee · app bán hàng ==');
for (const r of results) console.log((r.ok ? 'OK   ' : 'FAIL ') + r.name + (r.detail ? `  [${r.detail}]` : ''));
console.log(jsErrors.length ? 'LỖI JS:\n' + jsErrors.join('\n') : 'Không có lỗi JS');
const failed = results.filter((r) => !r.ok).length + jsErrors.length;
console.log(`${results.length - results.filter((r) => !r.ok).length}/${results.length} bước đạt. Ảnh: ${OUT}`);
ws.close();
process.exit(failed ? 1 : 0);
