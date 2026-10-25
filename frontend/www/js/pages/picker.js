/**
 * MÀN CHỌN MÓN (hộp thoại tràn màn hình) - giống màn "Chọn món" của app nhân viên AICF:
 * lưới món có ảnh, tìm kiếm, lọc theo nhóm; chạm vào món để thêm (góc ảnh hiện ×số lượng);
 * nút dưới cùng mở "Món đã chọn" để sửa số lượng, ghi chú (ít đá, ít ngọt...) rồi gửi.
 *
 * Món đang chọn được lưu xuống LocalDb (SQLite trên Android) qua data/cart.js: tắt app giữa chừng,
 * mở lại cùng bàn thì vẫn còn. Thực đơn lấy qua productRepo.menu(): mất mạng thì dùng bản sao đã lưu.
 */
import { h } from '../core/dom.js';
import { money } from '../core/format.js';
import { cart } from '../data/cart.js';
import { productRepo } from '../data/repositories.js';
import { emptyState, productThumb, sheet, toast, toastError } from '../ui/components.js';
import { icon } from '../ui/icons.js';

/**
 * @param {object} o
 * @param {string} o.target    đích của phiếu nháp, vd "table:3" / "order:12" / "takeaway"
 * @param {string} o.title     tiêu đề, vd "Bàn 03 · Chọn món"
 * @param {string} o.okText    chữ nút gửi, vd "Gửi pha chế"
 * @param {(items: object[]) => Promise<any>} o.onSubmit  gửi lên máy chủ; ném lỗi thì giữ nguyên phiếu
 * @param {() => void} [o.onCancel]  đóng mà chưa gửi
 */
export async function openPicker({ target, title, okText, onSubmit, onCancel }) {
  let menu;
  try {
    menu = await productRepo.menu();
  } catch (err) {
    toastError(err);
    return;
  }
  let lines = await cart.begin(target);
  if (lines.length) toast('Còn ' + cart.totals(lines).count + ' món chọn dở lần trước', 'info');

  const products = menu.items;
  const cats = [...new Set(products.map((p) => p.categoryName))];
  let activeCat = null;
  let query = '';
  let submitted = false;
  const badges = new Map();
  const grid = h('div', { class: 'st-products' });
  const chips = h('div', { class: 'st-chips scroll', role: 'toolbar', 'aria-label': 'Nhóm món' });
  const search = h('input', { type: 'search', placeholder: 'Tìm món…', 'aria-label': 'Tìm món', autocomplete: 'off', id: 'pick-search' });
  const cartBtn = h('button', { class: 'btn btn-primary btn-lg btn-block', type: 'button', 'data-act': 'view-cart', disabled: true });

  const sh = sheet({
    title, full: true,
    body: h('div', null,
      menu.offline ? h('p', { class: 'st-banner bad' }, 'Đang mất mạng - thực đơn là bản đã lưu, gửi đơn khi có mạng lại.') : null,
      h('div', { class: 'search st-search' }, h('span', { class: 'search-icon' }, icon('search', 18)), search), chips, grid),
    footer: cartBtn,
    onClose: () => { if (!submitted && onCancel) onCancel(); },
  });

  const refreshCart = () => {
    const { count, sum } = cart.totals(lines);
    cartBtn.disabled = count === 0;
    cartBtn.textContent = count === 0 ? 'Chạm vào món để thêm' : 'Xem món đã chọn · ' + count + ' món · ' + money(sum);
    badges.forEach((b, id) => {
      const n = lines.find((l) => l.productId === id)?.quantity || 0;
      b.hidden = !n;
      b.textContent = n ? '×' + n : '';
    });
  };

  const renderChips = () => chips.replaceChildren(...[null, ...cats].map((c) => h('button', {
    class: 'st-chip' + (activeCat === c ? ' on' : ''), type: 'button', 'aria-pressed': String(activeCat === c),
    onclick: () => { activeCat = c; renderChips(); renderGrid(); },
  }, c || 'Tất cả')));

  const renderGrid = () => {
    badges.clear();
    const q = query.trim().toLowerCase();
    const list = products.filter((p) => (!activeCat || p.categoryName === activeCat) && (!q || p.name.toLowerCase().includes(q)));
    grid.replaceChildren(...(list.length ? list.map((p) => {
      const badge = h('span', { class: 'st-badge', hidden: true });
      badges.set(p.id, badge);
      return h('button', {
        class: 'st-prod' + (p.available ? '' : ' sold-out'), type: 'button', 'data-product': p.name, disabled: !p.available,
        onclick: async () => { lines = await cart.add(p); refreshCart(); },
      }, h('div', { class: 'st-prod-img' }, productThumb(p), badge,
        p.bestSeller ? h('span', { class: 'st-hot' }, 'Bán chạy') : null),
      h('strong', null, p.name), h('span', { class: 'st-price' }, p.available ? money(p.price) : 'Tạm hết'));
    }) : [emptyState('search', 'Không có món phù hợp')]));
    refreshCart();
  };

  search.addEventListener('input', () => { query = search.value; renderGrid(); });
  cartBtn.addEventListener('click', () => openCart());
  renderChips();
  renderGrid();

  /** "Món đã chọn": sửa số lượng, ghi chú, rồi gửi. */
  function openCart() {
    const body = h('div', { class: 'st-cart' });
    const send = h('button', { class: 'btn btn-ok btn-lg btn-block', type: 'button', 'data-act': 'send' });
    const cs = sheet({ title: 'Món đã chọn', full: true, body, footer: send });
    const draw = () => {
      const { count, sum } = cart.totals(lines);
      if (!count) { cs.close(); return; }
      body.replaceChildren(...lines.map((l) => h('div', { class: 'st-cart-line', 'data-line': l.name },
        h('div', { class: 'st-cart-top' }, h('strong', null, l.name), h('span', { class: 'st-price' }, money(l.price * l.quantity))),
        h('div', { class: 'st-cart-ctl' },
          h('div', { class: 'stepper' },
            h('button', { type: 'button', 'aria-label': 'Bớt ' + l.name, onclick: async () => { lines = await cart.setQuantity(l.productId, l.quantity - 1); refreshCart(); draw(); } }, '−'),
            h('output', null, String(l.quantity)),
            h('button', { type: 'button', 'aria-label': 'Thêm ' + l.name, onclick: async () => { lines = await cart.setQuantity(l.productId, l.quantity + 1); refreshCart(); draw(); } }, '+')),
          h('input', {
            class: 'st-note-input', type: 'text', maxlength: 100, placeholder: 'Ghi chú (ít đá, ít ngọt…)', value: l.note || '',
            'aria-label': 'Ghi chú ' + l.name,
            onchange: (e) => { l.note = e.target.value.trim(); cart.setNote(l.productId, l.note); },
          })))));
      send.textContent = okText + ' · ' + count + ' món · ' + money(sum);
    };
    send.addEventListener('click', async () => {
      send.disabled = true;
      try {
        await onSubmit(cart.toRequest(lines));
        submitted = true;
        await cart.clear();
        cs.close();
        sh.close();
      } catch (err) {
        toastError(err);   // giữ nguyên phiếu để gửi lại
        send.disabled = false;
      }
    });
    draw();
  }
}
