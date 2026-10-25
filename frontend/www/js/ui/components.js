/**
 * COMPONENT GIAO DIỆN DÙNG CHUNG - cùng ngôn ngữ thiết kế với app nhân viên AICF (staff/ui.js, core/form-sheet.js):
 *   toast()          thông báo nổi ngắn
 *   sheet()          hộp thoại kéo lên từ dưới (full = tràn màn hình), nút Back của Android đóng hộp trên cùng
 *   confirmBox()     hỏi Có / Không (thay window.confirm), trả Promise<boolean>
 *   formSheet()      FORM TỰ SINH từ danh sách trường (Template Method: kiểm tra -> khóa nút -> gửi -> báo lỗi tại chỗ)
 *   toolbar(), pill(), statusPill(), emptyState(), spinner(), errorState(), field(), productThumb(), poll()
 * Mọi nội dung từ máy chủ đưa vào trang bằng textContent (hàm h()), không innerHTML.
 */
import { h } from '../core/dom.js';
import { STATUS } from '../core/format.js';
import { icon } from './icons.js';

/* ------------------------------ Thông báo nổi ------------------------------ */

/** @param {'info'|'ok'|'error'} kind */
export function toast(message, kind = 'info') {
  const box = document.getElementById('toasts');
  const el = h('div', { class: 'toast toast-' + kind, role: kind === 'error' ? 'alert' : 'status' }, message);
  box.append(el);
  setTimeout(() => el.remove(), kind === 'error' ? 5000 : 2500);
}

export const toastError = (err) => toast(err?.message || 'Có lỗi xảy ra, vui lòng thử lại.', 'error');

/* ------------------------------ Phần tử nhỏ ------------------------------ */

/**
 * Nút "Quay lại" của màn đang mở. Khung app (app.js) đặt hàm này trước khi vẽ mỗi màn:
 * màn mở từ mục "Thêm" / chuông thông báo (không nằm trên thanh dưới) thì có, màn trên thanh dưới thì null.
 */
let backAction = null;
export const setBackAction = (fn) => { backAction = fn; };

/** Thanh tiêu đề của 1 màn: [nút quay lại] + chữ to bên trái, các nút bên phải. */
export function toolbar(title, ...actions) {
  const back = backAction;
  backAction = null; // chỉ thanh tiêu đề đầu tiên của màn có nút quay lại
  return h('div', { class: 'st-toolbar' },
    h('div', { class: 'st-title' },
      back ? h('button', { class: 'icon-btn st-back', type: 'button', 'aria-label': 'Quay lại', 'data-act': 'back', onclick: back }, icon('back', 22)) : null,
      h('h1', null, title)),
    actions.length ? h('div', { class: 'st-actions' }, actions) : null);
}

/** Tem nhỏ bo tròn. kind: '' | ok | warn | bad | info */
export const pill = (text, kind = '') => h('span', { class: 'st-pill' + (kind ? ' ' + kind : '') }, text);

/** Tem trạng thái đơn (Chờ pha / Đang pha / ...). */
export const statusPill = (status) => pill(...(STATUS[status] || [status, '']));

export const emptyState = (iconName, title, hint, action) => h('div', { class: 'st-empty' },
  icon(iconName, 34), h('strong', null, title), hint ? h('p', null, hint) : null, action || null);

export const spinner = (text = 'Đang tải…') => h('div', { class: 'st-empty', 'aria-busy': 'true' }, h('p', null, text));

export const errorState = (err, retry) => emptyState(err?.status === 0 ? 'alert' : 'alert',
  err?.status === 0 ? 'Mất kết nối máy chủ' : 'Không tải được dữ liệu', err?.message,
  retry ? h('button', { class: 'btn btn-ghost', type: 'button', onclick: retry }, 'Thử lại') : null);

/** Nhóm "nhãn + ô nhập". */
export const field = (label, control, hint) => h('div', { class: 'field' },
  h('label', { for: control.id || null }, label), control, hint ? h('p', { class: 'hint' }, hint) : null);

/** Ảnh món; không có ảnh / ảnh lỗi thì khung tách cà phê trên nền màu (màu đổi theo nhóm món). */
export function productThumb(p) {
  const placeholder = () => {
    let hash = 0;
    for (const ch of p.categoryName || p.name || '') hash = (hash * 31 + ch.charCodeAt(0)) >>> 0;
    return h('div', { class: 'placeholder tint-' + (hash % 4), 'aria-hidden': 'true' }, icon('coffee', 34));
  };
  if (!p.imageUrl) return placeholder();
  const img = h('img', { class: 'thumb', src: p.imageUrl, alt: '', loading: 'lazy' });
  img.addEventListener('error', () => img.replaceWith(placeholder()));
  return img;
}

/* ------------------------------ Hộp thoại kéo lên ------------------------------ */

