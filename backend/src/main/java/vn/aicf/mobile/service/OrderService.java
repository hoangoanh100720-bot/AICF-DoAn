package vn.aicf.mobile.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.aicf.mobile.common.ApiException;
import vn.aicf.mobile.config.AuthUser;
import vn.aicf.mobile.domain.CustomerOrder;
import vn.aicf.mobile.domain.DiningTable;
import vn.aicf.mobile.domain.OrderItem;
import vn.aicf.mobile.domain.OrderStatus;
import vn.aicf.mobile.domain.OrderType;
import vn.aicf.mobile.domain.PaymentMethod;
import vn.aicf.mobile.domain.Product;
import vn.aicf.mobile.repository.DiningTableRepository;
import vn.aicf.mobile.repository.OrderRepository;
import vn.aicf.mobile.repository.ProductRepository;
import vn.aicf.mobile.repository.UserRepository;
import vn.aicf.mobile.web.dto.OrderDtos.CreateOrderRequest;
import vn.aicf.mobile.web.dto.OrderDtos.ItemRequest;
import vn.aicf.mobile.web.dto.OrderDtos.OrderResponse;
import vn.aicf.mobile.web.dto.OrderDtos.PayRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * NGHIỆP VỤ ĐƠN HÀNG (bán hàng tại quán) - phần "lõi" của đồ án.
 *
 * <p>Luồng làm việc trong quán:
 * <ol>
 *   <li>Nhân viên chạm vào bàn trống trên sơ đồ bàn -> chọn món -> {@link #create} (bill ở trạng thái NEW).</li>
 *   <li>Khách gọi thêm -> {@link #addItems}; gọi nhầm -> {@link #removeItem} (chỉ khi món chưa pha).</li>
 *   <li>Pha chế bấm Bắt đầu / Xong, phục vụ bấm Đã mang ra -> {@link #updateStatus}.</li>
 *   <li>Thu tiền -> {@link #pay}. Đã phục vụ + đã thu tiền = HOÀN TẤT, bàn trống trở lại.</li>
 * </ol>
 *
 * <p>Nguyên tắc:
 * <ul>
 *   <li>Giá lấy từ CSDL tại thời điểm gọi món, tổng tiền do backend tính ({@link CustomerOrder#recalculateTotal()}).</li>
 *   <li>Chuyển trạng thái theo luật trong {@link OrderStatus#canMoveTo}; mỗi bàn chỉ có 1 bill đang mở.</li>
 *   <li>Nhân viên chỉ hủy được đơn chưa pha; quản lý / chủ quán hủy được mọi đơn chưa thu tiền.</li>
 *   <li>Mỗi thay đổi quan trọng gửi thông báo (trong app + FCM) qua {@link NotificationService}.</li>
 * </ul>
 */
@Service
public class OrderService {

    private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** Các trạng thái "đang mở" (bill chưa đóng): hiện trên sơ đồ bàn và màn pha chế. */
    public static final EnumSet<OrderStatus> OPEN =
            EnumSet.of(OrderStatus.NEW, OrderStatus.PREPARING, OrderStatus.READY, OrderStatus.SERVED);

    private final OrderRepository orders;
    private final ProductRepository products;
    private final DiningTableRepository tables;
    private final UserRepository users;
    private final NotificationService notifications;

    public OrderService(OrderRepository orders, ProductRepository products, DiningTableRepository tables,
                        UserRepository users, NotificationService notifications) {
        this.orders = orders;
        this.products = products;
        this.tables = tables;
        this.users = users;
        this.notifications = notifications;
    }

    /**
     * Mở bill mới. Toàn bộ chạy trong 1 giao dịch: lỗi ở bất kỳ món nào thì không lưu gì cả (tính nguyên tử).
     */
    @Transactional
    public OrderResponse create(AuthUser me, CreateOrderRequest r) {
        CustomerOrder o = new CustomerOrder();
        o.setCreatedBy(users.getReferenceById(me.id()));
        o.setOrderType(r.orderType());
        o.setNote(blankToNull(r.note()));

        // Đơn tại bàn phải chọn bàn đang dùng và bàn đó chưa có bill mở
        if (r.orderType() == OrderType.DINE_IN) {
            if (r.tableId() == null) {
                throw ApiException.badRequest("Đơn tại bàn cần chọn bàn");
            }
            DiningTable t = tables.findById(r.tableId())
                    .filter(DiningTable::isActive)
                    .orElseThrow(() -> ApiException.badRequest("Bàn đã chọn không tồn tại hoặc đang tạm ngưng"));
            if (orders.existsByTableIdAndStatusIn(t.getId(), OPEN)) {
                throw ApiException.conflict(t.getName() + " đang có bill, hãy thêm món vào bill đó");
            }
            o.setTable(t);
        }
        for (ItemRequest ir : r.items()) {
            o.addItem(newItem(ir));
        }
        o.recalculateTotal();

        // Mã đơn dựa trên id (luôn duy nhất, không bị trùng khi 2 người tạo cùng lúc):
        // lưu trước với mã tạm để SQLite cấp id, sau đó đổi thành mã đẹp DH<ngày>-<id>.
        o.setCode("TMP-" + UUID.randomUUID());
        orders.saveAndFlush(o);
        o.setCode("DH" + LocalDate.now().format(CODE_DATE) + "-" + String.format("%04d", o.getId()));

        notifications.notifyTeam(me.id(), "Đơn mới · " + where(o),
                o.itemCount() + " món: " + itemsText(o.getItems()), o.getId());
        return OrderResponse.of(o);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long id) {
        return OrderResponse.of(find(id));
    }

    /** Bill đang mở (chưa hoàn tất / chưa hủy), gọi trước đứng trước. */
    @Transactional(readOnly = true)
    public List<OrderResponse> openOrders() {
        return orders.findByStatusInOrderByIdAsc(OPEN).stream().map(OrderResponse::of).toList();
    }

    /** Mọi đơn tạo trong 1 ngày (lịch sử). */
    @Transactional(readOnly = true)
    public List<OrderResponse> ordersOfDay(LocalDate day) {
        return orders.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByIdDesc(day.atStartOfDay(), day.plusDays(1).atStartOfDay())
                .stream().map(OrderResponse::of).toList();
    }

    /**
     * Khách gọi thêm món vào bill đang mở. Nếu đồ của bill đã pha xong (READY / SERVED) thì bill quay về
     * "Chờ pha" để quầy pha chế thấy có món mới.
     */
    @Transactional
    public OrderResponse addItems(AuthUser me, Long id, List<ItemRequest> items) {
        CustomerOrder o = find(id);
        requireOpen(o);
        if (o.isPaid()) {
            throw ApiException.conflict("Đơn đã thu tiền, hãy mở bill mới cho món gọi thêm");
        }
        for (ItemRequest ir : items) {
            o.addItem(newItem(ir));
        }
        o.recalculateTotal();
        if (o.getStatus() == OrderStatus.READY || o.getStatus() == OrderStatus.SERVED) {
            o.setStatus(OrderStatus.NEW);
        }
        int size = o.getItems().size();   // các món vừa thêm nằm cuối danh sách
        notifications.notifyTeam(me.id(), "Gọi thêm · " + where(o), itemsText(o.getItems().subList(size - items.size(), size)), o.getId());
        return OrderResponse.of(o);
    }

    /** Bớt 1 dòng món gọi nhầm: chỉ khi bill còn "Chờ pha" và chưa thu tiền; bill phải còn ít nhất 1 món. */
    @Transactional
    public OrderResponse removeItem(Long orderId, Long itemId) {
        CustomerOrder o = find(orderId);
        if (o.getStatus() != OrderStatus.NEW || o.isPaid()) {
            throw ApiException.conflict("Chỉ bớt được món khi đơn còn \"Chờ pha\" và chưa thu tiền");
        }
        OrderItem item = o.getItems().stream().filter(i -> i.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Đơn không có món #" + itemId));
        if (o.getItems().size() == 1) {
            throw ApiException.conflict("Đơn chỉ còn 1 món - hãy hủy đơn thay vì bớt món");
        }
        o.getItems().remove(item);   // orphanRemoval: dòng món bị xóa khỏi CSDL
        o.recalculateTotal();
        return OrderResponse.of(o);
    }

    /** Đổi trạng thái theo đúng luật. Hủy đơn: nhân viên chỉ khi đơn chưa pha, quản lý thì mọi đơn chưa thu tiền. */
    @Transactional
    public OrderResponse updateStatus(AuthUser me, Long id, OrderStatus target, String cancelReason) {
        CustomerOrder o = find(id);
        OrderStatus current = o.getStatus();
        if (target == OrderStatus.COMPLETED) {
            throw ApiException.badRequest("Đơn tự hoàn tất khi đã phục vụ và đã thanh toán (POST /api/orders/{id}/pay)");
        }
        if (!current.canMoveTo(target)) {
            throw ApiException.conflict("Không thể chuyển đơn từ \"" + current.label() + "\" sang \"" + target.label() + "\"");
        }
        if (target == OrderStatus.CANCELLED) {
            if (o.isPaid()) {
                throw ApiException.conflict("Đơn đã thu tiền, không hủy được");
            }
            if (!me.isManager() && current != OrderStatus.NEW) {
                throw ApiException.forbidden("Đồ đã pha, chỉ quản lý hoặc chủ quán được hủy đơn này");
            }
            o.setCancelReason(cancelReason == null || cancelReason.isBlank() ? "Hủy bởi " + me.fullName() : cancelReason.trim());
        }
        o.setStatus(target);
        completeIfDone(o);

        if (target == OrderStatus.READY && !o.getCreatedBy().getId().equals(me.id())) {
            notifications.notifyUsers(List.of(o.getCreatedBy().getId()), where(o) + " đã xong",
                    "Mang ra cho khách: " + itemsText(o.getItems()), o.getId());
        } else if (target == OrderStatus.CANCELLED) {
            notifications.notifyManagers(me.id(), "Đơn " + o.getCode() + " đã hủy",
                    where(o) + " · " + o.getCancelReason() + " (" + me.fullName() + ")", o.getId());
        }
        return OrderResponse.of(o);
    }

    /**
     * Thu tiền. Tiền mặt có thể nhập tiền khách đưa để app hiện tiền thối (phải >= tổng bill).
     * Bill đã phục vụ thì hoàn tất luôn; chưa phục vụ (vd. đơn mang đi trả trước) thì vẫn mở tới khi giao đồ.
     */
    @Transactional
    public OrderResponse pay(Long id, PayRequest r) {
        CustomerOrder o = find(id);
        requireOpen(o);
        if (o.isPaid()) {
            throw ApiException.conflict("Đơn " + o.getCode() + " đã thu tiền rồi");
        }
        Long received = r.amountReceived();
        if (received != null && r.paymentMethod() == PaymentMethod.CASH && received < o.getTotalAmount()) {
            throw ApiException.badRequest("Tiền khách đưa chưa đủ (thiếu " + money(o.getTotalAmount() - received) + ")");
        }
        o.setPaymentMethod(r.paymentMethod());
        o.setAmountReceived(r.paymentMethod() == PaymentMethod.CASH ? received : null);
        o.setPaid(true);
        o.setPaidAt(LocalDateTime.now());
        completeIfDone(o);
        return OrderResponse.of(o);
    }

    // ============================== Hàm phụ ==============================

    /** Đã phục vụ + đã thu tiền -> hoàn tất (bàn trống trở lại). */
    private static void completeIfDone(CustomerOrder o) {
        if (o.isPaid() && o.getStatus() == OrderStatus.SERVED) {
            o.setStatus(OrderStatus.COMPLETED);
        }
    }

    private OrderItem newItem(ItemRequest ir) {
        Product p = products.findById(ir.productId())
                .orElseThrow(() -> ApiException.badRequest("Món #" + ir.productId() + " không còn trong thực đơn"));
        if (!p.isAvailable()) {
            throw ApiException.conflict("Món \"" + p.getName() + "\" đang tạm hết");
        }
        OrderItem item = new OrderItem();
        item.setProduct(p);
        item.setProductName(p.getName());   // chụp lại tên + giá tại thời điểm gọi món
        item.setUnitPrice(p.getPrice());
        item.setQuantity(ir.quantity());
        item.setNote(blankToNull(ir.note()));
        return item;
    }

    private static void requireOpen(CustomerOrder o) {
        if (o.getStatus().isFinal()) {
            throw ApiException.conflict("Đơn " + o.getCode() + " đã " + o.getStatus().label().toLowerCase());
        }
    }

    private CustomerOrder find(Long id) {
        return orders.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn #" + id));
    }

    /** "Bàn 02" hoặc "Mang đi DH...-0007". */
    private static String where(CustomerOrder o) {
        return o.getTable() != null ? o.getTable().getName() : "Mang đi " + o.getCode();
    }

    private static String itemsText(List<OrderItem> items) {
        return String.join(", ", items.stream().map(i -> i.getQuantity() + " " + i.getProductName()).toList());
    }

    static String money(long amount) {
        return String.format("%,d", amount).replace(',', '.') + " đ";
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
