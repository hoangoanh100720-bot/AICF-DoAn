package vn.aicf.mobile.service;

import com.google.firebase.auth.FirebaseToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import vn.aicf.mobile.common.ApiException;
import vn.aicf.mobile.config.JwtService;
import vn.aicf.mobile.domain.User;
import vn.aicf.mobile.repository.UserRepository;
import vn.aicf.mobile.service.firebase.FirebaseService;
import vn.aicf.mobile.web.dto.AuthDtos.AuthResponse;
import vn.aicf.mobile.web.dto.AuthDtos.LoginRequest;
import vn.aicf.mobile.web.dto.UserDtos.UserResponse;

import java.util.Locale;
import java.util.Optional;

/**
 * Nghiệp vụ XÁC THỰC: đăng nhập bằng mật khẩu, đăng nhập Google qua Firebase.
 * App chỉ dùng trong quán nên KHÔNG có đăng ký tự do: tài khoản do chủ quán / quản lý tạo (POST /api/users).
 * Cả 2 cách đăng nhập đều kết thúc bằng việc cấp JWT của hệ thống ({@link #issue}), nên các API còn lại
 * không cần quan tâm người dùng đã đăng nhập theo cách nào.
 *
 * <p>Việc CHẬM không dính tới CSDL (kiểm tra mật khẩu BCrypt ~100 ms, gọi mạng tới Firebase) làm NGOÀI
 * giao dịch; chỉ phần đọc/ghi bảng users chạy trong giao dịch ngắn ({@link TransactionTemplate}).
 * Lý do: backend chỉ dùng 1 kết nối SQLite (xem application.yml).
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final FirebaseService firebase;
    private final TransactionTemplate tx;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt, FirebaseService firebase,
                       TransactionTemplate tx) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.firebase = firebase;
        this.tx = tx;
    }

    /**
     * Đăng nhập bằng tên + mật khẩu. Sai tên hay sai mật khẩu đều báo CÙNG một câu
     * để người ngoài không dò được tên đăng nhập nào có tồn tại.
     */
    public AuthResponse login(LoginRequest r) {
        // findByUsername tự chạy trong giao dịch ngắn của repository; kiểm tra BCrypt sau khi đã trả kết nối
        User u = users.findByUsername(r.username().trim().toLowerCase(Locale.ROOT))
                .filter(x -> x.getPasswordHash() != null && encoder.matches(r.password(), x.getPasswordHash()))
                .orElseThrow(() -> ApiException.unauthorized("Sai tên đăng nhập hoặc mật khẩu"));
        if (!u.isActive()) {
            throw ApiException.forbidden("Tài khoản đã bị khóa, vui lòng liên hệ chủ quán");
        }
        return issue(u);
    }

    /**
     * Đăng nhập Google qua Firebase:
     * <ol>
     *   <li>Kiểm tra ID token với Firebase -> lấy uid, email, ảnh.</li>
     *   <li>Tìm tài khoản theo firebase_uid; chưa có thì theo EMAIL mà chủ quán đã nhập khi tạo tài khoản.</li>
     *   <li>Không có tài khoản nào khớp -> từ chối (403): người lạ không tự vào được app của quán.</li>
     *   <li>Cấp JWT của hệ thống.</li>
     * </ol>
     */
    public AuthResponse loginWithFirebase(String idToken) {
        FirebaseToken token = firebase.verifyIdToken(idToken);   // gọi mạng tới Firebase, ngoài giao dịch
        String email = blankToNull(token.getEmail());
        User saved = tx.execute(s -> {
            User u = users.findByFirebaseUid(token.getUid())
                    .or(() -> email == null ? Optional.empty() : users.findByEmail(email))
                    .orElseThrow(() -> ApiException.forbidden(
                            "Tài khoản Google " + (email == null ? "" : email + " ") + "chưa được chủ quán cấp quyền vào app"));
            if (!u.isActive()) {
                throw ApiException.forbidden("Tài khoản đã bị khóa, vui lòng liên hệ chủ quán");
            }
            u.setFirebaseUid(token.getUid());
            if (u.getAvatarUrl() == null) {
                u.setAvatarUrl(token.getPicture());
            }
            return users.save(u);
        });
        return issue(saved);
    }

    private AuthResponse issue(User u) {
        JwtService.Issued t = jwt.issue(u);
        return new AuthResponse(t.token(), t.expiresAt(), UserResponse.of(u));
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
