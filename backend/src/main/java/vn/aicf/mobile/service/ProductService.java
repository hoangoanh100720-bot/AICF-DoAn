package vn.aicf.mobile.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.aicf.mobile.common.ApiException;
import vn.aicf.mobile.common.CrudService;
import vn.aicf.mobile.domain.Product;
import vn.aicf.mobile.repository.CategoryRepository;
import vn.aicf.mobile.repository.OrderItemRepository;
import vn.aicf.mobile.repository.ProductRepository;
import vn.aicf.mobile.web.dto.CatalogDtos.ProductRequest;
import vn.aicf.mobile.web.dto.CatalogDtos.ProductResponse;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * CRUD món + tìm kiếm / lọc. Kế thừa {@link CrudService}, ghi đè thêm {@link #findAll(Map)} để hỗ trợ:
 * <pre>
 *   GET /api/products?categoryId=1          món thuộc danh mục 1
 *   GET /api/products?q=sữa                 món có chữ "sữa" trong tên
 *   GET /api/products?available=true        chỉ món đang bán
 * </pre>
 * Mỗi món trả về kèm cờ {@code bestSeller}: thuộc top {@value #BEST_SELLER_COUNT} món bán nhiều nhất
 * {@value #BEST_SELLER_DAYS} ngày qua (tính từ đơn đã thu tiền) - app gắn nhãn "Bán chạy" như thực đơn của AICF.
 */
@Service
public class ProductService extends CrudService<Product, ProductRequest, ProductResponse> {

    static final int BEST_SELLER_COUNT = 3;
    static final int BEST_SELLER_DAYS = 30;
    /** Danh sách món bán chạy chỉ tính lại sau mỗi phút (tránh chạy câu thống kê cho từng món). */
    private static final long BEST_SELLER_CACHE_MS = 60_000;

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final OrderItemRepository orderItems;

    private volatile Set<Long> bestSellers = Set.of();
    private volatile long bestSellersAt;

    public ProductService(ProductRepository products, CategoryRepository categories, OrderItemRepository orderItems) {
        super(products);
        this.products = products;
        this.categories = categories;
        this.orderItems = orderItems;
    }

    @Override
    protected String resourceName() {
        return "món";
    }

    @Override
    protected Product newEntity() {
        return new Product();
    }

    @Override
    protected Sort defaultSort() {
        return Sort.by("category.sortOrder", "name");
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> findAll(Map<String, String> filters) {
        Long categoryId = parseLong(filters.get("categoryId"));
        String q = filters.get("q");
        String keyword = q == null || q.isBlank() ? null : q.trim().toLowerCase(Locale.ROOT);
        boolean onlyAvailable = Boolean.parseBoolean(filters.get("available"));
        return products.search(categoryId, keyword, onlyAvailable).stream().map(this::toResponse).toList();
    }

    @Override
    protected void apply(ProductRequest r, Product p) {
        p.setCategory(categories.findById(r.categoryId())
                .orElseThrow(() -> ApiException.badRequest("Danh mục #" + r.categoryId() + " không tồn tại")));
        p.setName(r.name().trim());
        p.setDescription(r.description());
        p.setPrice(r.price());
        p.setImageUrl(r.imageUrl() == null || r.imageUrl().isBlank() ? null : r.imageUrl().trim());
        p.setAvailable(r.available());
    }

    @Override
    protected ProductResponse toResponse(Product p) {
        return new ProductResponse(p.getId(), p.getCategory().getId(), p.getCategory().getName(), p.getCategory().getIcon(),
                p.getName(), p.getDescription(), p.getPrice(), p.getImageUrl(), p.isAvailable(), bestSellerIds().contains(p.getId()));
    }

    /** Id các món bán chạy (lưu tạm 1 phút). Luôn được gọi trong giao dịch của hàm đọc / ghi món. */
    private Set<Long> bestSellerIds() {
        long now = System.currentTimeMillis();
        if (now - bestSellersAt > BEST_SELLER_CACHE_MS) {
            bestSellers = new HashSet<>(orderItems.bestSellerIds(LocalDateTime.now().minusDays(BEST_SELLER_DAYS),
                    PageRequest.of(0, BEST_SELLER_COUNT)));
            bestSellersAt = now;
        }
        return bestSellers;
    }

    private static Long parseLong(String s) {
        try {
            return s == null || s.isBlank() ? null : Long.valueOf(s.trim());
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("categoryId phải là số");
        }
    }
}
