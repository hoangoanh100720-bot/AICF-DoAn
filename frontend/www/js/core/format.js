/** ĐỊNH DẠNG hiển thị dùng chung: tiền, ngày giờ, nhãn trạng thái / vai trò (khớp với backend). */

const moneyFmt = new Intl.NumberFormat('vi-VN');

/** 29000 -> "29.000đ" (giống app AICF) */
export const money = (v) => moneyFmt.format(Math.round(Number(v) || 0)) + 'đ';

/** 1250000 -> "1,3tr", 85000 -> "85k" (số ngắn trên biểu đồ) */
export function shortMoney(v) {
  const a = Math.abs(v);
  const trim = (x) => String(Math.round(x * 10) / 10).replace('.', ',');
  return a >= 1e6 ? trim(v / 1e6) + 'tr' : a >= 1e3 ? trim(v / 1e3) + 'k' : String(v);
}

/** Đọc số tiền người dùng gõ: "200000", "200.000", "200k", "1,5tr" -> số đồng; rỗng -> 0; sai -> -1. */
export function parseMoney(text) {
  let s = String(text ?? '').trim().toLowerCase().replace(/\s+/g, '').replace(/(đồng|đ|vnd|₫)$/, '');
  if (!s) return 0;
  let mult = 1;
  if (s.endsWith('tr')) { mult = 1e6; s = s.slice(0, -2); } else if (s.endsWith('k')) { mult = 1e3; s = s.slice(0, -1); }
  if (!/^\d[\d.,]*$/.test(s)) return -1;
  s = mult > 1 ? s.replace(',', '.') : s.replace(/[.,]/g, '');
  const v = Number(s) * mult;
  return Number.isFinite(v) ? Math.round(v) : -1;
}

/** "2026-10-15T09:30:00" -> "09:30" */
export const hm = (iso) => (/T(\d{2}:\d{2})/.exec(iso || '') || [])[1] || '';

/** "2026-10-15T09:30:00" -> "09:30" nếu hôm nay, "09:30 15/10" nếu ngày khác */
export function dateTime(iso) {
  if (!iso) return '';
  const day = iso.slice(0, 10);
  return day === todayIso() ? hm(iso) : hm(iso) + ' ' + day.slice(8, 10) + '/' + day.slice(5, 7);
}

/** Ngày hôm nay dạng YYYY-MM-DD theo giờ máy (dùng cho tham số API). */
export function todayIso() {
  const d = new Date();
  return new Date(d.getTime() - d.getTimezoneOffset() * 60_000).toISOString().slice(0, 10);
}

/** Số phút đã trôi qua từ thời điểm máy chủ trả về (giờ địa phương). */
export function minutesSince(iso) {
  const t = Date.parse(iso || '');
  return Number.isFinite(t) ? Math.max(0, Math.floor((Date.now() - t) / 60000)) : -1;
}

export const duration = (m) => (m < 60 ? m + ' phút' : Math.floor(m / 60) + 'g' + String(m % 60).padStart(2, '0'));

/** Trạng thái đơn: [nhãn, tông màu của tem st-pill]. Khớp enum OrderStatus ở backend. */
export const STATUS = {
  NEW: ['Chờ pha', 'bad'],
  PREPARING: ['Đang pha', 'warn'],
  READY: ['Xong · chờ mang ra', 'info'],
  SERVED: ['Đã phục vụ', 'ok'],
  COMPLETED: ['Hoàn tất', 'ok'],
  CANCELLED: ['Đã hủy', ''],
};

/** Nút "bước tiếp theo" của từng trạng thái (màn pha chế / bill). */
export const NEXT_STEP = {
  NEW: { status: 'PREPARING', label: 'Bắt đầu pha', kind: 'primary' },
  PREPARING: { status: 'READY', label: 'Xong', kind: 'ok' },
  READY: { status: 'SERVED', label: 'Đã mang ra', kind: 'ok' },
};

export const PAYMENT_LABEL = { CASH: 'Tiền mặt', BANK_TRANSFER: 'Chuyển khoản', E_WALLET: 'Ví điện tử' };
export const ROLE_LABEL = { OWNER: 'Chủ quán', MANAGER: 'Quản lý', STAFF: 'Nhân viên bán hàng' };
export const isManager = (user) => !!user && (user.role === 'OWNER' || user.role === 'MANAGER');

/** "Nguyễn Thị Lan" -> "TL" (chữ trên vòng tròn ảnh đại diện) */
export const initials = (name) => String(name || '?').trim().split(/\s+/).slice(-2).map((w) => w[0]).join('').toUpperCase();
