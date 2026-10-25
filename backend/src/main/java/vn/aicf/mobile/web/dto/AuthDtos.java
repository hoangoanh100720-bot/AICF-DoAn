package vn.aicf.mobile.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * DTO (Data Transfer Object) cho nhóm API xác thực /api/auth.
 * DTO tách biệt với Entity: app chỉ thấy đúng các trường cần thiết, không lộ password_hash;
 * các chú thích @NotBlank, @Size... được kiểm tra tự động khi controller dùng @Valid.
 * {@code @Schema}: mô tả + ví dụ hiện trên trang Swagger.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    /** POST /api/auth/login */
    public record LoginRequest(
            @Schema(example = "nhanvien") @NotBlank(message = "không được để trống") String username,
            @Schema(example = "nhanvien123") @NotBlank(message = "không được để trống") String password) {
    }

    /** POST /api/auth/firebase - app đăng nhập Google qua Firebase rồi gửi ID token lên đây. */
    public record FirebaseLoginRequest(
            @Schema(description = "ID token Firebase lấy sau khi đăng nhập Google trên app")
            @NotBlank(message = "thiếu idToken") String idToken) {
    }

    /** Kết quả đăng nhập: token JWT + thông tin người dùng. */
    public record AuthResponse(
            @Schema(description = "JWT, gửi kèm header Authorization: Bearer <token>") String token,
            Instant expiresAt,
            UserDtos.UserResponse user) {
    }

    /** PUT /api/users/me/password */
    public record ChangePasswordRequest(
            @Schema(description = "Bắt buộc nếu tài khoản đã có mật khẩu") String currentPassword,
            @NotBlank(message = "không được để trống")
            @Size(min = 6, max = 100, message = "dài ít nhất 6 ký tự")
            String newPassword) {
    }
}
