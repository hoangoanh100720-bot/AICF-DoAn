/**
 * PHA CHẾ - màn của quầy bar (giống màn "Pha chế" của app nhân viên AICF):
 * 3 thẻ Chờ pha (n) / Đang pha (n) / Đã xong (n); mỗi bill 1 thẻ, bill chờ quá 10 phút viền đỏ.
 * Nút trên thẻ: Bắt đầu / Xong / Đã mang ra  -> PATCH /api/orders/{id}/status.
 * Dữ liệu: GET /api/orders/active, tự làm mới mỗi 5 giây; có bill mới thì điện thoại rung nhẹ.
 */
import { BOARD_REFRESH_MS } from '../config.js';
import { h, mount } from '../core/dom.js';
import { minutesSince } from '../core/format.js';
import { orderRepo } from '../data/repositories.js';
import { emptyState, poll, spinner, toast, toastError, toolbar } from '../ui/components.js';
import { openBill } from './bill.js';

const LATE_MINUTES = 10;
const COLS = [
  ['NEW', 'Chờ pha', 'Không có bill chờ pha'],
  ['PREPARING', 'Đang pha', 'Chưa có bill đang pha'],
  ['READY', 'Đã xong', 'Chưa có bill chờ mang ra'],
];

export function render(ctx) {
  let orders = [];
  let tab = null;          // null = tự chọn cột đầu tiên có bill
  let known = null;        // id các bill "Chờ pha" lần trước (để biết có bill mới)
  const seg = h('div', { class: 'st-seg' });
  const list = h('div', { class: 'st-cards' }, spinner());
  const err = h('p', { class: 'form-error', role: 'alert' });

  const where = (o) => (o.tableName || 'Mang về ' + o.code.slice(-4));

  const step = async (btn, o, status) => {
    btn.disabled = true;
    try {
      await orderRepo.setStatus(o.id, status);
      if (status === 'READY') toast(where(o) + ' đã xong - báo phục vụ mang ra', 'ok');
    } catch (e) {
      toastError(e);
    }
    await load();
  };

  const card = (o) => {
    const wait = minutesSince(o.createdAt);
    const late = wait >= LATE_MINUTES && o.status !== 'READY';
    const actionBtn = (label, kind, status, act) => {
      const b = h('button', { class: 'btn ' + kind, type: 'button', 'data-act': act });
      b.textContent = label;
      b.addEventListener('click', () => step(b, o, status));
      return b;
    };
    const actions = o.status === 'NEW'
      ? [actionBtn('Bắt đầu', 'btn-primary', 'PREPARING', 'start'), actionBtn('Xong', 'btn-ok', 'READY', 'done')]
      : o.status === 'PREPARING'
        ? [actionBtn('Xong', 'btn-ok', 'READY', 'done')]
        : [actionBtn('Đã mang ra', 'btn-ghost', 'SERVED', 'served')];
    return h('div', { class: 'st-card' + (late ? ' late' : ''), 'data-order': o.code },
      h('button', { class: 'st-link st-card-title', type: 'button', onclick: () => openBill({ orderId: o.id, tableId: o.tableId, title: where(o), onClosed: load }) },
        where(o) + ' · ' + o.itemCount + ' ly'),
      h('p', { class: late ? 'bad-text' : 'note' }, o.code + ' · ' + wait + ' phút · ' + o.createdByName),
      h('ul', { class: 'st-frame-items' }, o.items.map((i) => h('li', null, h('strong', null, i.quantity + ' × ' + i.productName),
        i.note ? h('span', { class: 'st-note-line' }, ' — ' + i.note) : null))),
      o.note ? h('p', { class: 'st-note-line' }, '📝 ' + o.note) : null,
      h('div', { class: 'st-actions' }, actions));
  };

  const draw = () => {
    const rows = (k) => orders.filter((o) => o.status === k);
    if (!tab) tab = (COLS.find(([k]) => rows(k).length) || COLS[0])[0];
    mount(seg, COLS.map(([k, label]) => h('button', {
      class: 'st-seg-btn' + (tab === k ? ' on' : ''), type: 'button', 'data-tab': k,
      onclick: () => { tab = k; draw(); },
    }, label + ' (' + rows(k).length + ')')));
    const cur = rows(tab);
    mount(list, cur.length ? cur.map(card) : emptyState('coffee', COLS.find((c) => c[0] === tab)[2]));
  };

  const load = async () => {
    try {
      orders = await orderRepo.active();
      err.textContent = '';
    } catch {
      err.textContent = '⚠ Mất kết nối máy chủ, đang thử lại…';
      return;
    }
    const ids = new Set(orders.filter((o) => o.status === 'NEW').map((o) => o.id));
    if (known && [...ids].some((id) => !known.has(id))) {
      navigator.vibrate?.(250);   // có bill mới
      toast('Có bill mới cần pha', 'info');
    }
    known = ids;
    draw();
  };
  const handle = poll(load, BOARD_REFRESH_MS);
  ctx.onLeave(() => handle.stop());

  return h('section', { 'aria-label': 'Pha chế' }, toolbar('Pha chế'), seg, err, list);
}
