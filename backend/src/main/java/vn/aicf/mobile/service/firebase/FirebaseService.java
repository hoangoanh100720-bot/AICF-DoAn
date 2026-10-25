package vn.aicf.mobile.service.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.messaging.FirebaseMessaging;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.aicf.mobile.common.ApiException;
import vn.aicf.mobile.config.AppProperties;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Cầu nối với FIREBASE qua Firebase Admin SDK (chạy phía máy chủ, có toàn quyền với dự án Firebase).
 *
 * <p>Dùng cho 2 việc:
 * <ol>
 *   <li><b>Firebase Authentication</b>: app đăng nhập Google bằng Firebase SDK phía client, nhận "ID token";
 *       backend gọi {@link #verifyIdToken} để kiểm tra token đó do đúng Firebase cấp (chữ ký Google, đúng dự án,
 *       còn hạn) rồi mới cấp JWT riêng của hệ thống.</li>
 *   <li><b>Firebase Cloud Messaging (FCM)</b>: gửi thông báo đẩy tới điện thoại / trình duyệt, xem {@code PushSender}.</li>
 * </ol>
 *
 * <p>TÙY CHỌN: nếu chưa đặt file service-account (app.firebase.credentials-file) thì {@link #isEnabled()} = false,
 * mọi tính năng khác vẫn chạy bình thường (đăng nhập bằng mật khẩu, thông báo trong app).
 */
@Slf4j
@Service
public class FirebaseService {

    private final AppProperties props;
    private FirebaseApp app;   // null = chưa cấu hình

    public FirebaseService(AppProperties props) {
        this.props = props;
    }

    /** Chạy 1 lần sau khi Spring tạo bean: đọc file service-account và khởi tạo FirebaseApp. */
    @PostConstruct
    void init() {
        String file = props.firebase() == null ? null : props.firebase().credentialsFile();
        if (file == null || file.isBlank() || !Files.isRegularFile(Path.of(file))) {
            log.info("Firebase: CHƯA cấu hình (không thấy file '{}'). Tắt đăng nhập Google + thông báo đẩy.", file);
            return;
        }
        try (InputStream in = Files.newInputStream(Path.of(file))) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(in);
            FirebaseOptions.Builder builder = FirebaseOptions.builder().setCredentials(credentials);
            // Lấy tên dự án từ file service account để ghi rõ vào options (log hiện đúng tên dự án)
            if (credentials instanceof ServiceAccountCredentials sa) {
                builder.setProjectId(sa.getProjectId());
            }
            FirebaseOptions options = builder.build();
            // Tránh lỗi "FirebaseApp đã tồn tại" khi Spring khởi động lại context (vd. trong test)
            app = FirebaseApp.getApps().isEmpty() ? FirebaseApp.initializeApp(options) : FirebaseApp.getInstance();
            log.info("Firebase: đã kết nối dự án '{}'", app.getOptions().getProjectId());
        } catch (Exception e) {
            log.error("Firebase: file '{}' không hợp lệ, tắt tính năng Firebase: {}", file, e.getMessage());
            app = null;
        }
    }

    public boolean isEnabled() {
        return app != null;
    }

    /**
     * Kiểm tra ID token do app gửi lên (sau khi đăng nhập Google bằng Firebase).
     *
     * @return thông tin người dùng trong token: uid, email, tên, ảnh
     * @throws ApiException 503 nếu chưa cấu hình Firebase; 401 nếu token không hợp lệ / hết hạn
     */
    public FirebaseToken verifyIdToken(String idToken) {
        if (!isEnabled()) {
            throw ApiException.unavailable("Máy chủ chưa cấu hình Firebase nên chưa đăng nhập Google được");
        }
        try {
            return FirebaseAuth.getInstance(app).verifyIdToken(idToken);
        } catch (FirebaseAuthException e) {
            throw ApiException.unauthorized("Phiên đăng nhập Google không hợp lệ hoặc đã hết hạn");
        }
    }

    /** Đối tượng gửi FCM; chỉ gọi khi {@link #isEnabled()}. */
    public FirebaseMessaging messaging() {
        return FirebaseMessaging.getInstance(app);
    }
}