const stack = []; // các hộp đang mở, cái trên cùng ở cuối

/**
 * Mở hộp thoại kéo lên. opts: { title, body, footer, full, onClose }.
 * Trả về { el, body, close, setTitle, setFooter }.
 */
export function sheet(opts = {}) {
  const titleEl = h('h2', null, opts.title || '');
  const closeBtn = h('button', { class: 'icon-btn', type: 'button', 'aria-label': 'Đóng' }, icon('x', 20));
  const body = h('div', { class: 'st-sheet-body' }, opts.body || null);
  const footer = h('div', { class: 'st-sheet-foot', hidden: !opts.footer }, opts.footer || null);
  const panel = h('div', { class: 'sheet-panel st-sheet' + (opts.full ? ' full' : '') },
    h('div', { class: 'sheet-grip', 'aria-hidden': 'true' }),
    h('div', { class: 'sheet-head' }, titleEl, closeBtn), body, footer);
  const el = h('div', { class: 'sheet st-layer', role: 'dialog', 'aria-modal': 'true', 'aria-label': opts.title || '' },
    h('div', { class: 'sheet-backdrop' }), panel);
  const api = {
    el, body, closed: false,
    setTitle: (t) => { titleEl.textContent = t; },
    setFooter: (node) => { footer.replaceChildren(...(node ? [node] : [])); footer.hidden = !node; },
    close() {
      if (api.closed) return;
      api.closed = true;
      const i = stack.indexOf(api);
      if (i >= 0) stack.splice(i, 1);
      el.remove();
      if (opts.onClose) opts.onClose();
    },
  };
  el.querySelector('.sheet-backdrop').addEventListener('click', () => api.close());
  closeBtn.addEventListener('click', () => api.close());
  stack.push(api);
  document.body.append(el);
  return api;
}

/** Đóng hộp trên cùng (nút Back Android / phím Esc). Trả true nếu có hộp để đóng. */
export function closeTopSheet() {
  const top = stack[stack.length - 1];
  if (!top) return false;
  top.close();
  return true;
}

/** Đóng hết (khi chuyển màn hình). */
export function closeAllSheets() {
  while (stack.length) stack[stack.length - 1].close();
}

document.addEventListener('keydown', (e) => { if (e.key === 'Escape') closeTopSheet(); });

/** Hỏi xác nhận. @returns {Promise<boolean>} */
export function confirmBox({ title = 'Xác nhận', message, okText = 'Đồng ý', danger = false }) {
  return new Promise((resolve) => {
    let answered = false;
    const done = (v) => { if (!answered) { answered = true; resolve(v); } };
    const sh = sheet({ title, onClose: () => done(false) });
    sh.body.append(h('p', { class: 'st-confirm-text' }, message));
    sh.setFooter(h('div', { class: 'st-actions' },
      h('button', { class: 'btn btn-ghost', type: 'button', onclick: () => sh.close() }, 'Không'),
      h('button', { class: 'btn ' + (danger ? 'btn-danger' : 'btn-primary'), type: 'button', 'data-act': 'confirm',
        onclick: () => { done(true); sh.close(); } }, okText)));
  });
}

/* ------------------------------ Form tự sinh ------------------------------ */

/**
 * Tạo 1 ô nhập theo cấu hình. Kiểu: text | password | email | tel | number | textarea | select | switch.
 * @param {object} f  { name, label, type, required, min, max, minLength, placeholder, hint, options: [{value,label}] }
 */
function control(f, value) {
  const id = 'f-' + f.name + '-' + Math.random().toString(36).slice(2, 7);
  if (f.type === 'switch') {
    const input = h('input', { type: 'checkbox', id, name: f.name, checked: !!value });
    return h('label', { class: 'mod-row', for: id, dataset: { field: f.name } },
      h('span', { class: 'mod-text' }, h('strong', null, f.label), f.hint ? h('p', { class: 'note' }, f.hint) : null),
      h('span', { class: 'mod-switch' }, input, h('span', { class: 'mod-knob' })));
  }
  const common = { id, name: f.name, required: !!f.required, placeholder: f.placeholder || null, autocomplete: f.autocomplete || 'off' };
  let input;
  if (f.type === 'textarea') input = h('textarea', { ...common, rows: 3 }, value ?? '');
  else if (f.type === 'select') {
    input = h('select', common, !f.required ? h('option', { value: '' }, '— Chọn —') : null,
      (f.options || []).map((o) => h('option', { value: o.value, selected: String(o.value) === String(value ?? '') }, o.label)));
  } else {
    input = h('input', {
      ...common, type: f.type || 'text', value: value ?? '', min: f.min ?? null, max: f.max ?? null,
      minlength: f.minLength ?? null, inputmode: f.type === 'number' ? 'numeric' : null,
    });
  }
  return h('div', { class: 'field', dataset: { field: f.name } },
    h('label', { for: id }, f.label, f.required ? ' *' : ''), input,
    f.hint ? h('p', { class: 'hint' }, f.hint) : null, h('p', { class: 'err', role: 'alert' }));
}

