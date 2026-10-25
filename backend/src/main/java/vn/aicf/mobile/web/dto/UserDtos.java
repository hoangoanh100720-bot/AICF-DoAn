package vn.aicf.mobile.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import vn.aicf.mobile.domain.Role;
import vn.aicf.mobile.domain.User;

import java.time.LocalDateTime;

/** DTO cho nhóm API người dùng /api/users. */
public final class UserDtos {

    private UserDtos() {
    }

    /** Thông tin người dùng trả về cho app (KHÔNG có mật khẩu). */
    public record UserResponse(Long id, String username, String fullName, String email, String phone,
                               String avatarUrl, Role role,
                               @Schema(example = "Nhân viên bán hàng") String roleLabel,
                               boolean active, boolean hasPassword, LocalDateTime createdAt) {

        public static UserResponse of(User u) {
            return new UserResponse(u.getId(), u.getUsername(), u.getFullName(), u.getEmail(), u.getPhone(),
                    u.getAvatarUrl(), u.getRole(), u.getRole().label(), u.isActive(), u.getPasswordHash() != null,
                    u.getCreatedAt());
        }
    }

    /** PUT /api/users/me - tự sửa hồ sơ. */
    public record UpdateProfileRequest(
            @NotBlank(message = "không được để trống") @Size(max = 100) String fullName,
            @Email(message = "không đúng định dạng email") String email,
            @Size(max = 20) String phone) {
    }

    /** POST /api/users - chủ quán / quản lý tạo tài khoản cho người trong quán. */
    public record CreateUserRequest(
            @Schema(example = "lan")
            @NotBlank(message = "không được để trống")
            @Size(min = 3, max = 40, message = "dài 3-40 ký tự")
            @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "chỉ gồm chữ không dấu, số, dấu . _ -")
            String username,

            @Schema(example = "lan123")
            @NotBlank(message = "không được để trống")
            @Size(min = 6, max = 100, message = "dài ít nhất 6 ký tự")
            String password,

            @Schema(example = "Nguyễn Thị Lan")
            @NotBlank(message = "không được để trống") @Size(max = 100) String fullName,

            @Schema(description = "Trùng email tài khoản Google thì người này đăng nhập Google được")
            @Email(message = "không đúng định dạng email") String email,

            @Size(max = 20) String phone,

            @Schema(description = "Quản lý chỉ tạo được STAFF") @NotNull(message = "chọn vai trò") Role role) {
    }

    /**
     * PUT /api/users/{id} - đổi vai trò, khóa / mở khóa, sửa tên, cấp lại mật khẩu.
     * newPassword để trống = giữ mật khẩu cũ.
     */
    public record AdminUpdateRequest(
            @NotBlank(message = "không được để trống") @Size(max = 100) String fullName,
            @Size(max = 20) String phone,
            @NotNull(message = "chọn vai trò") Role role,
            boolean active,
            @Schema(description = "Để trống = giữ nguyên mật khẩu") @Size(max = 100) String newPassword) {
    }
}
