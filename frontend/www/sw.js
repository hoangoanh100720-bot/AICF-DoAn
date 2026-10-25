/* =============================================================================
   SERVICE WORKER - thành phần giúp web trở thành PWA:
     1. Cache "vỏ app" (HTML/CSS/JS/ảnh) -> mở app được cả khi mất mạng.
     2. Nhận thông báo đẩy (Web Push từ Firebase Cloud Messaging) khi app đang đóng.
   Chạy ở luồng riêng của trình duyệt, không truy cập được DOM.
   Đổi nội dung file trong danh sách cache -> tăng CACHE_VERSION để người dùng nhận bản mới.
   ============================================================================= */

const CACHE_VERSION = 'aicf-ban-hang-v2';

/** Các file "vỏ app" tải sẵn ngay khi cài service worker. */
const APP_SHELL = [
  './',
  './index.html',
  './manifest.webmanifest',
  './css/style.css',
  './css/staff.css',
  './css/app.css',
  './fonts/BeVietnamPro-Regular.ttf',
  './fonts/BeVietnamPro-SemiBold.ttf',
  './fonts/BeVietnamPro-Bold.ttf',
  './icons/icon-192.png',
  './icons/icon-512.png',
  './js/app.js',
  './js/config.js',
  './js/core/dom.js',
  './js/core/format.js',
  './js/core/http.js',
  './js/core/router.js',
  './js/core/store.js',
  './js/data/api.js',
  './js/data/cart.js',
  './js/data/local-db.js',
  './js/data/repositories.js',
  './js/data/session.js',
  './js/firebase/firebase-config.js',
  './js/firebase/firebase.js',
  './js/firebase/push.js',
  './js/pages/bill.js',
  './js/pages/chat.js',
  './js/pages/dashboard.js',
  './js/pages/history.js',
  './js/pages/kitchen.js',
  './js/pages/login.js',
  './js/pages/manage.js',
  './js/pages/more.js',
  './js/pages/notifications.js',
  './js/pages/payment.js',
  './js/pages/picker.js',
  './js/pages/settings.js',
  './js/pages/staff.js',
  './js/pages/tables.js',
  './js/ui/components.js',
  './js/ui/crud-page.js',
  './js/ui/icons.js',
];

// Cài đặt: tải và lưu vỏ app vào cache
self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(CACHE_VERSION).then((c) => c.addAll(APP_SHELL)).then(() => self.skipWaiting()));
});

// Kích hoạt: xóa cache của phiên bản cũ
self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE_VERSION).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  );
});

/**
 * Chiến lược tải:
 *  - /api/...: luôn đi mạng (dữ liệu phải mới; mất mạng thì app tự dùng dữ liệu đã lưu trong SQLite/LocalDb)
 *  - file tĩnh: "network first" -> có mạng lấy bản mới và cập nhật cache, mất mạng dùng bản trong cache
 */
self.addEventListener('fetch', (event) => {
  const req = event.request;
  const url = new URL(req.url);
  if (req.method !== 'GET' || url.origin !== self.location.origin || url.pathname.startsWith('/api/')) {
    return;
  }
  event.respondWith(
    fetch(req)
      .then((res) => {
        if (res.ok) {
          const copy = res.clone();
          caches.open(CACHE_VERSION).then((c) => c.put(req, copy));
        }
        return res;
      })
      .catch(() => caches.match(req).then((hit) => hit || caches.match('./index.html'))),
  );
});

/**
 * Nhận Web Push từ Firebase Cloud Messaging. Dữ liệu FCM gửi tới có dạng:
 *   { "notification": { "title": "...", "body": "..." }, "data": { "orderId": "12" }, ... }
 */
self.addEventListener('push', (event) => {
  let payload = {};
  try {
    payload = event.data ? event.data.json() : {};
  } catch {
    payload = { notification: { title: 'AICF Coffee', body: event.data ? event.data.text() : '' } };
  }
  const n = payload.notification || {};
  const data = payload.data || {};
  event.waitUntil(
    self.registration.showNotification(n.title || 'AICF Coffee', {
      body: n.body || '',
      icon: 'icons/icon-192.png',
      badge: 'icons/icon-192.png',
      data,
      tag: data.orderId ? 'order-' + data.orderId : undefined,
    }),
  );
});

// Bấm vào thông báo: mở app (hoặc chuyển tới tab đang mở) và mở bill liên quan
self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const orderId = event.notification.data && event.notification.data.orderId;
  const target = new URL(orderId ? './#/history?order=' + orderId : './#/notifications', self.registration.scope).href;
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((list) => {
      for (const c of list) {
        if ('focus' in c) {
          c.navigate(target);
          return c.focus();
        }
      }
      return self.clients.openWindow(target);
    }),
  );
});
