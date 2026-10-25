package vn.aicf.mobile.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.aicf.mobile.domain.Role;
import vn.aicf.mobile.domain.User;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Truy vấn bảng USERS.
 * Spring Data JPA tự sinh câu SQL từ TÊN PHƯƠNG THỨC, ví dụ:
 * <pre>
 *   findByUsername(u)    -> SELECT * FROM users WHERE username = ?
 *   existsByEmail(e)     -> SELECT COUNT(*) > 0 FROM users WHERE email = ?
 *   findByRoleIn(...)    -> SELECT * FROM users WHERE role IN (?, ?)
 * </pre>
 * Không cần viết lớp cài đặt: Spring tạo sẵn lúc chạy.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByFirebaseUid(String firebaseUid);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /** Dùng để gửi thông báo "có đơn mới" cho người trong quán đang hoạt động. */
    List<User> findByRoleInAndActiveTrue(Collection<Role> roles);
}
