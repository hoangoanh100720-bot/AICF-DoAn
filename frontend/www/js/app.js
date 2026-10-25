/**
 * ĐIỂM KHỞI ĐỘNG CỦA APP (app bán hàng nội bộ: chủ quán, quản lý, nhân viên bán hàng).
 *
 * Thứ tự: mở LocalDb (SQLite / localStorage) -> nạp phiên đăng nhập -> dựng khung app (thanh trên + thanh dưới
 * theo vai trò, giống app nhân viên AICF) + router -> đăng ký service worker (PWA) -> định kỳ đếm thông báo.
 *
 * Cấu trúc thư mục js/:
 *   core/      hạ tầng dùng chung, không biết gì về nghiệp vụ (HTTP, router, store, DOM, định dạng)
 *   data/      truy cập dữ liệu: REST repository, phiên đăng nhập, phiếu gọi món nháp, CSDL cục bộ
 *   firebase/  đăng nhập Google + thông báo đẩy
 *   ui/        component giao diện tái sử dụng + trang CRUD tổng quát
 *   pages/     từng màn hình (mỗi file export hàm render) + các hộp thoại bill / chọn món / thanh toán
 */
import { IS_NATIVE, UNREAD_POLL_MS } from './config.js';
import { h, mount } from './core/dom.js';
import { initials, ROLE_LABEL } from './core/format.js';
import { Router } from './core/router.js';
import { appState } from './core/store.js';
import { initLocalDb } from './data/local-db.js';
import { notificationRepo, settingsRepo } from './data/repositories.js';
import { session } from './data/session.js';
import { closeAllSheets, closeTopSheet, errorState, setBackAction } from './ui/components.js';
import { icon } from './ui/icons.js';

/* ------------------------------ Các màn hình ------------------------------ */

const ALL = ['OWNER', 'MANAGER', 'STAFF'];
const MANAGERS = ['OWNER', 'MANAGER'];

/**
 * Danh sách màn hình theo THỨ TỰ trên thanh điều hướng. Mỗi vai trò thấy các màn mình được phép;
 * 4 màn đầu nằm trên thanh, phần còn lại vào mục "Thêm" (cùng cách làm của app nhân viên AICF).
 * nav: false = không có nút riêng (mở từ màn khác).
 */
const VIEWS = [
  { id: 'dashboard', path: '/dashboard', title: 'Tổng quan', icon: 'home', roles: MANAGERS, load: () => import('./pages/dashboard.js') },
  { id: 'tables', path: '/tables', title: 'Bàn', icon: 'grid', roles: ALL, load: () => import('./pages/tables.js') },
  { id: 'kitchen', path: '/kitchen', title: 'Pha chế', icon: 'coffee', roles: ALL, load: () => import('./pages/kitchen.js') },
  { id: 'history', path: '/history', title: 'Đơn hàng', icon: 'clipboard', roles: ALL, load: () => import('./pages/history.js') },
  { id: 'chat', path: '/chat', title: 'Trợ lý AI', icon: 'chat', roles: ALL, load: () => import('./pages/chat.js') },
  { id: 'manage', path: '/manage', title: 'Thực đơn & bàn', icon: 'book', roles: MANAGERS, load: () => import('./pages/manage.js') },
  { id: 'staff', path: '/staff', title: 'Nhân viên', icon: 'users', roles: MANAGERS, load: () => import('./pages/staff.js') },
  { id: 'settings', path: '/settings', title: 'Cài đặt quán & AI', icon: 'settings', roles: ['OWNER'], load: () => import('./pages/settings.js') },
  { id: 'notifications', path: '/notifications', title: 'Thông báo', icon: 'bell', roles: ALL, nav: false, load: () => import('./pages/notifications.js') },
  { id: 'more', path: '/more', title: 'Thêm', icon: 'more', roles: ALL, nav: false, load: () => import('./pages/more.js') },
];

const routes = [
  { path: '/login', load: () => import('./pages/login.js'), guestOnly: true, bare: true },
  ...VIEWS.map((v) => ({ path: v.path, load: v.load, auth: true, roles: v.roles, view: v.id })),
];

const allowed = (user) => VIEWS.filter((v) => user && v.roles.includes(user.role));
const navViews = (user) => allowed(user).filter((v) => v.nav !== false);

/** Màn hình đầu tiên theo vai trò: chủ quán / quản lý xem Tổng quan, nhân viên vào thẳng sơ đồ Bàn. */
const homeFor = (user) => (!user ? '/login' : navViews(user)[0].path);

/** Các màn nằm trên thanh dưới: tối đa 5 nút; nhiều hơn thì 4 nút + "Thêm". */
const barViews = (user) => { const all = navViews(user); return all.slice(0, all.length > 5 ? 4 : 5); };

/** Các màn không nằm trên thanh dưới -> mục "Thêm" (dùng ở pages/more.js). */
export const moreViews = (user) => navViews(user).slice(barViews(user).length);

/**
 * Nút "Quay lại" cho màn KHÔNG nằm trên thanh dưới (mở từ mục Thêm, chuông thông báo...).
 * Đã đi qua màn khác trong app thì lùi lại đúng màn trước; mở thẳng bằng địa chỉ (vd từ thông báo đẩy)
 * thì về mục "Thêm" (màn thuộc Thêm) hoặc trang đầu theo vai trò.
 */
