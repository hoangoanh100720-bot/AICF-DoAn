/**
 * SƠ ĐỒ BÀN - màn chính của nhân viên bán hàng (giống màn "Chọn bàn" của app nhân viên AICF).
 * Ghép 2 API: GET /api/tables (bàn) + GET /api/orders/active (bill đang mở) -> biết bàn nào trống / có khách.
 * Bàn được nhóm theo khu vực; mỗi trạng thái bàn có màu ô + dòng chữ riêng (State):
 *   trống (xanh) · có khách (cam: tổng tiền, số món, ngồi bao lâu) · đồ đã xong chờ mang ra (đỏ).
 * Chạm vào bàn -> mở bill (pages/bill.js). Nút "Đơn mang về" mở bill không có bàn.
 * Tự làm mới mỗi 5 giây khi màn đang hiện.
 */
import { BOARD_REFRESH_MS } from '../config.js';
import { h, mount } from '../core/dom.js';
import { duration, minutesSince, money } from '../core/format.js';
import { orderRepo, tableRepo } from '../data/repositories.js';
import { emptyState, errorState, poll, spinner, statusPill, toolbar } from '../ui/components.js';
import { icon } from '../ui/icons.js';
import { openBill } from './bill.js';

/** Trạng thái của 1 bàn: kiểu ô (màu) + các dòng chữ trên ô. */
const TABLE_STATES = {
  FREE: { kind: '', lines: () => [h('div', { class: 'st-table-free' }, 'Trống')] },
  BUSY: {
    kind: 'busy',
    lines: (o) => [h('div', { class: 'st-table-total' }, money(o.totalAmount)),
      h('div', { class: 'st-table-sub' }, o.itemCount + ' món · ' + duration(minutesSince(o.createdAt))),
      h('div', { class: 'st-table-sub' }, statusPill(o.status), o.paid ? ' ✓ đã thu' : '')],
  },
  READY: {
    kind: 'pending',
    lines: (o) => [h('div', { class: 'st-table-alert' }, 'Đồ đã xong · mang ra'),
      h('div', { class: 'st-table-sub' }, o.itemCount + ' món · ' + money(o.totalAmount))],
  },
};
const stateOf = (o) => (!o ? TABLE_STATES.FREE : o.status === 'READY' ? TABLE_STATES.READY : TABLE_STATES.BUSY);

export function render(ctx) {
  const listEl = h('div', null, spinner());
  const takeEl = h('div', { class: 'st-takeaway' });
  let last = '';

  const refresh = async () => {
    let tables;
    let open;
    try {
      [tables, open] = await Promise.all([tableRepo.list(), orderRepo.active()]);
    } catch (err) {
      if (!last) mount(listEl, errorState(err, () => handle.now()));
      return;
    }
    const sig = JSON.stringify([tables, open, Math.floor(Date.now() / 60000)]);
    if (sig === last) return;   // không đổi thì không vẽ lại (tránh nháy màn hình)
    last = sig;
    drawBoard(listEl, tables.filter((t) => t.active), open, show);
    drawTakeaways(takeEl, open.filter((o) => !o.tableId), show);
  };
  const show = (o) => openBill({ ...o, onClosed: () => { last = ''; handle.now(); } });
  const handle = poll(refresh, BOARD_REFRESH_MS);
  ctx.onLeave(() => handle.stop());

  return h('section', { 'aria-label': 'Sơ đồ bàn' },
    toolbar('Chọn bàn', h('button', { class: 'btn btn-ghost btn-sm', type: 'button', 'data-act': 'takeaway',
      onclick: () => show({ title: 'Đơn mang về' }) }, icon('bag', 16), 'Đơn mang về')),
    h('div', { class: 'st-legend' }, legend('', 'Trống'), legend('busy', 'Có khách'), legend('pending', 'Đồ xong, chờ mang ra')),
    listEl, takeEl);
}

const legend = (kind, text) => h('span', { class: 'st-legend-item' }, h('i', { class: 'st-dot ' + kind }), text);

function drawBoard(el, tables, open, show) {
  if (!tables.length) {
    mount(el, emptyState('grid', 'Chưa có bàn nào', 'Chủ quán / quản lý thêm bàn ở mục Thực đơn & bàn.'));
    return;
  }
  const byTable = new Map(open.filter((o) => o.tableId).map((o) => [o.tableId, o]));
  const areas = new Map();
  for (const t of tables) {
    const a = t.area || 'Khác';
    if (!areas.has(a)) areas.set(a, []);
    areas.get(a).push(t);
  }
  mount(el, [...areas.entries()].map(([area, list]) => {
    const busy = list.filter((t) => byTable.has(t.id)).length;
    return [h('div', { class: 'st-area' }, h('strong', null, area), h('span', null, list.length + ' bàn' + (busy ? ' · ' + busy + ' có khách' : ''))),
      h('div', { class: 'st-grid' }, list.map((t) => {
        const o = byTable.get(t.id);
        const state = stateOf(o);
        return h('button', {
          class: 'st-table ' + state.kind, type: 'button', 'data-table': t.name,
          onclick: () => show({ orderId: o?.id || null, tableId: t.id, title: t.name }),
        }, h('div', { class: 'st-table-head' }, h('strong', null, t.name), h('i', { class: 'st-dot ' + state.kind })),
        h('div', { class: 'st-table-sub' }, t.seats + ' chỗ'), state.lines(o));
      }))];
  }));
}

function drawTakeaways(el, list, show) {
  el.hidden = !list.length;
  mount(el, list.length ? [
    h('h3', { class: 'st-h' }, 'Đơn mang về đang mở (' + list.length + ')'),
    h('div', { class: 'st-chips' }, list.map((o) => h('button', {
      class: 'st-chip', type: 'button', 'data-takeaway': o.code,
      onclick: () => show({ orderId: o.id, title: 'Mang về ' + o.code.slice(-4) }),
    }, o.code.slice(-4) + ' · ' + money(o.totalAmount) + ' · ' + (o.paid ? 'đã thu' : 'chưa thu')))),
  ] : []);
}