/** Vẽ các ô nhập theo cấu hình (dùng chung cho formSheet và form đặt thẳng trên trang). */
export const formFields = (fields, initial = {}) => fields.map((f) => control(f, initial[f.name] ?? f.default));

/** Đọc giá trị form theo cấu hình trường (đổi number / switch về đúng kiểu). */
export function readForm(form, fields) {
  const out = {};
  for (const f of fields) {
    const el = form.elements[f.name];
    if (!el) continue;
    if (f.type === 'switch') out[f.name] = el.checked;
    else if (f.type === 'number') out[f.name] = el.value === '' ? null : Number(el.value);
    else out[f.name] = el.value.trim() === '' ? null : el.value.trim();
  }
  return out;
}

/** Hiện lỗi máy chủ trả về ("name: không được để trống") ngay dưới ô tương ứng. Trả true nếu đã hiện được. */
export function showFieldErrors(form, err) {
  form.querySelectorAll('.field .err').forEach((e) => { e.textContent = ''; });
  form.querySelectorAll('[aria-invalid]').forEach((e) => e.removeAttribute('aria-invalid'));
  let shown = false;
  for (const d of err?.details || []) {
    const [name, ...rest] = d.split(':');
    const box = form.querySelector(`.field[data-field="${name.trim()}"]`);
    if (!box) continue;
    box.querySelector('.err').textContent = rest.join(':').trim();
    box.querySelector('input, select, textarea')?.setAttribute('aria-invalid', 'true');
    shown = true;
  }
  return shown;
}

/**
 * HỘP THOẠI FORM TỰ SINH - viết 1 lần, dùng cho mọi màn thêm / sửa (món, danh mục, bàn, nhân viên, hồ sơ...).
 * Khung xử lý cố định: kiểm tra trên máy -> khóa nút -> onSubmit (gọi API) -> xong: đóng + báo
 *                                                                            -> lỗi: hiện ngay trong hộp, mở lại nút.
 * @param {object} o
 * @param {string} o.title
 * @param {object[]} o.fields
 * @param {object} [o.initial]
 * @param {string} [o.submitText]
 * @param {'primary'|'ok'|'danger'} [o.kind]
 * @param {(data) => Promise<any>} o.onSubmit
 * @param {string} [o.success]  câu báo khi xong
 * @param {Node} [o.extra]      phần thêm dưới form (vd nút Xóa)
 */
export function formSheet({ title, fields, initial = {}, submitText = 'Lưu', kind = 'primary', onSubmit, success, extra }) {
  const formId = 'form-' + Math.random().toString(36).slice(2, 8);
  const err = h('p', { class: 'form-error', role: 'alert' });
  const btn = h('button', { class: 'btn btn-' + kind + ' btn-lg btn-block', type: 'submit', form: formId }, submitText);
  const form = h('form', { id: formId, class: 'st-form', novalidate: true },
    formFields(fields, initial), err, extra || null);
  const sh = sheet({ title, body: form, footer: btn });
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    err.textContent = '';
    if (!form.reportValidity()) return; // required / min / max kiểm tra ngay trên máy
    btn.disabled = true;
    try {
      await onSubmit(readForm(form, fields));
      sh.close();
      if (success) toast(success, 'ok');
    } catch (ex) {
      if (!showFieldErrors(form, ex)) err.textContent = ex.message;
      btn.disabled = false;
    }
  });
  form.querySelector('input:not([type=checkbox]), select, textarea')?.focus();
  return sh;
}

/* ------------------------------ Tự làm mới ------------------------------ */

/**
 * Chạy fn ngay và lặp lại mỗi ms khi màn hình đang hiện (tạm dừng khi app ở nền).
 * Lượt trước chưa xong (mạng chậm) thì bỏ qua lượt mới, tránh dồn nhiều yêu cầu. Trả về { stop, now }.
 */
export function poll(fn, ms) {
  let stopped = false;
  let running = false;
  const tick = async () => {
    if (stopped || running || document.hidden) return;
    running = true;
    try { await fn(); } catch { /* lỗi mạng tạm thời: lần sau thử lại */ } finally { running = false; }
  };
  const timer = setInterval(tick, ms);
  const onVisible = () => { if (!document.hidden) tick(); };
  document.addEventListener('visibilitychange', onVisible);
  tick();
  return {
    now: tick,
    stop() {
      stopped = true;
      clearInterval(timer);
      document.removeEventListener('visibilitychange', onVisible);
    },
  };
}
