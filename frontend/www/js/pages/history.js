/**
 * ĐƠN HÀNG TRONG NGÀY (lịch sử) - GET /api/orders?date=YYYY-MM-DD.
 * Chọn ngày, lọc theo trạng thái; chạm vào 1 đơn để mở bill (xem lại / thu tiền / hủy).
 * Mở từ thông báo: #/history?order=12 -> mở thẳng bill số 12.
 */
import { h, mount } from '../core/dom.js';
import { hm, money, PAYMENT_LABEL, todayIso } from '../core/format.js';
import { orderRepo } from '../data/repositories.js';
import { emptyState, errorState, spinner, statusPill, toolbar } from '../ui/components.js';
import { openBill } from './bill.js';

const FILTERS = [
  ['ALL', 'Tất cả', () => true],
  ['OPEN', 'Đang mở', (o) => o.status !== 'COMPLETED' && o.status !== 'CANCELLED'],
  ['UNPAID', 'Chưa thu tiền', (o) => !o.paid && o.status !== 'CANCELLED'],
  ['COMPLETED', 'Hoàn tất', (o) => o.status === 'COMPLETED'],
  ['CANCELLED', 'Đã hủy', (o) => o.status === 'CANCELLED'],
];

export function render(ctx) {
  let date = ctx.query.date || todayIso();
  let filter = 'ALL';
  let orders = [];
  const chips = h('div', { class: 'st-chips scroll' });
  const summary = h('p', { class: 'note' });
  const list = h('div', { class: 'st-cards' }, spinner());
  const picker = h('input', {
    class: 'st-select', type: 'date', value: date, max: todayIso(), 'aria-label': 'Chọn ngày',
    onchange: (e) => { date = e.target.value || todayIso(); load(); },
  });

  const draw = () => {
    mount(chips, FILTERS.map(([k, label, fn]) => h('button', {
      class: 'st-chip' + (filter === k ? ' on' : ''), type: 'button', onclick: () => { filter = k; draw(); },
    }, label + ' (' + orders.filter(fn).length + ')')));
    const paid = orders.filter((o) => o.paid && o.status !== 'CANCELLED');
    summary.textContent = orders.length + ' đơn · đã thu ' + money(paid.reduce((s, o) => s + o.totalAmount, 0));
    const shown = orders.filter(FILTERS.find((f) => f[0] === filter)[2]);
    mount(list, shown.length ? shown.map(card) : emptyState('clipboard', 'Không có đơn nào', date === todayIso() ? 'Đơn mở trong hôm nay sẽ hiện ở đây.' : null));
  };

  const card = (o) => h('button', { class: 'st-card click', type: 'button', 'data-order': o.code, onclick: () => show(o.id, o) },
    h('div', { class: 'st-card-row' }, h('strong', { class: 'st-card-title' }, o.tableName || 'Mang về'), statusPill(o.status)),
    h('p', { class: 'note' }, o.code + ' · ' + hm(o.createdAt) + ' · ' + o.createdByName),
    h('p', { class: 'note' }, o.items.map((i) => i.quantity + '× ' + i.productName).join(', ')),
    h('div', { class: 'st-card-row' },
      h('span', { class: o.paid ? 'ok-text' : 'warn-text' }, o.paid ? '✓ ' + PAYMENT_LABEL[o.paymentMethod] : o.status === 'CANCELLED' ? '' : 'Chưa thu tiền'),
      h('strong', { class: 'st-price' }, money(o.totalAmount))));

  const show = (id, o) => openBill({ orderId: id, tableId: o?.tableId ?? null, title: o?.tableName || 'Đơn hàng', onClosed: load });

  async function load() {
    try {
      orders = await orderRepo.ofDay(date);
      draw();
    } catch (err) {
      mount(list, errorState(err, load));
    }
  }
  load();
  if (ctx.query.order) show(Number(ctx.query.order));

  return h('section', { 'aria-label': 'Đơn hàng' }, toolbar('Đơn hàng', picker), summary, chips, list);
}
