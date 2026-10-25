package vn.aicf.mobile.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.aicf.mobile.repository.UserRepository;

import java.io.IOException;
import java.util.List;

/**
 * BỘ LỌC chạy trước mọi request: đọc header "Authorization: Bearer ...", kiểm tra JWT
 * rồi báo cho Spring Security biết ai đang gọi và có vai trò gì ("ROLE_OWNER", "ROLE_STAFF"...).
 *
 * <p>Nạp lại người dùng từ CSDL ở mỗi request để: tài khoản bị khóa / đổi vai trò có hiệu lực NGAY,
 * không phải chờ token hết hạn. Token sai hoặc không có -> request đi tiếp dưới dạng "khách vãng lai";
 * API nào cần đăng nhập sẽ bị SecurityConfig chặn với mã 401.
 *
 * <p>Không gắn @Component để Spring Boot không tự đăng ký thêm 1 lần nữa vào chuỗi filter của servlet
 * (filter được thêm thủ công trong {@link SecurityConfig}).
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            jwtService.parse(header.substring(7).trim())
                    .flatMap(userRepository::findById)
                    .filter(u -> u.isActive())
                    .ifPresent(u -> {
                        AuthUser principal = AuthUser.of(u);
                        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name()));
                        SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(principal, null, authorities));
                    });
        }
        chain.doFilter(request, response);
    }
}
