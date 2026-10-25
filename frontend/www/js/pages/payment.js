/**
 * THANH TOÁN 1 BILL (hộp thoại tràn màn hình) - giống màn Thanh toán của app nhân viên AICF:
 * số tiền to ở trên, chọn cách trả (Tiền mặt / Chuyển khoản / Ví điện tử), tiền mặt có ô "tiền khách đưa"
 * + nút nhanh (Đủ tiền, 50.000, 100.000...) và tự tính tiền thối.
 * Mẫu Strategy: mỗi cách trả là 1 đối tượng tự vẽ phần nhập riêng và tự tạo dữ liệu gửi lên (POST /api/orders/{id}/pay).
 * Thêm cách trả mới = thêm 1 phần tử vào METHODS, không sửa luồng chung.
 */
import { h, mount } from '../core/dom.js';
import { money, parseMoney } from '../core/format.js';
import { orderRepo } from '../data/repositories.js';
import { sheet, toast } from '../ui/components.js';
import { icon } from '../ui/icons.js';

const vnd = (v) => new Intl.NumberFormat('vi-VN').format(v);

const METHODS = [
  {
    id: 'CASH',
    label: 'Tiền mặt',
    icon: 'dollar',
    pane(total) {
      const received = h('input', { type: 'text', inputmode: 'numeric', placeholder: 'Tiền khách đưa (bỏ trống nếu đưa đủ)', autocomplete: 'off', 'data-field': 'received' });
      const change = h('p', { class: 'st-change' });
      const onInput = () => {
        const r = parseMoney(received.value);
        change.className = 'st-change';
        if (!r) change.textContent = '';
        else if (r < 0) { change.textContent = 'Số tiền chưa đúng (ví dụ: 200000 hoặc 200k)'; change.classList.add('bad'); }
        else if (r >= total) { change.textContent = 'Tiền thối: ' + money(r - total); change.classList.add('ok'); }
        else { change.textContent = 'Còn thiếu: ' + money(total - r); change.classList.add('bad'); }
      };
      received.addEventListener('input', onInput);
      const set = (v) => { received.value = String(v); onInput(); };
      const quick = h('div', { class: 'st-chips' },
        h('button', { class: 'st-chip', type: 'button', onclick: () => set(total) }, 'Đủ tiền'),
        [50000, 100000, 200000, 500000].filter((v) => v >= total).map((v) => h('button', { class: 'st-chip', type: 'button', onclick: () => set(v) }, vnd(v))));
      return {
        el: h('div', { class: 'st-pane' }, h('p', { class: 'note' }, 'Tiền khách đưa'), received, quick, change),
        body() {
          const r = parseMoney(received.value);
          if (r < 0) throw new Error('Số tiền khách đưa chưa đúng (ví dụ: 200000, 200.000 hoặc 200k).');
          if (r > 0 && r < total) throw new Error('Tiền khách đưa chưa đủ.');
          return { method: 'CASH', amountReceived: r > 0 ? r : null };
        },
      };
    },
  },
  {
    id: 'BANK_TRANSFER',
    label: 'Chuyển khoản',
    icon: 'card',
    pane: () => ({
      el: h('div', { class: 'st-pane' }, h('p', { class: 'note' },
        'Khách chuyển khoản vào tài khoản của quán. Mở app ngân hàng, thấy tiền về đúng số tiền rồi mới bấm xác nhận.')),
      body: () => ({ method: 'BANK_TRANSFER', amountReceived: null }),
    }),
  },
  {
    id: 'E_WALLET',
    label: 'Ví điện tử',
    icon: 'phone',
    pane: () => ({
      el: h('div', { class: 'st-pane' }, h('p', { class: 'note' }, 'Khách quét mã MoMo / ZaloPay của quán. Kiểm tra tiền đã về rồi bấm xác nhận.')),
      body: () => ({ method: 'E_WALLET', amountReceived: null }),
    }),
  },
];

/** Mở hộp thanh toán. onPaid(orderMới) gọi sau khi thu tiền thành công. */
export function openPayment(order, onPaid) {
  const total = order.totalAmount;
  const sh = sheet({ title: 'Thanh toán', full: true });
  const panes = new Map(METHODS.map((m) => [m.id, m.pane(total)]));
  const host = h('div');
  let current = 'CASH';
  const buttons = METHODS.map((m) => h('button', {
    class: 'st-seg-btn', type: 'button', 'data-method': m.id, onclick: () => select(m.id),
  }, icon(m.icon, 16), m.label));
  function select(id) {
    current = id;
    buttons.forEach((b) => b.classList.toggle('on', b.dataset.method === id));
    host.replaceChildren(panes.get(id).el);
  }

  sh.body.append(
    h('p', { class: 'note' }, 'Đơn ' + order.code + ' · ' + (order.tableName || 'Mang đi') + ' · ' + order.itemCount + ' món'),
    h('div', { class: 'st-pay-total' }, money(total)),
    h('div', { class: 'st-seg wrap' }, buttons), host);
  const payBtn = h('button', { class: 'btn btn-ok btn-lg btn-block', type: 'button', 'data-act': 'do-pay' }, 'Xác nhận đã thu ' + money(total));
  sh.setFooter(payBtn);
  select('CASH');

  payBtn.addEventListener('click', async () => {
    let body;
    try { body = panes.get(current).body(); } catch (e) { toast(e.message, 'error'); return; }
    payBtn.disabled = true;
    try {
      const paid = await orderRepo.pay(order.id, body.method, body.amountReceived);
      showDone(paid);
      if (onPaid) onPaid(paid);
    } catch (e) {
      toast(e.message, 'error');
      payBtn.disabled = false;
    }
  });

  /** Màn "Đã thu tiền": số tiền thối thật to để nhân viên đếm trả khách. */
  function showDone(paid) {
    sh.setTitle('Đã thu tiền');
    mount(sh.body, h('div', { class: 'att-card card' },
      icon('check', 40),
      h('div', { class: 'st-pay-total ok' }, money(paid.totalAmount)),
      paid.changeAmount ? h('p', { class: 'st-change ok' }, 'Tiền thối cho khách: ' + money(paid.changeAmount)) : null,
      h('p', { class: 'note' }, paid.status === 'COMPLETED' ? 'Bill đã hoàn tất, bàn trống trở lại.' : 'Đã thu tiền trước. Bill còn mở tới khi mang đồ ra cho khách.')));
    sh.setFooter(h('button', { class: 'btn btn-primary btn-lg btn-block', type: 'button', 'data-act': 'pay-done', onclick: () => sh.close() }, 'Xong'));
  }
}
