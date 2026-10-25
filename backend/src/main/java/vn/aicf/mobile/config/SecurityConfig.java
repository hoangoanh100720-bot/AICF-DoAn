package vn.aicf.mobile.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import vn.aicf.mobile.common.ApiError;
import vn.aicf.mobile.repository.UserRepository;

import java.io.IOException;
import java.util.List;

/**
 * CẤU HÌNH BẢO MẬT - toàn bộ luật "ai được gọi API nào" nằm ở MỘT chỗ để dễ đọc / dễ chấm.
 *
 * <pre>
 *   API                                               Ai được gọi
 *   ------------------------------------------------  ---------------------------------------------
 *   /api/auth/** (đăng nhập), GET /api/shop, /api/health  Mọi người (màn đăng nhập cần tên quán)
 *   GET /api/categories, /products, /tables           Mọi người ĐÃ ĐĂNG NHẬP
 *   POST/PUT/DELETE categories, products, tables      OWNER, MANAGER (quản lý thực đơn, sơ đồ bàn)
 *   /api/stats/**                                     OWNER, MANAGER
 *   /api/settings/**                                  OWNER (thông tin quán, khóa API AI)
 *   /api/users/me/**                                  Mọi người đã đăng nhập
 *   /api/users/** còn lại                             OWNER, MANAGER (chi tiết thêm trong UserService)
 *   Các /api/** còn lại (đơn hàng, chat, thông báo)   Đã đăng nhập (luật riêng nằm trong Service)
 *   Đường dẫn ngoài /api (giao diện PWA, Swagger)     Mọi người
 * </pre>
 * App chỉ dùng nội bộ quán: không có API nào cho khách hàng.
 */
@Configuration
@EnableMethodSecurity   // bật @PreAuthorize("hasAnyRole('OWNER','MANAGER')") trên từng phương thức controller
public class SecurityConfig {

    /** Băm mật khẩu bằng BCrypt (có "muối" ngẫu nhiên, chậm có chủ đích -> khó dò mật khẩu). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService, UserRepository users,
                                           ObjectMapper json) throws Exception {
        http
                // API dùng JWT trong header, không dùng cookie phiên -> tắt CSRF và không tạo session
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(c -> { })   // dùng bean corsConfigurationSource bên dưới
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // Trình duyệt gửi OPTIONS trước request khác nguồn (CORS preflight)
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/**", "/api/health", "/api/shop").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/categories/**", "/api/products/**", "/api/tables/**").authenticated()
                        .requestMatchers("/api/categories/**", "/api/products/**", "/api/tables/**").hasAnyRole("OWNER", "MANAGER")
                        .requestMatchers("/api/users/me", "/api/users/me/**").authenticated()
                        .requestMatchers("/api/stats/**", "/api/users/**").hasAnyRole("OWNER", "MANAGER")
                        .requestMatchers("/api/settings/**").hasRole("OWNER")
                        .requestMatchers("/api/**").authenticated()
                        // Mọi đường dẫn ngoài /api: giao diện PWA, ảnh, và trang Swagger
                        // (/swagger-ui.html, /swagger-ui/**, /v3/api-docs) -> mở tự do. Trang Swagger chỉ là tài liệu;
                        // khi bấm "Try it out" gọi vào /api/** thì vẫn phải qua đúng các luật quyền ở trên.
                        .anyRequest().permitAll())
                // Trả lỗi 401 / 403 dạng JSON giống mọi lỗi khác (mặc định Spring trả trang HTML)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                writeError(res, json, 401, "Unauthorized", "Bạn cần đăng nhập (token thiếu, sai hoặc đã hết hạn)"))
                        .accessDeniedHandler((req, res, ex) ->
                                writeError(res, json, 403, "Forbidden", "Bạn không có quyền thực hiện thao tác này")))
                // Chèn bộ lọc JWT trước bộ lọc đăng nhập mặc định
                .addFilterBefore(new JwtAuthFilter(jwtService, users), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * CORS: cho phép app Capacitor (chạy ở http://localhost bên trong điện thoại) và các nguồn trong cấu hình
     * gọi API khác nguồn. PWA do chính backend phục vụ thì cùng nguồn, không cần CORS.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(AppProperties props) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(props.corsOrigins());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cfg);
        return source;
    }

    private static void writeError(HttpServletResponse res, ObjectMapper json, int status, String error, String message)
            throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        json.writeValue(res.getOutputStream(), ApiError.of(status, error, message));
    }
}
