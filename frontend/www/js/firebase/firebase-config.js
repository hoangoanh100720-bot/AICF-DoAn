/**
 * CẤU HÌNH FIREBASE PHÍA APP - điền sau khi tạo dự án trên https://console.firebase.google.com
 *
 * 1. Project settings > General > "Your apps" > thêm app Web (</>) -> chép đối tượng firebaseConfig vào dưới.
 * 2. Authentication > Sign-in method > bật "Google". Thêm tên miền chạy app vào "Authorized domains"
 *    (localhost có sẵn; nếu mở bằng IP máy tính thì thêm IP đó).
 * 3. Project settings > Cloud Messaging > Web Push certificates > "Generate key pair" -> chép vào VAPID_KEY.
 * 4. Backend: Project settings > Service accounts > "Generate new private key" -> lưu thành
 *    backend/data/firebase-service-account.json (xem README).
 * 5. App Android: thêm app Android (package vn.aicf.mobile) -> tải google-services.json vào
 *    frontend/android/app/ rồi đặt NATIVE_PUSH_ENABLED = true.
 *
 * Để TRỐNG apiKey: app vẫn chạy đầy đủ, chỉ ẩn nút "Đăng nhập Google" và thông báo đẩy
 * (thông báo vẫn có trong hộp thư trong app).
 * Các khóa ở đây là khóa CÔNG KHAI phía client (Firebase cho phép đưa vào app), không phải bí mật.
 */
export const firebaseConfig = {
  apiKey: 'AIzaSyBJWLgWAOQgMUS2v1Q_Sdy0zLbG4eAnSNc',
  authDomain: 'aicf-coffee-mobile.firebaseapp.com',
  projectId: 'aicf-coffee-mobile',
  storageBucket: 'aicf-coffee-mobile.firebasestorage.app',
  messagingSenderId: '710716423357',
  appId: '1:710716423357:web:d865fe5fffcf3002ee4bd4',
};

/** Khóa Web Push (VAPID) để nhận thông báo trên PWA. */
export const VAPID_KEY = 'BGhnhQBmEX6HBwkChaBmMul37xnTuwzXrOmAJa--5H-9oZZkkeH_VcaRgQchodozbC7mZMipjvnNo6DF9rngvQc';

/**
 * Bật thông báo đẩy trong app Android. CHỈ bật sau khi đã có google-services.json,
 * nếu không app Android sẽ lỗi khi đăng ký nhận thông báo.
 */
export const NATIVE_PUSH_ENABLED = false;

/** Phiên bản Firebase JS SDK tải từ CDN chính thức của Google. */
export const FIREBASE_SDK_VERSION = '10.14.1';
