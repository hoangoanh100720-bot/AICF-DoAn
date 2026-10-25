/**
 * HỘP THƯ THÔNG BÁO (GET /api/notifications) - kiểu hộp thư của Trợ lý AICF: thẻ có viền màu bên trái,
 * thẻ chưa đọc nền sáng hơn. Thông báo của quán: đơn mới / gọi thêm (cho quầy pha chế), đồ đã xong (cho người
 * mở bill), đơn bị hủy (cho chủ quán / quản lý). Bấm 1 thông báo: đánh dấu đã đọc + mở bill liên quan.
 * Thông báo luôn được lưu ở máy chủ nên vẫn xem được dù chưa bật Firebase / đã tắt thông báo đẩy.
 */
import { h, mount } from '../core/dom.js';
import { dateTime } from '../core/format.js';
import { navigate } from '../core/router.js';
import { appState } from '../core/store.js';
import { notificationRepo } from '../data/repositories.js';
import { emptyState, errorState, spinner, toastError, toolbar } from '../ui/components.js';

/** Viền màu theo loại thông báo (đọc từ tiêu đề). */
const sevOf = (n) => (/hủy/i.test(n.title) ? 'BAD' : /xong/i.test(n.title) ? 'GOOD' : /mới|thêm/i.test(n.title) ? 'WARN' : 'INFO');

export function render() {
  const box = h('div', null, spinner());
  const readAll = h('button', { class: 'btn btn-ghost btn-sm', type: 'button', hidden: true, onclick: async () => {
    try {
      await notificationRepo.readAll();
      appState.set({ unread: 0 });
      load();
    } catch (err) { toastError(err); }
  } }, 'Đã đọc hết');

  async function load() {
    let items;
    try {
      items = await notificationRepo.list();
    } catch (err) {
      mount(box, errorState(err, load));
      return;
    }
    readAll.hidden = !items.some((n) => !n.read);
    mount(box, items.length ? h('div', { class: 'as-inbox' }, items.map((n) => h('button', {
      class: 'as-note sev-' + sevOf(n) + (n.read ? ' read' : ' unread'), type: 'button',
      onclick: async () => {
        if (!n.read) {
          await notificationRepo.read(n.id).catch(() => {});
          appState.set({ unread: Math.max(0, appState.get().unread - 1) });
        }
        if (n.orderId) navigate('/history?order=' + n.orderId);
        else load();
      },
    }, h('span', { class: 'as-ico sev-' + sevOf(n) }, sevOf(n) === 'BAD' ? '!' : sevOf(n) === 'GOOD' ? '✓' : '•'),
    h('span', { class: 'as-note-main' },
      h('span', { class: 'as-note-top' }, h('strong', { class: 'as-title' }, n.title), h('span', { class: 'as-when' }, dateTime(n.createdAt))),
      h('span', { class: 'as-preview' }, n.body)))))
      : emptyState('bell', 'Chưa có thông báo', 'Khi có đơn mới, đồ đã pha xong hay đơn bị hủy, thông báo sẽ hiện ở đây.'));
  }
  load();
  return h('section', { 'aria-label': 'Thông báo' }, toolbar('Thông báo', readAll), box);
}
