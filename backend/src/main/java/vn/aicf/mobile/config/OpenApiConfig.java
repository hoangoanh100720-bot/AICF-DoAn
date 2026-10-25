package vn.aicf.mobile.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.customizers.OperationCustomizer;
import vn.aicf.mobile.common.CrudController;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * CẤU HÌNH SWAGGER - trang tài liệu REST API tự sinh, mở tại http://localhost:8090/swagger-ui.html
 *
 * <h3>Swagger là gì?</h3>
 * REST API là "cửa" để app nói chuyện với máy chủ (vd POST /api/orders để đặt món). App gọi bằng code nên
 * bình thường con người không nhìn thấy có những API nào, mỗi API cần gửi gì, trả về gì.
 * Swagger là một TRANG WEB tự động liệt kê toàn bộ API đó, và cho bấm nút "Try it out" để gọi thử
 * ngay trên trình duyệt (không cần app, không cần Postman).
 *
 * <h3>Swagger KHÔNG phải chương trình riêng</h3>
 * Nó là 1 thư viện (springdoc-openapi, khai báo trong pom.xml) chạy BÊN TRONG chính máy chủ backend.
 * Khi khởi động, thư viện tự đọc các Controller (@RestController, @GetMapping, @PostMapping...)
 * để biết có API nào, rồi sinh ra 2 thứ:
 * <ol>
 *   <li>/v3/api-docs        : bản mô tả API dạng JSON theo chuẩn OpenAPI 3 (máy đọc)</li>
 *   <li>/swagger-ui.html    : trang web vẽ từ bản mô tả đó (người đọc)</li>
 * </ol>
 * Thêm / sửa 1 API trong Controller -> khởi động lại -> Swagger tự cập nhật, không phải viết tài liệu tay.
 *
 * <h3>Vì sao đưa vào đồ án</h3>
 * <ul>
 *   <li>Báo cáo: thầy cô thấy ngay hệ thống có những API REST nào (47 API trên 31 đường dẫn, chia 10 nhóm).</li>
 *   <li>Kiểm thử backend độc lập với app: gọi thử từng API, xem mã trả về 200 / 400 / 401 / 403 / 409.</li>
 *   <li>Người làm frontend biết chính xác phải gửi JSON gì, nhận về JSON gì.</li>
 * </ul>
 *
 * <h3>Các chỗ liên quan tới Swagger trong code</h3>
 * <ul>
 *   <li>pom.xml                     : thêm thư viện springdoc-openapi-starter-webmvc-ui</li>
 *   <li>application.yml (springdoc:) : đường dẫn trang và cách sắp xếp</li>
 *   <li>LỚP NÀY                     : tên, mô tả, phiên bản API + nút "Authorize" để dán JWT</li>
 *   <li>@Tag trên mỗi Controller    : tên NHÓM API, vd @Tag(name = "06. Đơn hàng")</li>
 *   <li>@Operation trên mỗi hàm     : câu mô tả ngắn của từng API (dòng chữ cạnh đường dẫn)</li>
 *   <li>@Schema trên trường DTO     : mô tả + giá trị ví dụ của từng trường JSON</li>
 *   <li>crudSummaries() (lớp này)   : thêm tên tài nguyên vào mô tả 15 API CRUD dùng chung</li>
 *   <li>SecurityConfig              : cho phép mở trang Swagger không cần đăng nhập</li>
 * </ul>
 * Bỏ Swagger đi thì app vẫn chạy bình thường: chỉ mất trang tài liệu, các API không bị ảnh hưởng.
 */
@Configuration
public class OpenApiConfig {

    /**
     * Thông tin chung hiển thị ở đầu trang Swagger + khai báo cách xác thực.
     * {@code @Bean}: Spring tạo đối tượng này 1 lần lúc khởi động; thư viện springdoc tự tìm và dùng nó.
     */
    @Bean
    public OpenAPI openApi() {
        // Tên của "kiểu xác thực" (đặt tùy ý, chỉ dùng để 2 chỗ bên dưới tham chiếu tới nhau)
        String scheme = "bearer-jwt";
        return new OpenAPI()
                // Tiêu đề, phiên bản, mô tả ở đầu trang
                .info(new Info()
                        .title("AICF Coffee Mobile - REST API")
                        .version("1.0.0")
                        .description("REST API (Spring Boot) cho app bán hàng NỘI BỘ của quán cà phê - dành cho chủ quán (OWNER), "
                                + "quản lý (MANAGER) và nhân viên bán hàng (STAFF), không có phần cho khách. "
                                + "Gồm: đăng nhập JWT / Google (Firebase), người dùng, thực đơn, sơ đồ bàn, gọi món - pha chế - thu tiền, "
                                + "thông báo FCM, Trợ lý AI, thống kê. Dữ liệu lưu trong SQLite. "
                                + "Thử: 01. Xác thực > POST /api/auth/login (nhanvien / nhanvien123) > chép token > bấm Authorize."))
                // Áp dụng kiểu xác thực cho MỌI API: Swagger tự gắn header "Authorization: Bearer <token>"
                // vào request khi gọi thử (API công khai như /api/products vẫn gọi được không cần token)
                .addSecurityItem(new SecurityRequirement().addList(scheme))
                // Định nghĩa kiểu xác thực = HTTP Bearer dùng JWT. Nhờ dòng này trang Swagger có nút "Authorize":
                //   1. gọi POST /api/auth/login -> chép "token" trong kết quả
                //   2. bấm "Authorize" -> dán token -> từ đó gọi thử được các API cần đăng nhập
                .components(new Components().addSecuritySchemes(scheme, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }

    /**
     * 15 API của Danh mục / Món / Bàn dùng chung 1 lớp cha {@link CrudController} nên cùng 1 câu mô tả
     * ("Lấy danh sách", "Thêm"...). Hàm này đọc tên nhóm trong {@code @Tag} của controller con
     * (vd "04. Món" -> "món") rồi chèn vào câu mô tả: "Lấy danh sách món", "Thêm món (OWNER, MANAGER)".
     * OperationCustomizer: springdoc gọi hàm này cho TỪNG API lúc dựng trang Swagger.
     */
    @Bean
    public OperationCustomizer crudSummaries() {
        return (operation, handler) -> {
            Class<?> type = handler.getBeanType();
            Tag tag = type.getAnnotation(Tag.class);
            if (!CrudController.class.isAssignableFrom(type) || tag == null || operation.getSummary() == null) {
                return operation;
            }
            String resource = tag.name().replaceFirst("^\\d+\\.\\s*", "").toLowerCase();
            String s = operation.getSummary();
            int bracket = s.indexOf(" (");
            operation.setSummary(bracket < 0 ? s + " " + resource : s.substring(0, bracket) + " " + resource + s.substring(bracket));
            return operation;
        };
    }
}
