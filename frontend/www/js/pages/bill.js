/**
 * BILL CỦA 1 BÀN / 1 ĐƠN MANG ĐI (hộp thoại tràn màn hình) - giống "bill bàn" trong app nhân viên AICF.
 *   - Bàn trống / đơn mang đi mới: mở thẳng màn chọn món; gửi xong thì tạo bill (POST /api/orders).
 *   - Bill đang mở: danh sách món + tổng, các nút Thêm món (POST /{id}/items), bước tiếp theo
 *     (Bắt đầu pha / Xong / Đã mang ra - PATCH /{id}/status), Hủy đơn, Thu tiền (POST /{id}/pay).
 *   - Bớt món gọi nhầm khi bill còn "Chờ pha" (DELETE /{id}/items/{itemId}).
 * Bill tự làm mới mỗi 5 giây (nhân viên khác có thể vừa bấm "Xong" ở quầy pha chế).
 */
import { BOARD_REFRESH_MS } from '../config.js';
import { h, mount } from '../core/dom.js';
import { hm, isManager, money, NEXT_STEP, PAYMENT_LABEL } from '../core/format.js';
import { appState } from '../core/store.js';
import { orderRepo } from '../data/repositories.js';
import { confirmBox, emptyState, poll, sheet, spinner, statusPill, toast, toastError } from '../ui/components.js';
import { icon } from '../ui/icons.js';
import { openPayment } from './payment.js';
import { openPicker } from './picker.js';

/**
 * @param {object} o
 * @param {number|null} [o.orderId]   bill đang mở (null = bill mới)
 * @param {number|null} [o.tableId]   bàn (null = mang đi)
 * @param {string} o.title            vd "Bàn 03" / "Đơn mang về"
 * @param {() => void} [o.onClosed]   gọi khi đóng (vd làm mới sơ đồ bàn)
 */
