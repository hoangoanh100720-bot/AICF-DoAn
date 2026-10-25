/**
 * NHÂN VIÊN (chủ quán / quản lý): danh sách tài khoản của quán, tạo tài khoản mới, đổi vai trò, khóa / mở khóa,
 * cấp lại mật khẩu.  GET /api/users · POST /api/users · PUT /api/users/{id}
 * Quản lý chỉ thấy lựa chọn vai trò "Nhân viên bán hàng" (máy chủ cũng kiểm tra lại).
 */
import { h, mount } from '../core/dom.js';
import { initials, ROLE_LABEL } from '../core/format.js';
import { appState } from '../core/store.js';
import { userRepo } from '../data/repositories.js';
import { emptyState, errorState, formSheet, pill, spinner, toolbar } from '../ui/components.js';
import { icon } from '../ui/icons.js';

export function render() {
  const me = appState.get().user;
  const roleOptions = (me.role === 'OWNER' ? ['OWNER', 'MANAGER', 'STAFF'] : ['STAFF']).map((v) => ({ value: v, label: ROLE_LABEL[v] }));
  const box = h('div', null, spinner());

  const create = () => formSheet({
    title: 'Tạo tài khoản',
    fields: [
      { name: 'fullName', label: 'Họ tên', required: true },
      { name: 'username', label: 'Tên đăng nhập', required: true, minLength: 3, hint: 'Chữ không dấu, số, dấu . _ -' },
      { name: 'password', label: 'Mật khẩu ban đầu', type: 'password', required: true, minLength: 6, autocomplete: 'new-password', hint: 'Ít nhất 6 ký tự; người đó tự đổi sau khi đăng nhập' },
      { name: 'role', label: 'Vai trò', type: 'select', required: true, options: roleOptions },
      { name: 'phone', label: 'Số điện thoại', type: 'tel' },
      { name: 'email', label: 'Email Google', type: 'email', hint: 'Nhập để người này đăng nhập được bằng Google' },
    ],
    initial: { role: 'STAFF' },
    submitText: 'Tạo tài khoản',
    success: 'Đã tạo tài khoản',
    onSubmit: async (d) => { await userRepo.create(d); load(); },
  });

  const edit = (u) => formSheet({
    title: u.fullName,
    fields: [
      { name: 'fullName', label: 'Họ tên', required: true },
      { name: 'phone', label: 'Số điện thoại', type: 'tel' },
      { name: 'role', label: 'Vai trò', type: 'select', required: true, options: roleOptions.some((o) => o.value === u.role) ? roleOptions : [{ value: u.role, label: ROLE_LABEL[u.role] }] },
      { name: 'active', label: 'Cho phép đăng nhập', type: 'switch', hint: 'Tắt khi nhân viên nghỉ việc (giữ lại lịch sử đơn)' },
      { name: 'newPassword', label: 'Cấp lại mật khẩu', type: 'password', autocomplete: 'new-password', hint: 'Để trống = giữ mật khẩu cũ' },
    ],
    initial: u,
    success: 'Đã cập nhật ' + u.fullName,
    onSubmit: async (d) => { await userRepo.adminUpdate(u.id, d); load(); },
  });

  async function load() {
    let users;
    try {
      users = await userRepo.list();
    } catch (err) {
      mount(box, errorState(err, load));
      return;
    }
    mount(box, users.length ? h('div', { class: 'st-cards' }, users.map((u) => {
      const editable = me.role === 'OWNER' || u.role === 'STAFF';
      return h('button', { class: 'st-card' + (editable ? ' click' : ''), type: 'button', 'data-user': u.username, disabled: !editable,
        onclick: () => edit(u) },
      h('div', { class: 'crud-row' },
        h('span', { class: 'st-avatar' }, initials(u.fullName)),
        h('div', { class: 'grow' },
          h('strong', null, u.fullName + (u.id === me.id ? ' (bạn)' : '')),
          h('p', { class: 'note' }, '@' + u.username + (u.phone ? ' · ' + u.phone : ''))),
        h('div', { class: 'crud-pills' }, pill(ROLE_LABEL[u.role], u.role === 'OWNER' ? 'info' : u.role === 'MANAGER' ? 'warn' : ''),
          u.active ? null : pill('Đã khóa', 'bad'))));
    })) : emptyState('users', 'Chưa có tài khoản'));
  }
  load();

  return h('section', { 'aria-label': 'Nhân viên' },
    toolbar('Nhân viên', h('button', { class: 'btn btn-primary btn-sm', type: 'button', 'data-act': 'add-user', onclick: create }, icon('plus', 16), 'Tạo tài khoản')),
    h('p', { class: 'note' }, me.role === 'OWNER' ? 'Chủ quán quản lý mọi tài khoản.' : 'Quản lý được tạo và khóa tài khoản nhân viên bán hàng.'),
    box);
}
