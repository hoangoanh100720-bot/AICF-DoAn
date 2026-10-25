package vn.aicf.mobile.web.dto;

import vn.aicf.mobile.repository.OrderItemRepository.TopProduct;

import java.time.LocalDate;
import java.util.List;

/** DTO cho thống kê /api/stats (màn hình Tổng quan của chủ quán / quản lý). */
public final class StatsDtos {

    private StatsDtos() {
    }

    /** Doanh thu 1 ngày (vẽ biểu đồ cột 7 ngày). */
    public record DayRevenue(LocalDate date, long revenue) {
    }

    /**
     * GET /api/stats/dashboard?date=2026-10-15
     *
     * @param revenue         doanh thu ngày (đơn đã thu tiền trong ngày, không tính đơn hủy)
     * @param cashRevenue     phần thu bằng tiền mặt (đối chiếu tiền trong két)
     * @param transferRevenue phần thu bằng chuyển khoản + ví điện tử
     * @param paidCount       số bill đã thu tiền trong ngày
     * @param orderCount      tổng số đơn mở trong ngày
     * @param completedCount  số đơn hoàn tất
     * @param cancelledCount  số đơn bị hủy
     * @param activeCount     số bill đang mở (chưa xong / chưa hủy)
     * @param topProducts     5 món bán chạy nhất trong ngày
     * @param last7Days       doanh thu 7 ngày gần nhất (tính đến ngày được chọn)
     */
    public record Dashboard(LocalDate date, long revenue, long cashRevenue, long transferRevenue, long paidCount,
                            long orderCount, long completedCount, long cancelledCount, long activeCount,
                            List<TopProduct> topProducts, List<DayRevenue> last7Days) {
    }
}
