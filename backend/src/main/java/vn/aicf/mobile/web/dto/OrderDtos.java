package vn.aicf.mobile.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.aicf.mobile.domain.CustomerOrder;
import vn.aicf.mobile.domain.OrderItem;
import vn.aicf.mobile.domain.OrderStatus;
import vn.aicf.mobile.domain.OrderType;
import vn.aicf.mobile.domain.PaymentMethod;

import java.time.LocalDateTime;
import java.util.List;

/** DTO cho nhóm API đơn hàng /api/orders. */
public final class OrderDtos {

    private OrderDtos() {
    }

    /**
     * POST /api/orders - nhân viên mở bill cho 1 bàn (hoặc đơn mang đi). Ví dụ JSON:
     * <pre>
     * {
     *   "orderType": "DINE_IN", "tableId": 2, "note": "Khách quen",
     *   "items": [ { "productId": 1, "quantity": 2, "note": "ít đá" }, { "productId": 5, "quantity": 1 } ]
     * }
     * </pre>
     * Chỉ gửi id món và số lượng; GIÁ do backend tự lấy từ CSDL (không tin giá app gửi lên).
     */
    public record CreateOrderRequest(
            @Schema(example = "DINE_IN") @NotNull(message = "chọn tại bàn hoặc mang đi") OrderType orderType,
            @Schema(example = "2", description = "Bắt buộc khi DINE_IN") Long tableId,
            @Size(max = 500) String note,
            @NotEmpty(message = "chưa chọn món nào") @Size(max = 50) List<@Valid ItemRequest> items) {
    }

    /** POST /api/orders/{id}/items - khách gọi thêm món vào bill đang mở. */
    public record AddItemsRequest(
            @NotEmpty(message = "chưa chọn món nào") @Size(max = 50) List<@Valid ItemRequest> items) {
    }

    public record ItemRequest(
            @Schema(example = "1") @NotNull(message = "thiếu id món") Long productId,
            @Schema(example = "2") @Min(value = 1, message = "ít nhất 1") @Max(value = 99, message = "tối đa 99") int quantity,
            @Schema(example = "ít đá") @Size(max = 200) String note) {
    }

    /** PATCH /api/orders/{id}/status - pha chế / phục vụ đổi trạng thái, hoặc hủy đơn (kèm lý do). */
    public record UpdateStatusRequest(
            @Schema(example = "PREPARING", description = "PREPARING | READY | SERVED | CANCELLED")
            @NotNull(message = "chọn trạng thái") OrderStatus status,
            @Schema(description = "Lý do khi hủy đơn") @Size(max = 200) String cancelReason) {
    }

    /** POST /api/orders/{id}/pay - thu tiền. */
    public record PayRequest(
            @Schema(example = "CASH") @NotNull(message = "chọn cách thanh toán") PaymentMethod paymentMethod,
            @Schema(example = "200000", description = "Tiền mặt khách đưa (để tính tiền thối); bỏ trống = đưa đủ")
            @Min(value = 0, message = "không được âm") Long amountReceived) {
    }

    public record ItemResponse(Long id, Long productId, String productName, long unitPrice, int quantity,
                               long lineTotal, String note) {

        static ItemResponse of(OrderItem i) {
            return new ItemResponse(i.getId(), i.getProduct() == null ? null : i.getProduct().getId(),
                    i.getProductName(), i.getUnitPrice(), i.getQuantity(), i.getUnitPrice() * i.getQuantity(), i.getNote());
        }
    }

    /**
     * Một đơn (bill) trả về cho app.
     *
     * @param createdByName tên nhân viên mở bill
     * @param itemCount     tổng số ly / phần
     * @param changeAmount  tiền thối cho khách (khi trả tiền mặt và có nhập tiền khách đưa)
     */
    public record OrderResponse(Long id, String code, Long createdById, String createdByName,
                                Long tableId, String tableName, String tableArea,
                                OrderType orderType, OrderStatus status, String statusLabel,
                                long totalAmount, int itemCount,
                                PaymentMethod paymentMethod, boolean paid, LocalDateTime paidAt,
                                Long amountReceived, Long changeAmount,
                                String note, String cancelReason,
                                List<ItemResponse> items, LocalDateTime createdAt, LocalDateTime updatedAt) {

        /** Đổi Entity -> DTO. Phải gọi TRONG giao dịch vì đụng tới các quan hệ LAZY (createdBy, table, items). */
        public static OrderResponse of(CustomerOrder o) {
            Long change = o.getAmountReceived() == null ? null : o.getAmountReceived() - o.getTotalAmount();
            return new OrderResponse(o.getId(), o.getCode(), o.getCreatedBy().getId(), o.getCreatedBy().getFullName(),
                    o.getTable() == null ? null : o.getTable().getId(),
                    o.getTable() == null ? null : o.getTable().getName(),
                    o.getTable() == null ? null : o.getTable().getArea(),
                    o.getOrderType(), o.getStatus(), o.getStatus().label(), o.getTotalAmount(), o.itemCount(),
                    o.getPaymentMethod(), o.isPaid(), o.getPaidAt(), o.getAmountReceived(), change,
                    o.getNote(), o.getCancelReason(),
                    o.getItems().stream().map(ItemResponse::of).toList(), o.getCreatedAt(), o.getUpdatedAt());
        }
    }
}
