/**
 * Đối tượng HttpClient DUY NHẤT của app, nối với phiên đăng nhập:
 * tự gắn token, máy chủ trả 401 (token hết hạn / tài khoản bị khóa) thì đăng xuất và về màn hình Đăng nhập.
 */
import { HttpClient } from '../core/http.js';
import { session } from './session.js';

export const api = new HttpClient({
  baseUrl: () => session.serverUrl(),
  getToken: () => session.token(),
  onUnauthorized: async () => {
    await session.signOut();
    location.hash = '/login?expired=1';
  },
});
