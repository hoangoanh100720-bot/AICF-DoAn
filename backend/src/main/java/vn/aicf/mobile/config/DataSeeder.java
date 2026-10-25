package vn.aicf.mobile.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.aicf.mobile.domain.Role;
import vn.aicf.mobile.domain.User;
import vn.aicf.mobile.repository.UserRepository;

/**
 * Tạo TÀI KHOẢN BAN ĐẦU lần đầu chạy (khi bảng users còn trống), để chủ quán vào app ngay:
 * <pre>
 *   chu      / chu123       - OWNER   (chủ quán)
 *   quanly   / quanly123    - MANAGER (quản lý)
 *   nhanvien / nhanvien123  - STAFF   (nhân viên bán hàng)
 * </pre>
 * Không đặt trong data.sql vì mật khẩu phải được băm BCrypt bằng Java.
 * APP_SEED_DEMO=false: chỉ tạo tài khoản chủ quán (chủ quán tự tạo tài khoản cho nhân viên trong app).
 * Nhớ đổi mật khẩu khi dùng thật! (Danh mục, món, bàn mẫu nằm trong db/data.sql.)
 */
@Slf4j
@Component
public class DataSeeder implements ApplicationRunner {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AppProperties props;

    public DataSeeder(UserRepository users, PasswordEncoder encoder, AppProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        create("chu", "chu123", "Chủ quán", Role.OWNER);
        if (props.seedDemoUsers()) {
            create("quanly", "quanly123", "Quản lý ca", Role.MANAGER);
            create("nhanvien", "nhanvien123", "Nhân viên bán hàng", Role.STAFF);
            log.info("Đã tạo tài khoản mẫu: chu/chu123, quanly/quanly123, nhanvien/nhanvien123");
        } else {
            log.info("Đã tạo tài khoản chủ quán: chu/chu123 - hãy đổi mật khẩu ngay");
        }
    }

    private void create(String username, String password, String fullName, Role role) {
        User u = new User();
        u.setUsername(username);
        u.setPasswordHash(encoder.encode(password));
        u.setFullName(fullName);
        u.setRole(role);
        users.save(u);
    }
}
