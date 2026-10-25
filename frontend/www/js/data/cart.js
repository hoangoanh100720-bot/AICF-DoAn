/**
 * MÓN ĐANG CHỌN (phiếu gọi món nháp) - lưu trong LocalDb (SQLite trên Android / localStorage trên PWA).
 * Nhân viên đang chọn món cho Bàn 03 mà app bị tắt / mất mạng / có khách gọi giữa chừng thì lần mở lại
 * vẫn còn nguyên các món đã chọn. Mỗi lúc chỉ có 1 phiếu nháp, gắn với 1 "đích" (bàn / bill / mang đi).
 * Giá trên phiếu chỉ để xem tạm; giá thật do máy chủ tính khi gửi đơn.
 */
import { localDb } from './local-db.js';

const TARGET_KEY = 'draft.target';

export const cart = {
  items: () => localDb.cartList(),

  /** Đích của phiếu nháp hiện tại, vd "table:3", "order:12", "takeaway". */
  target: () => localDb.kvGet(TARGET_KEY),

  /** Bắt đầu chọn món cho 1 đích; phiếu nháp của đích khác bị bỏ. Trả về các món còn lại (nếu cùng đích). */
  async begin(target) {
    if ((await localDb.kvGet(TARGET_KEY)) !== target) {
      await localDb.cartClear();
      await localDb.kvSet(TARGET_KEY, target);
    }
    return localDb.cartList();
  },

  /** Thêm 1 phần (đã có thì cộng số lượng, tối đa 99). */
  async add(product) {
    const existing = (await localDb.cartList()).find((i) => i.productId === product.id);
    await localDb.cartPut({
      productId: product.id,
      name: product.name,
      price: product.price,
      imageUrl: product.imageUrl,
      quantity: Math.min(99, (existing?.quantity || 0) + 1),
      note: existing?.note ?? null,
    });
    return localDb.cartList();
  },

  /** Đặt lại số lượng; về 0 thì bỏ khỏi phiếu. */
  async setQuantity(productId, quantity) {
    const item = (await localDb.cartList()).find((i) => i.productId === productId);
    if (item) {
      if (quantity <= 0) await localDb.cartRemove(productId);
      else await localDb.cartPut({ ...item, quantity: Math.min(99, quantity) });
    }
    return localDb.cartList();
  },

  async setNote(productId, note) {
    const item = (await localDb.cartList()).find((i) => i.productId === productId);
    if (item) await localDb.cartPut({ ...item, note: note || null });
  },

  async clear() {
    await localDb.cartClear();
    await localDb.kvRemove(TARGET_KEY);
  },

  /** Tạm tính và tổng số phần. */
  totals: (items) => ({
    count: items.reduce((n, i) => n + i.quantity, 0),
    sum: items.reduce((s, i) => s + i.price * i.quantity, 0),
  }),

  /** Đổi sang dữ liệu gửi API: [{ productId, quantity, note }]. */
  toRequest: (items) => items.map((i) => ({ productId: i.productId, quantity: i.quantity, note: i.note || null })),
};
