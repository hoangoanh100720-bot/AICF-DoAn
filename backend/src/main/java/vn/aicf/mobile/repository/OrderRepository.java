package vn.aicf.mobile.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.aicf.mobile.domain.CustomerOrder;
import vn.aicf.mobile.domain.OrderStatus;
import vn.aicf.mobile.domain.PaymentMethod;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/** Truy vấn bảng ORDERS (+ thống kê doanh thu). */
public interface OrderRepository extends JpaRepository<CustomerOrder, Long> {

    /** Đơn theo danh sách trạng thái, cũ nhất trước (màn pha chế: đơn nào gọi trước làm trước). */
    List<CustomerOrder> findByStatusInOrderByIdAsc(Collection<OrderStatus> statuses);

    /** Bàn này đang có bill mở không (mỗi bàn chỉ 1 bill mở). */
    boolean existsByTableIdAndStatusIn(Long tableId, Collection<OrderStatus> statuses);

    /** Tất cả đơn trong khoảng [from, to), mới nhất trước. */
    List<CustomerOrder> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByIdDesc(LocalDateTime from, LocalDateTime to);

    /** Số đơn đã tạo trong khoảng [from, to). */
    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(LocalDateTime from, LocalDateTime to);

    /** Số đơn theo trạng thái trong khoảng [from, to). */
    long countByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(OrderStatus status, LocalDateTime from, LocalDateTime to);

    /**
     * Doanh thu = tổng tiền các đơn ĐÃ THU TIỀN (không tính đơn hủy) có giờ thu tiền trong [from, to).
     * COALESCE(..., 0): không có đơn nào thì SUM trả NULL -> đổi thành 0.
     */
    @Query("""
            SELECT COALESCE(SUM(o.totalAmount), 0) FROM CustomerOrder o
            WHERE o.paid = true AND o.status <> vn.aicf.mobile.domain.OrderStatus.CANCELLED
              AND o.paidAt >= :from AND o.paidAt < :to
            """)
    long revenueBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Doanh thu theo 1 cách thanh toán (tiền mặt / chuyển khoản / ví) trong [from, to). */
    @Query("""
            SELECT COALESCE(SUM(o.totalAmount), 0) FROM CustomerOrder o
            WHERE o.paid = true AND o.status <> vn.aicf.mobile.domain.OrderStatus.CANCELLED
              AND o.paymentMethod = :method AND o.paidAt >= :from AND o.paidAt < :to
            """)
    long revenueByMethod(@Param("method") PaymentMethod method, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Số đơn đã thu tiền trong [from, to). */
    @Query("""
            SELECT COUNT(o) FROM CustomerOrder o
            WHERE o.paid = true AND o.status <> vn.aicf.mobile.domain.OrderStatus.CANCELLED
              AND o.paidAt >= :from AND o.paidAt < :to
            """)
    long paidCountBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