export function openBill({ orderId = null, tableId = null, title, onClosed }) {
  const st = { orderId, order: null, sig: '' };
  let refresher = null;
  const sh = sheet({ title, full: true, body: spinner(), onClose: () => { refresher?.stop(); onClosed?.(); } });

  const reload = async () => {
    if (!st.orderId) return render();
    const o = await orderRepo.get(st.orderId);
    const sig = JSON.stringify(o);
    if (sig !== st.sig) { st.sig = sig; st.order = o; render(); }
  };
  const after = async () => { st.sig = ''; await reload().catch(toastError); };

  if (st.orderId) refresher = poll(reload, BOARD_REFRESH_MS);
  else {
    render();
    setTimeout(() => addItems(true), 0);   // bàn trống: vào thẳng màn chọn món
  }

  /** Chạy 1 thao tác: khóa nút -> gọi API -> báo -> làm mới bill. */
  const act = (btn, run, success) => async () => {
    btn.disabled = true;
    try {
      await run();
      if (success) toast(success, 'ok');
    } catch (err) {
      toastError(err);
    }
    btn.disabled = false;
    await after();
  };

  function render() {
    const o = st.order;
    if (!o) {
      mount(sh.body, emptyState('grid', tableId ? 'Bàn đang trống' : 'Đơn mang về mới', 'Bấm "Gọi món" để chọn món cho khách.'));
      sh.setFooter(h('button', { class: 'btn btn-primary btn-lg btn-block', type: 'button', 'data-act': 'add', onclick: () => addItems() },
        icon('plus', 18), 'Gọi món'));
      return;
    }
    const open = o.status !== 'COMPLETED' && o.status !== 'CANCELLED';
    const canRemove = open && o.status === 'NEW' && !o.paid && o.items.length > 1;
    sh.setTitle((o.tableName || 'Mang về') + ' · ' + o.code.slice(-4));
    mount(sh.body,
      h('div', { class: 'st-order-head' }, statusPill(o.status),
        h('span', { class: 'note' }, 'Mã ' + o.code + ' · mở lúc ' + hm(o.createdAt) + ' · ' + o.createdByName)),
      o.note ? h('p', { class: 'st-note-line' }, '📝 ' + o.note) : null,
      o.status === 'CANCELLED' ? h('div', { class: 'st-banner bad' }, h('p', null, 'Đã hủy: ' + (o.cancelReason || ''))) : null,
      h('div', { class: 'st-items' }, o.items.map((i) => h('div', { class: 'st-item', 'data-item': i.productName },
        h('div', { class: 'st-item-main' },
          h('strong', null, i.quantity + ' × ' + i.productName),
          i.note ? h('span', { class: 'st-note' }, '“' + i.note + '”') : null),
        h('div', { class: 'st-item-side' },
          h('span', { class: 'st-price' }, money(i.lineTotal)),
          canRemove ? removeBtn(o, i) : null)))),
      h('div', { class: 'st-line total' }, h('span', null, 'Tổng bill'), h('strong', null, money(o.totalAmount))),
      o.paid ? h('p', { class: 'ok-text' }, '✓ Đã thu ' + PAYMENT_LABEL[o.paymentMethod] + ' lúc ' + hm(o.paidAt)
        + (o.changeAmount ? ' · thối ' + money(o.changeAmount) : '')) : null);
    sh.setFooter(open ? openFooter(o) : h('p', { class: 'note' }, o.status === 'COMPLETED' ? 'Bill đã hoàn tất.' : 'Bill đã hủy.'));
  }

  function removeBtn(o, i) {
    const b = h('button', { class: 'btn btn-ghost danger btn-sm', type: 'button', 'aria-label': 'Bớt món ' + i.productName }, icon('trash', 16));
    b.addEventListener('click', async () => {
      if (!(await confirmBox({ title: 'Bớt món', message: 'Bớt "' + i.quantity + ' × ' + i.productName + '" khỏi bill?', okText: 'Bớt món', danger: true }))) return;
      act(b, () => orderRepo.removeItem(o.id, i.id), 'Đã bớt món')();
    });
    return b;
  }

  function openFooter(o) {
    const user = appState.get().user;
    const next = NEXT_STEP[o.status];
    const nextBtn = next ? h('button', { class: 'btn btn-' + next.kind, type: 'button', 'data-act': 'next' }, next.label) : null;
    if (nextBtn) nextBtn.addEventListener('click', act(nextBtn, () => orderRepo.setStatus(o.id, next.status)));
    // Nhân viên chỉ hủy được bill chưa pha; quản lý / chủ quán hủy được mọi bill chưa thu tiền (luật ở backend)
    const canCancel = !o.paid && (o.status === 'NEW' || isManager(user));
    const cancelBtn = canCancel ? h('button', { class: 'btn btn-ghost danger', type: 'button', 'data-act': 'cancel' }, 'Hủy đơn') : null;
    cancelBtn?.addEventListener('click', async () => {
      if (!(await confirmBox({ title: 'Hủy đơn', message: 'Hủy cả đơn ' + o.code + '?', okText: 'Hủy đơn', danger: true }))) return;
      act(cancelBtn, () => orderRepo.setStatus(o.id, 'CANCELLED', 'Hủy bởi ' + user.fullName), 'Đã hủy đơn')();
    });
    return h('div', { class: 'st-foot-stack' },
      h('div', { class: 'st-actions' },
        o.paid ? null : h('button', { class: 'btn btn-ghost', type: 'button', 'data-act': 'add', onclick: () => addItems() }, icon('plus', 16), 'Thêm món'),
        nextBtn, cancelBtn),
      o.paid ? null : h('button', { class: 'btn btn-ok btn-lg btn-block', type: 'button', 'data-act': 'pay',
        onclick: () => openPayment(o, () => after()) }, 'Thu tiền ' + money(o.totalAmount)));
  }

  /** auto = vừa chạm vào bàn trống: đóng màn chọn món mà chưa chọn gì thì đóng luôn bill. */
  function addItems(auto = false) {
    openPicker({
      target: st.orderId ? 'order:' + st.orderId : tableId ? 'table:' + tableId : 'takeaway',
      title: title + ' · Chọn món',
      okText: st.orderId ? 'Thêm vào bill' : 'Gửi pha chế',
      onCancel: auto ? () => { if (!st.order) sh.close(); } : null,
      onSubmit: async (items) => {
        const o = st.orderId
          ? await orderRepo.addItems(st.orderId, items)
          : await orderRepo.create({ orderType: tableId ? 'DINE_IN' : 'TAKEAWAY', tableId, items });
        const isNew = !st.orderId;
        st.orderId = o.id;
        st.order = o;
        st.sig = JSON.stringify(o);
        render();
        if (isNew) refresher = poll(reload, BOARD_REFRESH_MS);
        toast(isNew ? 'Đã gửi pha chế' : 'Đã thêm món vào bill', 'ok');
      },
    });
  }
}
