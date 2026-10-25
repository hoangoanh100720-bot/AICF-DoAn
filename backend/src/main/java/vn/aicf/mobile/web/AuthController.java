package vn.aicf.mobile.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.aicf.mobile.service.AuthService;
import vn.aicf.mobile.web.dto.AuthDtos.AuthResponse;
import vn.aicf.mobile.web.dto.AuthDtos.FirebaseLoginRequest;
import vn.aicf.mobile.web.dto.AuthDtos.LoginRequest;

/**
 * API XÁC THỰC (không cần đăng nhập).
 * <pre>
 *   POST /api/auth/login      đăng nhập tên + mật khẩu       -> 200 + token | 401 | 403 (bị khóa)
 *   POST /api/auth/firebase   đăng nhập Google (Firebase)    -> 200 + token | 401 | 403 (chưa được cấp) | 503
 * </pre>
 * Không có API đăng ký: app dành cho người trong quán, tài khoản do chủ quán / quản lý tạo ở POST /api/users.
 * Controller chỉ nhận request, kiểm tra dữ liệu (@Valid) rồi giao cho Service - không chứa nghiệp vụ.
 *
 * <p>Các chú thích dành cho SWAGGER (chỉ ảnh hưởng trang tài liệu /swagger-ui.html, không đổi cách API chạy):
 * <ul>
 *   <li>{@code @Tag} đặt trên lớp: tên NHÓM chứa các API của controller này trên trang Swagger.</li>
 *   <li>{@code @Operation(summary = ...)} đặt trên từng hàm: dòng mô tả hiện cạnh đường dẫn API.</li>
 *   <li>{@code @SecurityRequirements()} rỗng: API này không cần token (Swagger bỏ biểu tượng ổ khóa).</li>
 * </ul>
 * Mọi controller khác dùng cùng cách này (xem giải thích đầy đủ ở config/OpenApiConfig.java).
 */
@Tag(name = "01. Xác thực", description = "Đăng nhập lấy JWT. Tài khoản do chủ quán / quản lý cấp, không có đăng ký tự do.")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    @SecurityRequirements
    // Swagger: dòng mô tả hiện cạnh "POST /api/auth/login"; thư viện tự đọc thêm kiểu dữ liệu
    // LoginRequest (các trường + điều kiện @NotBlank) để vẽ mẫu JSON cần gửi
    @Operation(summary = "Đăng nhập bằng tên đăng nhập + mật khẩu",
            description = "Tài khoản mẫu: chu/chu123 (chủ quán), quanly/quanly123 (quản lý), nhanvien/nhanvien123 (nhân viên). "
                    + "Chép giá trị token rồi bấm nút Authorize ở đầu trang.")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request);
    }

    @PostMapping("/firebase")
    @SecurityRequirements
    @Operation(summary = "Đăng nhập Google: gửi Firebase ID token, nhận JWT của hệ thống",
            description = "Chỉ vào được nếu email Google trùng email của một tài khoản chủ quán đã tạo (ngược lại 403).")
    public AuthResponse firebase(@Valid @RequestBody FirebaseLoginRequest request) {
        return auth.loginWithFirebase(request.idToken());
    }
}
