package vn.aicf.mobile.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** DTO cho thực đơn: danh mục (/api/categories), món (/api/products), bàn (/api/tables). */
public final class CatalogDtos {

    private CatalogDtos() {
    }

    // ---------------- Danh mục ----------------

    public record CategoryRequest(
            @NotBlank(message = "không được để trống") @Size(max = 60) String name,
            @Size(max = 255) String description,
            @Size(max = 8) String icon,
            int sortOrder) {
    }

    public record CategoryResponse(Long id, String name, String description, String icon, int sortOrder) {
    }

    // ---------------- Món ----------------

    public record ProductRequest(
            @NotNull(message = "chọn danh mục") Long categoryId,
            @NotBlank(message = "không được để trống") @Size(max = 100) String name,
            @Size(max = 500) String description,
            @Min(value = 0, message = "không được âm") @Max(value = 10_000_000, message = "quá lớn") long price,
            @Size(max = 500) String imageUrl,
            boolean available) {
    }

    /**
     * @param bestSeller món nằm trong top bán nhiều nhất 30 ngày qua (app gắn nhãn "Bán chạy"), tính từ đơn đã thu tiền
     */
    public record ProductResponse(Long id, Long categoryId, String categoryName, String categoryIcon, String name,
                                  String description, long price, String imageUrl, boolean available,
                                  @Schema(description = "Top bán chạy 30 ngày qua (nhãn \"Bán chạy\" trên app)") boolean bestSeller) {
    }

    // ---------------- Bàn ----------------

    public record TableRequest(
            @NotBlank(message = "không được để trống") @Size(max = 40) String name,
            @Size(max = 40) String area,
            @Min(value = 1, message = "ít nhất 1 ghế") @Max(value = 50) int seats,
            boolean active) {
    }

    public record TableResponse(Long id, String name, String area, int seats, boolean active) {
    }
}
