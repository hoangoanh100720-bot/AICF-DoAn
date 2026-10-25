/**
 * MỤC "THÊM / TÔI" (giống mục "Thêm" của app nhân viên AICF): thẻ người đang đăng nhập, các chức năng không nằm
 * trên thanh dưới (Trợ lý AI, Thực đơn & bàn, Nhân viên, Cài đặt... tùy vai trò), tài khoản (sửa hồ sơ,
 * đổi mật khẩu, bật thông báo đẩy, địa chỉ máy chủ), đăng xuất.
 */
import { moreViews } from '../app.js';
import { IS_NATIVE } from '../config.js';
import { h } from '../core/dom.js';
import { initials, ROLE_LABEL } from '../core/format.js';
import { navigate } from '../core/router.js';
import { appState } from '../core/store.js';
import { cart } from '../data/cart.js';
import { userRepo } from '../data/repositories.js';
import { session } from '../data/session.js';
import { firebaseSignOut } from '../firebase/firebase.js';
import { disablePush, enablePush, pushAvailable } from '../firebase/push.js';
import { confirmBox, formSheet, toast, toastError, toolbar } from '../ui/components.js';
import { icon } from '../ui/icons.js';
import { serverSheet } from './login.js';

const item = (iconName, label, onclick, extra) => h('button', { class: 'st-menu-item', type: 'button', onclick, 'data-menu': label },
  icon(iconName, 20), h('span', null, label), extra || null, icon('chevron', 18));

export function render() {
  const user = appState.get().user;
  const extras = moreViews(user);

  const editProfile = () => formSheet({
    title: 'Sửa hồ sơ',
    fields: [
      { name: 'fullName', label: 'Họ tên', required: true },
      { name: 'phone', label: 'Số điện thoại', type: 'tel' },
      { name: 'email', label: 'Email Google', type: 'email', hint: 'Dùng để đăng nhập bằng Google' },
    ],
    initial: user,
    success: 'Đã lưu hồ sơ',
    onSubmit: async (data) => {
      await session.setUser(await userRepo.updateMe(data));
      navigate('/more?' + Date.now()); // vẽ lại màn hình
    },
  });

  const changePassword = () => formSheet({
    title: user.hasPassword ? 'Đổi mật khẩu' : 'Đặt mật khẩu',
    fields: [
      user.hasPassword ? { name: 'currentPassword', label: 'Mật khẩu hiện tại', type: 'password', required: true, autocomplete: 'current-password' } : null,
      { name: 'newPassword', label: 'Mật khẩu mới', type: 'password', required: true, minLength: 6, autocomplete: 'new-password' },
    ].filter(Boolean),
    submitText: 'Đổi mật khẩu',
    success: 'Đã đổi mật khẩu',
    onSubmit: (d) => userRepo.changePassword(d.currentPassword, d.newPassword),
  });

  const turnOnPush = async () => {
    try {
      if (await enablePush((orderId) => navigate('/history?order=' + orderId))) toast('Đã bật thông báo đẩy', 'ok');
    } catch (err) {
      toastError(err);
    }
  };

  const logout = async () => {
    if (!(await confirmBox({ title: 'Đăng xuất', message: 'Đăng xuất khỏi tài khoản này?', okText: 'Đăng xuất' }))) return;
    await disablePush();
    await firebaseSignOut();
    await cart.clear();
    await session.signOut();
    navigate('/login');
  };

  return h('section', { 'aria-label': 'Tài khoản' },
    toolbar(extras.length ? 'Thêm' : 'Tôi'),
    h('div', { class: 'st-me card' },
      h('span', { class: 'st-avatar big' }, initials(user.fullName)),
      h('div', null, h('strong', null, user.fullName), h('p', { class: 'note' }, '@' + user.username + ' · ' + ROLE_LABEL[user.role]))),
    extras.length ? h('h3', { class: 'st-h' }, 'Chức năng khác') : null,
    extras.length ? h('div', { class: 'st-menu' }, extras.map((v) => item(v.icon, v.title, () => navigate(v.path)))) : null,
    h('h3', { class: 'st-h' }, 'Tài khoản'),
    h('div', { class: 'st-menu' },
      item('bell', 'Thông báo', () => navigate('/notifications'),
        appState.get().unread ? h('span', { class: 'st-pill bad' }, appState.get().unread + ' mới') : null),
      item('user', 'Sửa hồ sơ', editProfile),
      item('lock', user.hasPassword ? 'Đổi mật khẩu' : 'Đặt mật khẩu', changePassword),
      pushAvailable() ? item('bell', 'Bật thông báo đẩy (Firebase)', turnOnPush) : null,
      IS_NATIVE ? item('swap', 'Địa chỉ máy chủ', serverSheet) : null,
      h('button', { class: 'st-menu-item danger', type: 'button', 'data-act': 'logout', onclick: logout }, icon('logout', 20), h('span', null, 'Đăng xuất'))));
}