let moves = 0;
function backFor(route, user) {
  if (!user || route.bare || route.view === 'more' || barViews(user).some((v) => v.id === route.view)) return null;
  const fallback = moreViews(user).some((v) => v.id === route.view) ? '/more' : homeFor(user);
  const canGoBack = moves > 1;
  return () => { if (canGoBack) history.back(); else location.hash = fallback; };
}

/* ------------------------------ Khung app ------------------------------ */

const header = document.getElementById('header');
const tabbar = document.getElementById('tabbar');
const main = document.getElementById('view');
let current = { view: null, bare: true };

function renderHeader() {
  const { user, shop, unread } = appState.get();
  if (!user) return;
  const open = shop?.openNow;
  mount(header,
    h('span', { class: 'logo sm' }, icon('coffee', 16)),
    h('strong', { class: 'st-shop' }, shop?.name || 'AICF Coffee'),
    open == null ? h('span', { class: 'st-pill' }, ROLE_LABEL[user.role])
      : h('span', { class: 'st-pill ' + (open ? 'ok' : 'warn') }, open ? '● Đang mở cửa' : '○ Ngoài giờ mở cửa'),
    h('a', { class: 'st-bell', href: '#/notifications', 'aria-label': 'Thông báo' + (unread ? ` (${unread} chưa đọc)` : '') },
      icon('bell', 22), unread > 0 ? h('span', { class: 'st-badge' }, unread > 9 ? '9+' : unread) : null),
    h('a', { class: 'st-avatar', href: '#/more', 'aria-label': 'Tài khoản' }, initials(user.fullName)));
}

function renderTabs() {
  const user = appState.get().user;
  if (!user) return;
  const onBar = barViews(user);
  const hasMore = navViews(user).length > onBar.length;
  const activeId = onBar.some((v) => v.id === current.view) ? current.view : 'more';
  const btn = (id, path, title, iconName) => h('a', {
    class: 'st-nav-btn' + (id === activeId ? ' active' : ''), href: '#' + path, 'data-tab': id,
    'aria-current': id === activeId ? 'page' : null,
  }, icon(iconName, 22), h('span', null, title));
  mount(tabbar, onBar.map((v) => btn(v.id, v.path, v.title, v.icon)),
    btn('more', '/more', hasMore ? 'Thêm' : 'Tôi', hasMore ? 'more' : 'user'));
}

function renderShell() {
  const show = !current.bare && !!appState.get().user;
  header.hidden = !show;
  tabbar.hidden = !show;
  if (show) {
    renderHeader();
    renderTabs();
  }
}

/* ------------------------------ Khởi động ------------------------------ */

const router = new Router({
  outlet: main,
  routes,
  getUser: () => appState.get().user,
  homeFor,
  onChange: (route) => {
    closeAllSheets(); // đổi màn hình: đóng hết hộp thoại đang mở
    moves++;
    current = { view: route.view || null, bare: !!route.bare };
    setBackAction(backFor(route, appState.get().user));
    renderShell();
  },
  renderError: (err) => errorState(err, () => router.resolve()),
});

/** Đếm thông báo chưa đọc (dự phòng khi chưa bật Firebase; có FCM thì thông báo vẫn hiện ngay). */
async function refreshUnread() {
  if (!appState.get().user || document.hidden) return;
  try {
    const { unread } = await notificationRepo.unreadCount();
    if (unread !== appState.get().unread) appState.set({ unread });
  } catch { /* mất mạng: bỏ qua */ }
}

async function refreshShop() {
  try {
    appState.set({ shop: await settingsRepo.shop() });
  } catch { /* mất mạng: giữ thông tin cũ */ }
}

async function start() {
  await initLocalDb();
  await session.load();

  let lastUser = appState.get().user;
  appState.subscribe((s) => {
    renderShell();
    if (s.user !== lastUser) {
      lastUser = s.user;
      if (s.user) refreshUnread();
    }
  });

  router.resolve();
  refreshShop();
  refreshUnread();
  setInterval(refreshUnread, UNREAD_POLL_MS);
  setInterval(refreshShop, 5 * 60_000); // nhãn "Đang mở cửa" tự đổi theo giờ
  document.addEventListener('visibilitychange', refreshUnread);

  // PWA: đăng ký service worker (trong app Android không cần vì giao diện đã nằm sẵn trong máy)
  if ('serviceWorker' in navigator && !IS_NATIVE && window.isSecureContext) {
    navigator.serviceWorker.register('sw.js').catch((e) => console.warn('Không đăng ký được service worker', e));
  }
  // App Android: nút Back cứng -> đóng hộp thoại đang mở; không có thì quay lại màn trước; ở màn đầu thì thu app xuống
  window.Capacitor?.Plugins?.App?.addListener('backButton', ({ canGoBack }) => {
    if (closeTopSheet()) return;
    if (canGoBack && location.hash !== '#' + homeFor(appState.get().user)) history.back();
    else window.Capacitor.Plugins.App.minimizeApp();
  });
}

start();
