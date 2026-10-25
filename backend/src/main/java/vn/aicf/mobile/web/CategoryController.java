package vn.aicf.mobile.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.aicf.mobile.common.CrudController;
import vn.aicf.mobile.common.CrudService;
import vn.aicf.mobile.service.CategoryService;
import vn.aicf.mobile.web.dto.CatalogDtos.CategoryRequest;
import vn.aicf.mobile.web.dto.CatalogDtos.CategoryResponse;

/**
 * API DANH MỤC: GET/POST /api/categories, GET/PUT/DELETE /api/categories/{id}.
 * Toàn bộ 5 API được KẾ THỪA từ {@link CrudController} - lớp này chỉ khai báo đường dẫn và service.
 */
@Tag(name = "03. Danh mục", description = "Nhóm món trên thực đơn. Xem: mọi người đã đăng nhập; thêm / sửa / xóa: OWNER, MANAGER")
@RestController
@RequestMapping("/api/categories")
public class CategoryController extends CrudController<CategoryRequest, CategoryResponse> {

    private final CategoryService service;

    public CategoryController(CategoryService service) {
        this.service = service;
    }

    @Override
    protected CrudService<?, CategoryRequest, CategoryResponse> service() {
        return service;
    }
}
