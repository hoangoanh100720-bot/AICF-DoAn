package vn.aicf.mobile.domain;

import java.util.Map;
import java.util.Set;

/**
 * Trạng thái đơn hàng và luật chuyển trạng thái (máy trạng thái - state machine).
 *
 * <pre>
 *   NEW ──> PREPARING ──> READY ──> SERVED ──(đã thanh toán)──> COMPLETED
 *    │  └───────────────────^                     ^
 *    └── (chưa thanh toán) có thể ──> CANCELLED    └── thanh toán trước rồi mới phục vụ cũng được
 * </pre>
 * Nhân viên gọi món -> đơn ở NEW (chờ pha). Pha chế bấm "Bắt đầu" (PREPARING), "Xong" (READY).
 * Phục vụ mang ra bàn (SERVED). Đơn chỉ HOÀN TẤT (COMPLETED) khi đã phục vụ VÀ đã thanh toán:
 * hệ thống tự chuyển, không ai chuyển tay sang COMPLETED.
 * Đặt luật ở MỘT chỗ giúp mọi nơi (API, Trợ lý AI...) đều tuân theo cùng một quy tắc.
 */
public enum OrderStatus {
    NEW("Chờ pha"),
    PREPARING("Đang pha"),
    READY("Đã xong, chờ mang ra"),
    SERVED("Đã phục vụ"),
    COMPLETED("Hoàn tất"),
    CANCELLED("Đã hủy");

    /** Tên tiếng Việt để hiển thị / gửi thông báo. */
    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Từ mỗi trạng thái, nhân viên được bấm chuyển tới những trạng thái nào. */
    private static final Map<OrderStatus, Set<OrderStatus>> NEXT = Map.of(
            NEW, Set.of(PREPARING, READY, CANCELLED),
            PREPARING, Set.of(READY, CANCELLED),
            READY, Set.of(SERVED, CANCELLED),
            SERVED, Set.of(CANCELLED),
            COMPLETED, Set.of(),
            CANCELLED, Set.of());

    /** Có được chuyển từ trạng thái hiện tại sang {@code target} không. */
    public boolean canMoveTo(OrderStatus target) {
        return NEXT.get(this).contains(target);
    }

    /** Đơn đã kết thúc (không đổi được nữa, bàn được giải phóng). */
    public boolean isFinal() {
        return this == COMPLETED || this == CANCELLED;
    }
}
