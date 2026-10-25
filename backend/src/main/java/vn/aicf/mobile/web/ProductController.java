package vn.aicf.mobile.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.aicf.mobile.common.CrudController;
import vn.aicf.mobile.common.CrudService;
import vn.aicf.mobile.service.ProductService;
import vn.aicf.mobile.web.dto.CatalogDtos.ProductRequest;
import vn.aicf.mobile.web.dto.CatalogDtos.ProductResponse;

import java.util.List;
import java.util.Map;

/**
 * API MÓN: kế thừa 5 API CRUD từ {@link CrudController}.
 * Lọc danh sách bằng tham số: GET /api/products?categoryId=1&q=sữa&available=true
 */
@Tag(name = "04. Món", description = "Món trên thực đơn. Xem: mọi người đã đăng nhập; thêm / sửa / xóa: OWNER, MANAGER")
@RestController
@RequestMapping("/api/products")
public class ProductController extends CrudController<ProductRequest, ProductResponse> {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @Override
    protected CrudService<?, ProductRequest, ProductResponse> service() {
        return service;
    }

    /**
     * Ghi đè CHỈ để Swagger hiện 3 tham số lọc (lớp cha nhận chung 1 Map nên Swagger không biết có tham số nào).
     * Cách xử lý giữ nguyên: gọi lại hàm của lớp cha.
     */
    @Override
    @GetMapping
    @Operation(summary = "Lấy danh sách", description = "Mỗi món có cờ bestSeller = top 3 bán chạy 30 ngày qua.")
    @Parameter(name = "categoryId", in = ParameterIn.QUERY, description = "Chỉ lấy món thuộc danh mục này",
            schema = @Schema(type = "integer", example = "1"))
    @Parameter(name = "q", in = ParameterIn.QUERY, description = "Tìm theo tên món (không phân biệt hoa thường)",
            schema = @Schema(type = "string", example = "sữa"))
    @Parameter(name = "available", in = ParameterIn.QUERY, description = "true = chỉ món đang bán",
            schema = @Schema(type = "boolean"))
    public List<ProductResponse> list(@Parameter(hidden = true) @RequestParam Map<String, String> filters) {
        return super.list(filters);
    }
}
