package vn.aicf.mobile.service;

import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.aicf.mobile.common.ApiException;
import vn.aicf.mobile.config.AuthUser;
import vn.aicf.mobile.domain.Role;
import vn.aicf.mobile.domain.User;
import vn.aicf.mobile.repository.UserRepository;
import vn.aicf.mobile.web.dto.AuthDtos.ChangePasswordRequest;
import vn.aicf.mobile.web.dto.UserDtos.AdminUpdateRequest;
import vn.aicf.mobile.web.dto.UserDtos.CreateUserRequest;
import vn.aicf.mobile.web.dto.UserDtos.UpdateProfileRequest;
import vn.aicf.mobile.web.dto.UserDtos.UserResponse;

import java.util.List;
import java.util.Locale;

/**
 * Hồ sơ cá nhân (mọi người dùng) và quản lý tài khoản của quán.
 *
 * <p>Luật phân quyền quản lý tài khoản:
 * <ul>
 *   <li>Chủ quán (OWNER): tạo / sửa / khóa mọi tài khoản, gán mọi vai trò.</li>
 *   <li>Quản lý (MANAGER): chỉ tạo / sửa / khóa tài khoản NHÂN VIÊN BÁN HÀNG (STAFF).</li>
 *   <li>Không ai tự hạ quyền hoặc tự khóa chính mình (tránh quán mất người quản trị).</li>
 * </ul>
 * Mật khẩu băm BCrypt; với 1 kết nối SQLite, băm ngay trong giao dịch chỉ khi tạo / đổi (thao tác hiếm).
 */
@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public UserService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Transactional(readOnly = true)
    public UserResponse me(AuthUser me) {
        return UserResponse.of(get(me.id()));
    }

    @Transactional
    public UserResponse updateProfile(AuthUser me, UpdateProfileRequest r) {
        User u = get(me.id());
        String email = AuthService.blankToNull(r.email());
        if (email != null && !email.equalsIgnoreCase(u.getEmail()) && users.existsByEmail(email)) {
            throw ApiException.conflict("Email đã được dùng cho tài khoản khác");
        }
        u.setFullName(r.fullName().trim());
        u.setEmail(email);
        u.setPhone(AuthService.blankToNull(r.phone()));
        return UserResponse.of(u);   // trong giao dịch: JPA tự UPDATE khi kết thúc (dirty checking), không cần save()
    }

    /** Đổi mật khẩu. Tài khoản Google chưa có mật khẩu thì được đặt mật khẩu mới không cần mật khẩu cũ. */
    @Transactional
    public void changePassword(AuthUser me, ChangePasswordRequest r) {
        User u = get(me.id());
        if (u.getPasswordHash() != null
                && (r.currentPassword() == null || !encoder.matches(r.currentPassword(), u.getPasswordHash()))) {
            throw ApiException.badRequest("Mật khẩu hiện tại không đúng");
        }
        u.setPasswordHash(encoder.encode(r.newPassword()));
    }

    // ---------------- Chủ quán / quản lý ----------------

    /** Danh sách tài khoản: chủ quán trước, rồi quản lý, nhân viên (thứ tự khai báo trong enum Role). */
    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return users.findAll(Sort.by("username")).stream()
                .sorted((a, b) -> a.getRole().compareTo(b.getRole()))
                .map(UserResponse::of).toList();
    }

    @Transactional
    public UserResponse create(AuthUser me, CreateUserRequest r) {
        requireCanAssign(me, r.role());
        String username = r.username().trim().toLowerCase(Locale.ROOT);
        String email = AuthService.blankToNull(r.email());
        if (users.existsByUsername(username)) {
            throw ApiException.conflict("Tên đăng nhập \"" + username + "\" đã có người dùng");
        }
        if (email != null && users.existsByEmail(email)) {
            throw ApiException.conflict("Email đã được dùng cho tài khoản khác");
        }
        User u = new User();
        u.setUsername(username);
        u.setPasswordHash(encoder.encode(r.password()));
        u.setFullName(r.fullName().trim());
        u.setEmail(email);
        u.setPhone(AuthService.blankToNull(r.phone()));
        u.setRole(r.role());
        return UserResponse.of(users.save(u));
    }

    @Transactional
    public UserResponse adminUpdate(AuthUser me, Long id, AdminUpdateRequest r) {
        User u = get(id);
        if (u.getId().equals(me.id()) && (r.role() != u.getRole() || !r.active())) {
            throw ApiException.badRequest("Không thể tự đổi vai trò hoặc tự khóa tài khoản của chính mình");
        }
        if (!u.getId().equals(me.id())) {
            requireCanAssign(me, u.getRole());   // được đụng vào tài khoản này không
            requireCanAssign(me, r.role());      // được gán vai trò mới không
        }
        u.setFullName(r.fullName().trim());
        u.setPhone(AuthService.blankToNull(r.phone()));
        u.setRole(r.role());
        u.setActive(r.active());
        if (r.newPassword() != null && !r.newPassword().isBlank()) {
            if (r.newPassword().length() < 6) {
                throw ApiException.badRequest("Mật khẩu mới dài ít nhất 6 ký tự");
            }
            u.setPasswordHash(encoder.encode(r.newPassword()));
        }
        return UserResponse.of(u);
    }

    /** Quản lý chỉ làm việc với tài khoản nhân viên bán hàng; chủ quán thì không giới hạn. */
    private static void requireCanAssign(AuthUser me, Role role) {
        if (!me.isOwner() && role != Role.STAFF) {
            throw ApiException.forbidden("Quản lý chỉ được quản lý tài khoản nhân viên bán hàng");
        }
    }

    private User get(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng #" + id));
    }
}
