/**
 * TỔNG QUAN (chủ quán / quản lý) - giống màn Tổng quan của app nhân viên AICF:
 *   ô KPI lớn "Doanh thu hôm nay" (tách tiền mặt / chuyển khoản), bill đã thu, bàn đang có khách, đơn hủy;
 *   biểu đồ cột doanh thu 7 ngày; 5 món bán chạy (thanh ngang); mục "Cần chú ý".
 * Dữ liệu: GET /api/stats/dashboard?date=... + GET /api/orders/active. Tự làm mới mỗi 30 giây.
 */
import { h, mount } from '../core/dom.js';
import { money, shortMoney, todayIso } from '../core/format.js';
import { appState } from '../core/store.js';
import { orderRepo, statsRepo } from '../data/repositories.js';
import { errorState, poll, spinner, toolbar } from '../ui/components.js';
import { icon } from '../ui/icons.js';

const DOW = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'];

export function render(ctx) {
  let date = ctx.query.date || todayIso();
  const host = h('div', null, spinner());
  const hour = new Date().getHours();
  const part = hour < 11 ? 'buổi sáng' : hour < 13 ? 'buổi trưa' : hour < 18 ? 'buổi chiều' : 'buổi tối';
  const picker = h('input', {
    class: 'st-select', type: 'date', value: date, max: todayIso(), 'aria-label': 'Chọn ngày',
    onchange: (e) => { date = e.target.value || todayIso(); handle.now(); },
  });

  const load = async () => {
    try {
      const [d, open] = await Promise.all([statsRepo.dashboard(date), orderRepo.active()]);
      draw(host, d, open, date === todayIso());
    } catch (err) {
      mount(host, errorState(err, load));
    }
  };
  const handle = poll(load, 30_000);
  ctx.onLeave(() => handle.stop());

  return h('section', { 'aria-label': 'Tổng quan' },
    toolbar('Chào ' + part + ', ' + (appState.get().user?.fullName || '')), h('div', { class: 'st-toolbar' }, h('span', { class: 'note' }, 'Số liệu ngày'), picker), host);
}

function draw(host, d, open, isToday) {
  const kpi = (iconName, label, value, note, hero = false) => h('div', { class: 'st-kpi wide' + (hero ? ' hero' : '') },
    h('span', { class: 'st-kpi-icon' }, icon(iconName, 20)), h('div', null, h('span', null, label), h('strong', null, value), h('small', null, note)));

  // Biểu đồ cột 7 ngày (HTML/CSS, không cần thư viện); cột của ngày đang xem tô đậm
  const max = Math.max(1, ...d.last7Days.map((x) => x.revenue));
  const total7 = d.last7Days.reduce((s, x) => s + x.revenue, 0);
  const bars = h('div', { class: 'st-bars', role: 'img', 'aria-label': 'Doanh thu 7 ngày: ' + d.last7Days.map((x) => x.date.slice(8) + '/' + x.date.slice(5, 7) + ' ' + money(x.revenue)).join(', ') },
    d.last7Days.map((x, i) => h('div', { class: 'st-bar', title: x.date + ': ' + money(x.revenue) },
      h('span', { class: 'st-bar-val' }, x.revenue ? shortMoney(x.revenue) : ''),
      h('i', { class: i === 6 ? 'today' : '', style: { height: (x.revenue ? Math.max(8, Math.round(96 * x.revenue / max)) : 4) + 'px' } }),
      h('span', null, DOW[new Date(x.date + 'T00:00:00').getDay()]))));

  const busyTables = open.filter((o) => o.tableId).length;
  const unpaid = open.filter((o) => !o.paid);
  const waiting = open.filter((o) => o.status === 'NEW' || o.status === 'PREPARING');
  const ready = open.filter((o) => o.status === 'READY');

  const alerts = [];
  if (isToday && ready.length) alerts.push(['bad', 'bell', ready.length + ' bill đã pha xong, chờ mang ra: ' + ready.map((o) => o.tableName || 'mang về').join(', ') + '.']);
  if (isToday && waiting.length >= 5) alerts.push(['warn', 'coffee', 'Quầy pha chế đang có ' + waiting.length + ' bill chờ - cân nhắc thêm người đứng quầy.']);
  if (isToday && unpaid.length) alerts.push(['warn', 'dollar', unpaid.length + ' bill chưa thu tiền, tổng ' + money(unpaid.reduce((s, o) => s + o.totalAmount, 0)) + '.']);
  if (d.cancelledCount > 0) alerts.push(['info', 'alert', d.cancelledCount + ' đơn bị hủy trong ngày - xem lý do ở mục Đơn hàng.']);
  if (!alerts.length) alerts.push(['ok', 'check', 'Mọi thứ đang ổn. Chưa có điều gì cần chú ý.']);

  const top = d.topProducts;
  const topMax = Math.max(1, ...top.map((p) => p.quantity));

  mount(host,
    h('div', { class: 'st-kpis wide' },
      kpi('dollar', 'Doanh thu ' + (isToday ? 'hôm nay' : d.date.slice(8) + '/' + d.date.slice(5, 7)), money(d.revenue),
        'Tiền mặt ' + shortMoney(d.cashRevenue) + ' · Chuyển khoản / ví ' + shortMoney(d.transferRevenue), true),
      kpi('bag', 'Bill đã thu tiền', String(d.paidCount), d.orderCount + ' đơn mở trong ngày'),
      kpi('grid', 'Bàn đang có khách', String(busyTables), open.length + ' bill đang mở'),
      kpi('coffee', 'Đang chờ pha', String(waiting.length), ready.length + ' bill xong chờ mang ra'),
      kpi('alert', 'Đơn bị hủy', String(d.cancelledCount), d.completedCount + ' đơn hoàn tất')),
    h('div', { class: 'card' }, h('div', { class: 'st-card-row' }, h('strong', null, 'Doanh thu 7 ngày'), h('span', { class: 'note' }, money(total7))), bars),
    h('div', { class: 'card' }, h('div', { class: 'st-card-row' }, h('strong', null, 'Món bán chạy'), h('span', { class: 'note' }, 'trong ngày')),
      top.length ? top.map((p, i) => h('div', { class: 'st-top' },
        h('span', { class: 'st-rank' }, String(i + 1)),
        h('div', { class: 'grow' }, h('strong', null, p.productName),
          h('div', { class: 'st-meter' }, h('i', { style: { width: Math.max(4, Math.round(100 * p.quantity / topMax)) + '%' } }))),
        h('span', { class: 'note' }, p.quantity + ' ly · ' + shortMoney(p.revenue))))
        : h('p', { class: 'note' }, 'Chưa có bill nào thu tiền trong ngày.')),
    h('div', { class: 'card' }, h('strong', null, 'Cần chú ý'),
      alerts.map(([kind, ic, msg]) => h('div', { class: 'st-alert ' + kind }, icon(ic, 18), h('span', null, msg)))));
}
