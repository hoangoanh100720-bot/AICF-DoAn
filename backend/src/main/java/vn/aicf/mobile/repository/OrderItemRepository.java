package vn.aicf.mobile.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.aicf.mobile.domain.OrderItem;

import java.time.LocalDateTime;
import java.util.List;

/** Truy vấn bảng ORDER_ITEMS. */
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /** 1 dòng kết quả "món bán chạy": tên món, tổng số lượng, tổng tiền. */
    record TopProduct(String productName, long quantity, long revenue) {
    }

    /**
     * Món bán chạy trong khoảng [from, to) (chỉ tính đơn đã thu tiền, không tính đơn hủy).
     * "SELECT new ..." tạo thẳng đối tượng TopProduct từ kết quả GROUP BY (constructor expression của JPQL);
     * lớp lồng bên trong interface được viết là TênNgoài$TênTrong.
     * Pageable dùng để giới hạn số dòng (LIMIT), vd PageRequest.of(0, 5) = top 5.
     */
    @Query("""
            SELECT new vn.aicf.mobile.repository.OrderItemRepository$TopProduct(
                     i.productName, SUM(i.quantity), SUM(i.unitPrice * i.quantity))
            FROM OrderItem i JOIN i.order o
            WHERE o.paid = true AND o.status <> vn.aicf.mobile.domain.OrderStatus.CANCELLED
              AND o.paidAt >= :from AND o.paidAt < :to
            GROUP BY i.productName
            ORDER BY SUM(i.quantity) DESC
            """)
    List<TopProduct> topProducts(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, Pageable page);

    /** Id các món bán nhiều nhất từ {@code from} tới nay (gắn nhãn "Bán chạy" trên thực đơn). */
    @Query("""
            SELECT i.product.id FROM OrderItem i JOIN i.order o
            WHERE o.paid = true AND o.status <> vn.aicf.mobile.domain.OrderStatus.CANCELLED
              AND i.product IS NOT NULL AND o.paidAt >= :from
            GROUP BY i.product.id
            ORDER BY SUM(i.quantity) DESC
            """)
    List<Long> bestSellerIds(@Param("from") LocalDateTime from, Pageable page);
}
