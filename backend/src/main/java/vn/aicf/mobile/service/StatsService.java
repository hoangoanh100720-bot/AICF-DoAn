package vn.aicf.mobile.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.aicf.mobile.domain.OrderStatus;
import vn.aicf.mobile.domain.PaymentMethod;
import vn.aicf.mobile.repository.OrderItemRepository;
import vn.aicf.mobile.repository.OrderItemRepository.TopProduct;
import vn.aicf.mobile.repository.OrderRepository;
import vn.aicf.mobile.web.dto.StatsDtos.Dashboard;
import vn.aicf.mobile.web.dto.StatsDtos.DayRevenue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * THỐNG KÊ cho màn hình Tổng quan của chủ quán / quản lý, đồng thời là nguồn số liệu cho các "công cụ" của Chat AI
 * (AI hỏi doanh thu thì gọi vào đây, không tự bịa số).
 * Doanh thu tính theo NGÀY THU TIỀN (paid_at): bill mở tối qua, thu tiền sáng nay thì tính vào hôm nay.
 */
@Service
public class StatsService {

    private final OrderRepository orders;
    private final OrderItemRepository items;

    public StatsService(OrderRepository orders, OrderItemRepository items) {
        this.orders = orders;
        this.items = items;
    }

    /** Số liệu tổng hợp trong khoảng ngày [from, to] (tính cả 2 đầu). */
    public record Summary(LocalDate from, LocalDate to, long revenue, long paidCount, long orderCount,
                          long cancelledCount, List<TopProduct> topProducts) {
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard(LocalDate day) {
        LocalDateTime start = day.atStartOfDay();
        LocalDateTime end = day.plusDays(1).atStartOfDay();
        long total = orders.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(start, end);
        long completed = orders.countByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(OrderStatus.COMPLETED, start, end);
        long cancelled = orders.countByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(OrderStatus.CANCELLED, start, end);
        long revenue = orders.revenueBetween(start, end);
        long cash = orders.revenueByMethod(PaymentMethod.CASH, start, end);

        List<DayRevenue> last7 = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = day.minusDays(i);
            last7.add(new DayRevenue(d, orders.revenueBetween(d.atStartOfDay(), d.plusDays(1).atStartOfDay())));
        }
        return new Dashboard(day, revenue, cash, revenue - cash, orders.paidCountBetween(start, end),
                total, completed, cancelled, total - completed - cancelled,
                items.topProducts(start, end, PageRequest.of(0, 5)), last7);
    }

    @Transactional(readOnly = true)
    public Summary summary(LocalDate from, LocalDate to, int top) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        return new Summary(from, to,
                orders.revenueBetween(start, end),
                orders.paidCountBetween(start, end),
                orders.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(start, end),
                orders.countByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(OrderStatus.CANCELLED, start, end),
                items.topProducts(start, end, PageRequest.of(0, Math.max(1, Math.min(top, 20)))));
    }
}
