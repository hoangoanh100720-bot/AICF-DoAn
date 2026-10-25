package vn.aicf.mobile.service;

import com.google.firebase.auth.FirebaseToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import vn.aicf.mobile.common.ApiException;
import vn.aicf.mobile.config.JwtService;
import vn.aicf.mobile.domain.Role;
import vn.aicf.mobile.domain.User;
import vn.aicf.mobile.repository.UserRepository;
import vn.aicf.mobile.service.firebase.FirebaseService;
import vn.aicf.mobile.web.dto.AuthDtos.AuthResponse;
import vn.aicf.mobile.web.dto.AuthDtos.LoginRequest;
import vn.aicf.mobile.web.dto.AuthDtos.RegisterRequest;
import vn.aicf.mobile.web.dto.UserDtos.UserResponse;

import java.util.Locale;

/**
 * Nghiệp vụ XÁC THỰC: đăng ký, đăng nhập bằng mật khẩu, đăng nhập Google qua Firebase.
 * Cả 3 cách đều kết thúc bằng việc cấp JWT của hệ thống ({@link #issue}), nên các API còn lại
 * không cần quan tâm người dùng đã đăng nhập theo cách nào.
 *
 * <p>Việc CHẬM không dính tới CSDL (băm/kiểm tra mật khẩu BCrypt ~100 ms, gọi mạng tới Firebase) làm NGOÀI
 * giao dịch; chỉ phần đọc/ghi bảng users chạy trong giao dịch ngắn ({@link TransactionTemplate}).
 * Lý do: backend chỉ dùng 1 kết nối SQLite (xem application.yml); giữ kết nối trong lúc băm mật khẩu thì
 * khi nhiều người đăng ký cùng lúc, mọi yêu cầu khác (kể cả đặt đơn) phải xếp hàng chờ.
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

    /** Khách tự đăng ký -> luôn là CUSTOMER (không cho tự chọn vai trò). Đăng ký xong đăng nhập luôn. */
    public AuthResponse register(RegisterRequest r) {
        String username = r.username().trim().toLowerCase(Locale.ROOT);
        String email = blankToNull(r.email());
        String hash = encoder.encode(r.password());   // băm trước, ngoài giao dịch
        User saved = tx.execute(s -> {
            if (users.existsByUsername(username)) {
                throw ApiException.conflict("Tên đăng nhập đã có người dùng");
            }
            if (email != null && users.existsByEmail(email)) {
                throw ApiException.conflict("Email đã được dùng cho tài khoản khác");
            }
            User u = new User();
            u.setUsername(username);
            u.setPasswordHash(hash);
            u.setFullName(r.fullName().trim());
            u.setEmail(email);
            u.setPhone(blankToNull(r.phone()));
            u.setRole(Role.CUSTOMER);
            return users.save(u);
        });
        return issue(saved);
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
            throw ApiException.forbidden("Tài khoản đã bị khóa, vui lòng liên hệ quán");
        }
        return issue(u);
    }

    /**
     * Đăng nhập Google qua Firebase:
     * <ol>
     *   <li>Kiểm tra ID token với Firebase -> lấy uid, email, tên, ảnh.</li>
     *   <li>Tìm người dùng theo firebase_uid; chưa có thì tìm theo email (liên kết với tài khoản đã đăng ký trước);
     *       vẫn chưa có thì tạo tài khoản CUSTOMER mới.</li>
     *   <li>Cấp JWT của hệ thống.</li>
     * </ol>
     */
    public AuthResponse loginWithFirebase(String idToken) {
        FirebaseToken token = firebase.verifyIdToken(idToken);   // gọi mạng tới Firebase, ngoài giao dịch
        String email = blankToNull(token.getEmail());
        User saved = tx.execute(s -> {
            User u = users.findByFirebaseUid(token.getUid())
                    .or(() -> email == null ? java.util.Optional.empty() : users.findByEmail(email))
                    .orElseGet(() -> newFirebaseUser(token, email));
            if (!u.isActive()) {
                throw ApiException.forbidden("Tài khoản đã bị khóa, vui lòng liên hệ quán");
            }
            u.setFirebaseUid(token.getUid());
            if (u.getAvatarUrl() == null) {
                u.setAvatarUrl(token.getPicture());
            }
            return users.save(u);
        });
        return issue(saved);
    }

    private User newFirebaseUser(FirebaseToken token, String email) {
        User u = new User();
        u.setUsername(uniqueUsername(email != null ? email.substring(0, email.indexOf('@')) : "google"));
        u.setFullName(token.getName() != null ? token.getName() : (email != null ? email : "Khách Google"));
        u.setEmail(email);
        u.setRole(Role.CUSTOMER);
        return u;   // passwordHash = null: tài khoản chỉ đăng nhập bằng Google
    }

    /** Sinh tên đăng nhập chưa ai dùng: "an", "an1", "an2"... */
    private String uniqueUsername(String base) {
        String clean = base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "");
        if (clean.length() < 3) {
            clean = "user" + clean;
        }
        String candidate = clean;
        for (int i = 1; users.existsByUsername(candidate); i++) {
            candidate = clean + i;
        }
        return candidate;
    }

    private AuthResponse issue(User u) {
        JwtService.Issued t = jwt.issue(u);
        return new AuthResponse(t.token(), t.expiresAt(), UserResponse.of(u));
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
