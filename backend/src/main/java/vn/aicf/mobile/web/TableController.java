package vn.aicf.mobile.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.aicf.mobile.common.CrudController;
import vn.aicf.mobile.common.CrudService;
import vn.aicf.mobile.service.TableService;
import vn.aicf.mobile.web.dto.CatalogDtos.TableRequest;
import vn.aicf.mobile.web.dto.CatalogDtos.TableResponse;

/** API BÀN: kế thừa 5 API CRUD từ {@link CrudController} (xem: đã đăng nhập; thêm/sửa/xóa: OWNER, MANAGER). */
@Tag(name = "05. Bàn", description = "Bàn trong quán (sơ đồ bàn). Xem: mọi người đã đăng nhập; thêm / sửa / xóa: OWNER, MANAGER")
@RestController
@RequestMapping("/api/tables")
public class TableController extends CrudController<TableRequest, TableResponse> {

    private final TableService service;

    public TableController(TableService service) {
        this.service = service;
    }

    @Override
    protected CrudService<?, TableRequest, TableResponse> service() {
        return service;
    }
}
