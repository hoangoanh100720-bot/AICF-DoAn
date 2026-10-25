/**
 * ROUTER theo dấu # (hash routing) cho Single Page App.
 *
 * Khai báo route:
 *   { path: '/staff', title: 'Nhân viên', load: () => import('../pages/staff.js'),
 *     auth: true, roles: ['OWNER', 'MANAGER'] }
 * - load: import() ĐỘNG -> chỉ tải code của màn hình khi mở tới (app khởi động nhanh hơn)
 * - auth: cần đăng nhập (chưa đăng nhập -> chuyển tới /login?next=<màn đang mở>);
 *   roles: chỉ các vai trò này được vào (không khai báo = mọi vai trò)
 *
 * Mỗi trang là 1 hàm render(ctx) trả về phần tử DOM (hoặc Promise). ctx gồm:
 *   params  : tham số trên đường dẫn, vd { id: '12' }
 *   query   : tham số sau dấu ?, vd #/menu?category=2 -> { category: '2' }
 *   onLeave : đăng ký hàm dọn dẹp chạy khi rời màn hình (hủy setInterval, hủy subscribe...)
 */
export class Router {
  /**
   * @param {object} opts
   * @param {HTMLElement} opts.outlet        nơi vẽ màn hình
   * @param {Array} opts.routes              danh sách route
   * @param {() => object|null} opts.getUser người dùng hiện tại
   * @param {(route, user) => string} opts.homeFor  trang chủ theo vai trò
   * @param {(route) => void} [opts.onChange]       gọi sau khi đổi màn hình (cập nhật tab, tiêu đề)
   * @param {(err) => HTMLElement} opts.renderError
   */
  constructor({ outlet, routes, getUser, homeFor, onChange, renderError }) {
    this.outlet = outlet;
    this.routes = routes.map((r) => ({ ...r, ...compile(r.path) }));
    this.getUser = getUser;
    this.homeFor = homeFor;
    this.onChange = onChange;
    this.renderError = renderError;
    this.cleanups = [];
    this.renderId = 0;
    window.addEventListener('hashchange', () => this.resolve());
  }

  /** Chuyển màn hình bằng code, vd router.go('/orders/5'). */
  go(path) {
    if (location.hash === '#' + path) this.resolve();
    else location.hash = path;
  }

  /** Vẽ lại màn hình hiện tại (vd. sau khi đăng nhập). */
  async resolve() {
    const [rawPath, rawQuery = ''] = (location.hash.slice(1) || '/').split('?');
    const user = this.getUser();
    let match = null;
    for (const r of this.routes) {
      const m = r.regex.exec(rawPath);
      if (m) {
        match = { route: r, params: Object.fromEntries(r.keys.map((k, i) => [k, decodeURIComponent(m[i + 1])])) };
        break;
      }
    }
    // Không có route / vào "/" / chưa đăng nhập / sai vai trò -> chuyển hướng
    if (!match || rawPath === '/') return this.go(this.homeFor(user));
    const { route } = match;
    // Màn cần đăng nhập: đưa sang Đăng nhập, kèm ?next= để đăng nhập xong quay lại đúng màn đang muốn mở
    if (route.auth && !user) return this.go('/login?next=' + encodeURIComponent(rawPath));
    if (route.guestOnly && user) return this.go(this.homeFor(user));
    if (route.roles && user && !route.roles.includes(user.role)) return this.go(this.homeFor(user));

    // Dọn dẹp màn hình cũ
    this.cleanups.forEach((fn) => { try { fn(); } catch { /* bỏ qua */ } });
    this.cleanups = [];
    const id = ++this.renderId;
    this.onChange?.(route);

    try {
      const mod = await route.load();
      const render = mod[route.export || 'render'];
      const ctx = {
        params: match.params,
        query: Object.fromEntries(new URLSearchParams(rawQuery)),
        onLeave: (fn) => this.cleanups.push(fn),
      };
      const el = await render(ctx);
      if (id !== this.renderId) return; // người dùng đã chuyển màn hình khác trong lúc đang tải
      this.outlet.replaceChildren(el);
      this.outlet.scrollTop = 0;
    } catch (err) {
      console.error(err);
      if (id === this.renderId) this.outlet.replaceChildren(this.renderError(err));
    }
  }
}

/** Đổi '/orders/:id' thành biểu thức chính quy ^/orders/([^/]+)$ và danh sách tên tham số ['id']. */
function compile(path) {
  const keys = [];
  const pattern = path.replace(/:([a-zA-Z]+)/g, (_, k) => {
    keys.push(k);
    return '([^/]+)';
  });
  return { regex: new RegExp('^' + pattern + '$'), keys };
}

/** Chuyển màn hình từ bất kỳ đâu trong app, vd navigate('/orders/5'). */
export function navigate(path) {
  location.hash = path;
}
