/**
 * MÀN HÌNH ĐĂNG NHẬP (giống màn đăng nhập app nhân viên AICF: logo, tên quán, ô tài khoản, nút lớn).
 *  - Đăng nhập bằng tên + mật khẩu  -> POST /api/auth/login
 *  - Đăng nhập Google (Firebase)    -> Firebase Auth -> POST /api/auth/firebase (chỉ tài khoản chủ quán đã cấp email)
 *  - App Android: nhập địa chỉ máy chủ, bấm "Kiểm tra" gọi GET /api/health
 * Không có đăng ký: tài khoản do chủ quán / quản lý tạo ở màn Nhân viên.
 */
import { IS_NATIVE } from '../config.js';
import { h } from '../core/dom.js';
import { navigate } from '../core/router.js';
import { appState } from '../core/store.js';
import { authRepo, settingsRepo } from '../data/repositories.js';
import { session } from '../data/session.js';
import { canUseGoogleSignIn, googleSignIn } from '../firebase/firebase.js';
import { field, formSheet, toast } from '../ui/components.js';
import { icon } from '../ui/icons.js';

const DEMO = [['chu', 'chu123', 'Chủ quán'], ['quanly', 'quanly123', 'Quản lý'], ['nhanvien', 'nhanvien123', 'Nhân viên']];

export function render(ctx) {
  const shop = appState.get().shop;
  const user = h('input', { id: 'username', name: 'username', autocomplete: 'username', autocapitalize: 'none', spellcheck: 'false' });
  const pass = h('input', { id: 'password', name: 'password', type: 'password', autocomplete: 'current-password' });
  const err = h('p', { class: 'form-error', role: 'alert' }, ctx.query.expired ? 'Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.' : '');
  const btn = h('button', { class: 'btn btn-primary btn-lg btn-block', type: 'submit' }, 'Đăng nhập');

  const finish = async (auth) => {
    await session.signIn(auth);
    toast('Xin chào, ' + auth.user.fullName + '!', 'ok');
    navigate('/');   // router đưa về màn đầu tiên theo vai trò
  };

  const form = h('form', { class: 'form', novalidate: true },
    field('Tên đăng nhập', user), field('Mật khẩu', pass), err, btn);
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    if (!user.value.trim() || !pass.value) { err.textContent = 'Vui lòng nhập tên đăng nhập và mật khẩu.'; return; }
    err.textContent = '';
    btn.disabled = true;
    btn.textContent = 'Đang đăng nhập…';
    try {
      await finish(await authRepo.login(user.value.trim(), pass.value));
    } catch (ex) {
      err.textContent = ex.message;
      btn.disabled = false;
      btn.textContent = 'Đăng nhập';
    }
  });

  // Đăng nhập Google qua Firebase (chỉ hiện khi đã cấu hình Firebase và đang chạy trên trình duyệt)
  const google = canUseGoogleSignIn() ? h('button', {
    class: 'btn btn-ghost btn-lg btn-block', type: 'button',
    onclick: async (e) => {
      const b = e.currentTarget;
      b.disabled = true;
      try {
        await finish(await authRepo.firebase(await googleSignIn()));
      } catch (ex) {
        if (ex?.code !== 'auth/popup-closed-by-user') err.textContent = ex.message;
      } finally {
        b.disabled = false;
      }
    },
  }, h('strong', { style: { color: '#4285f4' } }, 'G'), 'Đăng nhập bằng Google') : null;

  // Bấm nhanh tài khoản mẫu (đồ án demo)
  const demo = h('div', { class: 'st-chips', 'aria-label': 'Tài khoản mẫu' }, DEMO.map(([u, p, label]) =>
    h('button', { class: 'st-chip', type: 'button', onclick: () => { user.value = u; pass.value = p; btn.focus(); } }, label)));

  return h('div', { class: 'st-login' },
    h('div', { class: 'st-login-brand' },
      h('span', { class: 'logo' }, icon('coffee', 28)),
      h('h1', null, shop?.name || 'AICF Coffee'),
      h('p', null, 'App bán hàng cho chủ quán, quản lý và nhân viên')),
    h('div', { class: 'card track-card' }, form, google ? h('div', { class: 'st-or' }, 'hoặc') : null, google),
    h('div', { class: 'st-demo' }, h('p', { class: 'note' }, 'Tài khoản mẫu (bấm để điền):'), demo),
    h('div', { class: 'st-login-links' },
      IS_NATIVE ? h('button', { class: 'st-link', type: 'button', onclick: serverSheet }, 'Địa chỉ máy chủ') : null,
      h('span', { class: 'note' }, 'Quên mật khẩu? Nhờ chủ quán cấp lại.')));
}

/** App Android: đổi địa chỉ máy chủ (máy ảo: http://10.0.2.2:8090, điện thoại thật: IP máy tính trong cùng Wi-Fi). */
export function serverSheet() {
  formSheet({
    title: 'Địa chỉ máy chủ',
    fields: [{ name: 'url', label: 'URL máy chủ', type: 'url', required: true,
      hint: 'Máy ảo Android: http://10.0.2.2:8090 · Điện thoại thật: http://<IP máy tính>:8090' }],
    initial: { url: session.serverUrl() },
    submitText: 'Kiểm tra & lưu',
    onSubmit: async ({ url }) => {
      await session.setServerUrl(url);
      await settingsRepo.health();
      appState.set({ shop: await settingsRepo.shop().catch(() => null) });
    },
    success: 'Kết nối máy chủ thành công',
  });
}
