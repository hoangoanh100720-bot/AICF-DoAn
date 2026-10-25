package vn.aicf.mobile.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import vn.aicf.mobile.common.BaseEntity;

/**
 * Bảng USERS - tài khoản đăng nhập. Xem chú thích từng cột trong db/schema.sql.
 * Kế thừa id / created_at / updated_at từ {@link BaseEntity}.
 */
@Getter
@Setter
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String username;

    /** Mật khẩu đã băm BCrypt (KHÔNG bao giờ lưu mật khẩu gốc). Null với tài khoản chỉ đăng nhập Google. */
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    private String email;

    private String phone;

    @Column(name = "avatar_url")
    private String avatarUrl;

    /** Lưu tên hằng số ("OWNER"...) thay vì số thứ tự -> đọc được trong CSDL, thêm vai trò mới không lệch dữ liệu. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.STAFF;

    /** UID bên Firebase Authentication, có khi người dùng đăng nhập bằng Google. */
    @Column(name = "firebase_uid", unique = true)
    private String firebaseUid;

    @Column(nullable = false)
    private boolean active = true;
}
