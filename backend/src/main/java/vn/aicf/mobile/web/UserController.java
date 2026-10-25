package vn.aicf.mobile.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.aicf.mobile.config.AuthUser;
import vn.aicf.mobile.service.UserService;
import vn.aicf.mobile.web.dto.AuthDtos.ChangePasswordRequest;
import vn.aicf.mobile.web.dto.UserDtos.AdminUpdateRequest;
import vn.aicf.mobile.web.dto.UserDtos.CreateUserRequest;
import vn.aicf.mobile.web.dto.UserDtos.UpdateProfileRequest;
import vn.aicf.mobile.web.dto.UserDtos.UserResponse;

import java.util.List;

/**
 * API NGƯỜI DÙNG.
 * <pre>
 *   GET  /api/users/me            hồ sơ của tôi                         (đã đăng nhập)
 *   PUT  /api/users/me            sửa hồ sơ                             (đã đăng nhập)
 *   PUT  /api/users/me/password   đổi mật khẩu                          (đã đăng nhập)
 *   GET  /api/users               danh sách tài khoản của quán          (OWNER, MANAGER)
 *   POST /api/users               tạo tài khoản cho nhân viên           (OWNER, MANAGER) 201
 *   PUT  /api/users/{id}          đổi vai trò / khóa / cấp lại mật khẩu (OWNER, MANAGER)
 * </pre>
 * Quản lý chỉ thao tác được trên tài khoản nhân viên bán hàng (kiểm tra trong UserService).
 * {@code @AuthenticationPrincipal AuthUser me}: Spring đưa vào người dùng đang đăng nhập (do JwtAuthFilter xác định).
 */
@Tag(name = "02. Người dùng", description = "Hồ sơ cá nhân và tài khoản của người trong quán")
@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final String MANAGERS = "hasAnyRole('OWNER','MANAGER')";

    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @GetMapping("/me")
    @Operation(summary = "Hồ sơ người đang đăng nhập")
    public UserResponse me(@AuthenticationPrincipal AuthUser me) {
        return users.me(me);
    }

    @PutMapping("/me")
    @Operation(summary = "Sửa hồ sơ (họ tên, email, điện thoại)")
    public UserResponse updateMe(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody UpdateProfileRequest request) {
        return users.updateProfile(me, request);
    }

    @PutMapping("/me/password")
    @Operation(summary = "Đổi mật khẩu")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody ChangePasswordRequest request) {
        users.changePassword(me, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @PreAuthorize(MANAGERS)
    @Operation(summary = "Danh sách tài khoản của quán (OWNER, MANAGER)")
    public List<UserResponse> list() {
        return users.list();
    }

    @PostMapping
    @PreAuthorize(MANAGERS)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tạo tài khoản cho người trong quán (OWNER, MANAGER)",
            description = "Quản lý chỉ tạo được vai trò STAFF; chủ quán tạo được mọi vai trò.")
    public UserResponse create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody CreateUserRequest request) {
        return users.create(me, request);
    }

    @PutMapping("/{id}")
    @PreAuthorize(MANAGERS)
    @Operation(summary = "Đổi vai trò / khóa - mở khóa / cấp lại mật khẩu (OWNER, MANAGER)")
    public UserResponse adminUpdate(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                    @Valid @RequestBody AdminUpdateRequest request) {
        return users.adminUpdate(me, id, request);
    }
}
