/**
 * CẤU HÌNH CHUNG CỦA APP.
 *
 * Địa chỉ máy chủ (backend):
 *  - Chạy PWA do chính backend phục vụ (http://localhost:8090): để trống = gọi API cùng nguồn.
 *  - Chạy trong app Android (Capacitor): giao diện nằm trong máy nên phải biết máy chủ ở đâu.
 *    Mặc định 10.0.2.2 = "máy tính đang chạy máy ảo Android". Điện thoại thật: đổi ở màn hình Đăng nhập
 *    hoặc Tài khoản thành IP máy tính trong cùng Wi-Fi, vd http://192.168.1.10:8090
 */

/** true khi đang chạy bên trong app Capacitor (Android / iOS). */
export const IS_NATIVE = !!(window.Capacitor && typeof window.Capacitor.isNativePlatform === 'function'
  && window.Capacitor.isNativePlatform());

/** Nền tảng gửi kèm khi đăng ký thông báo đẩy. */
export const PLATFORM = IS_NATIVE ? (window.Capacitor.getPlatform() === 'ios' ? 'IOS' : 'ANDROID') : 'WEB';

export const DEFAULT_SERVER_URL = IS_NATIVE ? 'http://10.0.2.2:8090' : '';

export const APP_NAME = 'AICF Coffee';

/** Bao lâu kiểm tra số thông báo chưa đọc 1 lần (ms) - dự phòng khi chưa cấu hình Firebase. */
export const UNREAD_POLL_MS = 30_000;

/** Bao lâu làm mới sơ đồ bàn / màn pha chế (ms) - đơn do nhân viên khác tạo hiện ra sau tối đa chừng này. */
export const BOARD_REFRESH_MS = 5_000;
