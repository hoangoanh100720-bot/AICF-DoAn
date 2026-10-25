package vn.aicf.mobile.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.aicf.mobile.config.AuthUser;
import vn.aicf.mobile.service.OrderService;
import vn.aicf.mobile.web.dto.OrderDtos.AddItemsRequest;
import vn.aicf.mobile.web.dto.OrderDtos.CreateOrderRequest;
import vn.aicf.mobile.web.dto.OrderDtos.OrderResponse;
import vn.aicf.mobile.web.dto.OrderDtos.PayRequest;
import vn.aicf.mobile.web.dto.OrderDtos.UpdateStatusRequest;

import java.time.LocalDate;
import java.util.List;

/**
 * API ĐƠN HÀNG - bán hàng tại quán (mọi vai trò đã đăng nhập; luật riêng như "ai được hủy" nằm trong OrderService).
 * <pre>
 *   POST   /api/orders                      mở bill mới cho bàn / đơn mang đi          201
 *   GET    /api/orders/active               các bill đang mở (sơ đồ bàn, pha chế)
 *   GET    /api/orders?date=YYYY-MM-DD      mọi đơn trong 1 ngày (lịch sử)
 *   GET    /api/orders/{id}                 chi tiết 1 bill
 *   POST   /api/orders/{id}/items           khách gọi thêm món
 *   DELETE /api/orders/{id}/items/{itemId}  bớt món gọi nhầm (khi chưa pha)
 *   PATCH  /api/orders/{id}/status          Bắt đầu pha / Xong / Đã mang ra / Hủy
 *   POST   /api/orders/{id}/pay             thu tiền (tiền mặt tính tiền thối)
 * </pre>
 * PATCH (sửa một phần) dùng cho thao tác chỉ đổi 1-2 trường của đơn, khác PUT (thay toàn bộ).
 */
@Tag(name = "06. Đơn hàng", description = "Gọi món tại bàn / mang đi, pha chế, phục vụ và thu tiền")
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Mở bill mới (giá do máy chủ tính)",
            description = "DINE_IN cần tableId; bàn đang có bill mở thì trả 409. Bill mới ở trạng thái NEW (chờ pha).")
    public OrderResponse create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody CreateOrderRequest request) {
        return orders.create(me, request);
    }

    @GetMapping("/active")
    @Operation(summary = "Các bill đang mở (chưa hoàn tất / chưa hủy), gọi trước đứng trước")
    public List<OrderResponse> active() {
        return orders.openOrders();
    }

    @GetMapping
    @Operation(summary = "Mọi đơn trong 1 ngày, mặc định hôm nay (lịch sử)")
    public List<OrderResponse> ofDay(@Parameter(description = "Ngày dạng YYYY-MM-DD", example = "2026-10-25")
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return orders.ordersOfDay(date == null ? LocalDate.now() : date);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết 1 bill")
    public OrderResponse get(@PathVariable Long id) {
        return orders.get(id);
    }

    @PostMapping("/{id}/items")
    @Operation(summary = "Gọi thêm món vào bill đang mở",
            description = "Bill đã pha xong (READY / SERVED) sẽ quay về NEW để quầy pha chế thấy món mới.")
    public OrderResponse addItems(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                  @Valid @RequestBody AddItemsRequest request) {
        return orders.addItems(me, id, request.items());
    }

    @DeleteMapping("/{id}/items/{itemId}")
    @Operation(summary = "Bớt 1 món gọi nhầm (chỉ khi bill còn NEW và chưa thu tiền)")
    public OrderResponse removeItem(@PathVariable Long id, @PathVariable Long itemId) {
        return orders.removeItem(id, itemId);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Đổi trạng thái: NEW → PREPARING → READY → SERVED, hoặc CANCELLED",
            description = "Nhân viên chỉ hủy được bill chưa pha; quản lý / chủ quán hủy được mọi bill chưa thu tiền. "
                    + "Không chuyển tay sang COMPLETED: bill tự hoàn tất khi đã phục vụ và đã thu tiền.")
    public OrderResponse updateStatus(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                      @Valid @RequestBody UpdateStatusRequest request) {
        return orders.updateStatus(me, id, request.status(), request.cancelReason());
    }

    @PostMapping("/{id}/pay")
    @Operation(summary = "Thu tiền bill (tiền mặt / chuyển khoản / ví điện tử)",
            description = "Tiền mặt: gửi amountReceived để máy chủ tính changeAmount (tiền thối). Thu tiền rồi thì không hủy được.")
    public OrderResponse pay(@PathVariable Long id, @Valid @RequestBody PayRequest request) {
        return orders.pay(id, request);
    }
}
