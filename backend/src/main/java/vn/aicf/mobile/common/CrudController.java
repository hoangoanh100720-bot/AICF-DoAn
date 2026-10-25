package vn.aicf.mobile.common;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;
import java.util.Map;

/**
 * CONTROLLER REST TỔNG QUÁT - sinh sẵn 5 API chuẩn REST cho 1 tài nguyên:
 *
 * <pre>
 *   Phương thức  Đường dẫn           Ý nghĩa                     Mã trả về
 *   GET          /api/{tài-nguyên}       Lấy danh sách (có lọc)      200 OK
 *   GET          /api/{tài-nguyên}/{id}  Lấy 1 bản ghi               200 OK  | 404
 *   POST         /api/{tài-nguyên}       Thêm mới                    201 Created | 400 | 409
 *   PUT          /api/{tài-nguyên}/{id}  Sửa                         200 OK  | 400 | 404 | 409
 *   DELETE       /api/{tài-nguyên}/{id}  Xóa                         204 No Content | 404 | 409
 * </pre>
 *
 * Lớp con chỉ cần gắn {@code @RestController @RequestMapping("/api/...")} và trả về service tương ứng.
 * Quyền truy cập (ai được thêm/sửa/xóa) cấu hình tập trung theo đường dẫn trong SecurityConfig.
 *
 * @param <REQ> DTO nhận vào
 * @param <RES> DTO trả ra
 */
public abstract class CrudController<REQ, RES> {

    /** Service CRUD mà controller này dùng. */
    protected abstract CrudService<?, REQ, RES> service();

    // @Operation viết 1 lần ở lớp cha; OpenApiConfig.crudSummaries() tự thêm tên tài nguyên vào câu mô tả
    // ("Lấy danh sách" -> "Lấy danh sách món") cho từng nhóm Danh mục, Món, Bàn trên trang Swagger.
    // @Parameter(hidden = true): ẩn tham số dạng Map (Swagger không vẽ được); lớp con nào có bộ lọc
    // thì tự khai báo từng tham số (xem ProductController).
    @GetMapping
    @Operation(summary = "Lấy danh sách")
    public List<RES> list(@Parameter(hidden = true) @RequestParam Map<String, String> filters) {
        return filters.isEmpty() ? service().findAll() : service().findAll(filters);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem 1")
    public RES get(@PathVariable Long id) {
        return service().findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Thêm (OWNER, MANAGER)")
    public RES create(@Valid @RequestBody REQ request) {
        return service().create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Sửa (OWNER, MANAGER)")
    public RES update(@PathVariable Long id, @Valid @RequestBody REQ request) {
        return service().update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa (OWNER, MANAGER)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service().delete(id);
        return ResponseEntity.noContent().build();
    }
}
