package vn.aicf.mobile.service.ai;

import org.springframework.stereotype.Component;
import vn.aicf.mobile.config.AuthUser;
import vn.aicf.mobile.service.OrderService;
import vn.aicf.mobile.service.ProductService;
import vn.aicf.mobile.service.SettingsService;
import vn.aicf.mobile.service.StatsService;
import vn.aicf.mobile.service.TableService;
import vn.aicf.mobile.web.dto.CatalogDtos.ProductResponse;
import vn.aicf.mobile.web.dto.OrderDtos.OrderResponse;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "CHẾ ĐỘ CƠ BẢN" của Chat AI: trả lời theo từ khóa, KHÔNG cần mạng hay khóa API.
 * Dùng khi chủ quán chưa nhập khóa AI, hoặc dịch vụ AI thật đang lỗi (app vẫn luôn có câu trả lời).
 * Ý tưởng lấy từ "AssistantBrain" của dự án AICF, rút gọn cho đồ án.
 */
@Component
public class OfflineAssistant {

    private final ProductService products;
    private final OrderService orders;
    private final StatsService stats;
    private final SettingsService settings;
    private final TableService tables;

    public OfflineAssistant(ProductService products, OrderService orders, StatsService stats, SettingsService settings,
                            TableService tables) {
        this.tables = tables;
        this.products = products;
        this.orders = orders;
        this.stats = stats;
        this.settings = settings;
    }

    /** Trả lời 1 câu hỏi dựa trên từ khóa (đã bỏ dấu tiếng Việt để so khớp dễ hơn). */
    public String answer(AuthUser user, String message) {
        String q = normalize(message);
        if (containsAny(q, "doanh thu", "ban duoc", "thu duoc", "ban chay", "nhieu nhat", "top") && !user.isManager()) {
            return "Số liệu doanh thu chỉ chủ quán và quản lý xem được.";
        }
        if (containsAny(q, "doanh thu", "ban duoc", "thu duoc")) {
            return revenue(containsAny(q, "hom qua") ? LocalDate.now().minusDays(1) : LocalDate.now());
        }
        if (containsAny(q, "ban chay", "nhieu nhat", "top")) {
            return bestSellers();
        }
        if (containsAny(q, "ban trong", "con ban", "ban nao")) {
            return tablesStatus();
        }
        if (containsAny(q, "dang cho", "dang pha", "dang xu ly", "chua thanh toan", "chua thu", "bill", "don dang")) {
            return openOrders();
        }
        if (containsAny(q, "goi y", "tu van", "nen uong", "uong gi", "mon nao ngon", "de xuat")) {
            return suggest();
        }
        if (containsAny(q, "dia chi", "o dau", "gio mo", "mo cua", "dong cua")) {
            return "📍 " + settings.get(SettingsService.SHOP_NAME, "AICF Coffee") + ": "
                    + settings.get(SettingsService.SHOP_ADDRESS, "(chưa cập nhật địa chỉ)")
                    + "\n🕒 Giờ mở cửa: " + settings.get(SettingsService.SHOP_HOURS, "(chưa cập nhật)");
        }
        if (containsAny(q, "menu", "thuc don", "co mon", "gia", "bao nhieu tien", "mon gi", "tam het")) {
            return menu();
        }
        return help(user);
    }

    /** Câu hỏi gợi ý hiện dưới khung chat (khác nhau theo vai trò). */
    public List<String> suggestions(AuthUser user) {
        if (user.isManager()) {
            return List.of("Doanh thu hôm nay?", "Món nào bán chạy nhất?", "Bàn nào chưa thanh toán?", "Còn bàn trống không?");
        }
        return List.of("Còn bàn trống không?", "Có bill nào đang chờ pha?", "Tư vấn giúp mình 1 món cho khách", "Thực đơn hiện tại");
    }

    // ------------------------------------------------------------------

    private String menu() {
        Map<String, List<String>> byCategory = new LinkedHashMap<>();
        for (ProductResponse p : products.findAll()) {
            if (p.available()) {
                byCategory.computeIfAbsent(p.categoryName(), k -> new ArrayList<>()).add("• " + p.name() + " - " + money(p.price()));
            }
        }
        if (byCategory.isEmpty()) {
            return "Thực đơn chưa có món nào đang bán.";
        }
        StringBuilder sb = new StringBuilder("Thực đơn hôm nay:\n");
        byCategory.forEach((cat, list) -> sb.append("\n").append(cat).append(":\n").append(String.join("\n", list)).append("\n"));
        return sb.toString().trim();
    }

    private String suggest() {
        List<ProductResponse> available = new ArrayList<>(products.findAll().stream().filter(ProductResponse::available).toList());
        if (available.isEmpty()) {
            return "Hiện không có món nào đang bán.";
        }
        Collections.shuffle(available);
        StringBuilder sb = new StringBuilder("Có thể giới thiệu cho khách:\n");
        available.stream().limit(3).forEach(p -> sb.append("• ").append(p.name()).append(" (").append(money(p.price())).append(")")
                .append(p.description() == null ? "" : " - " + p.description()).append("\n"));
        return sb.toString().trim();
    }

    private String openOrders() {
        List<OrderResponse> open = orders.openOrders();
        if (open.isEmpty()) {
            return "Hiện không có bill nào đang mở.";
        }
        StringBuilder sb = new StringBuilder("Đang có " + open.size() + " bill mở:\n");
        open.stream().limit(8).forEach(x -> sb.append("• ").append(x.tableName() == null ? "Mang đi " + x.code() : x.tableName())
                .append(": ").append(x.statusLabel()).append(", ").append(money(x.totalAmount()))
                .append(x.paid() ? " (đã thu)" : " (chưa thu)").append("\n"));
        return sb.toString().trim();
    }

    private String tablesStatus() {
        java.util.Set<Long> busy = new java.util.HashSet<>();
        orders.openOrders().stream().filter(x -> x.tableId() != null).forEach(x -> busy.add(x.tableId()));
        List<String> free = tables.findAll().stream().filter(t -> t.active() && !busy.contains(t.id())).map(t -> t.name()).toList();
        return free.isEmpty() ? "Hiện không còn bàn trống (" + busy.size() + " bàn đang có khách)."
                : "Còn " + free.size() + " bàn trống: " + String.join(", ", free) + ". Đang có khách: " + busy.size() + " bàn.";
    }

    private String revenue(LocalDate day) {
        StatsService.Summary s = stats.summary(day, day, 3);
        return "Ngày " + day + ": doanh thu " + money(s.revenue()) + " từ " + s.paidCount() + " bill đã thu tiền"
                + " (tổng " + s.orderCount() + " đơn, hủy " + s.cancelledCount() + ").";
    }

    private String bestSellers() {
        LocalDate today = LocalDate.now();
        StatsService.Summary s = stats.summary(today.minusDays(6), today, 5);
        if (s.topProducts().isEmpty()) {
            return "7 ngày qua chưa có bill nào thu tiền để thống kê.";
        }
        StringBuilder sb = new StringBuilder("Món bán chạy 7 ngày qua:\n");
        s.topProducts().forEach(t -> sb.append("• ").append(t.productName()).append(": ").append(t.quantity())
                .append(" ly, ").append(money(t.revenue())).append("\n"));
        return sb.toString().trim();
    }

    private String help(AuthUser user) {
        return "Mình đang chạy ở chế độ cơ bản nên chỉ hiểu một số câu hỏi. Bạn thử hỏi: "
                + String.join(" / ", suggestions(user)) + ".";
    }

    /** Bỏ dấu tiếng Việt, chữ thường: "Doanh thu HÔM NAY" -> "doanh thu hom nay". */
    static String normalize(String s) {
        String noMarks = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noMarks.replace('đ', 'd');
    }

    private static boolean containsAny(String text, String... keys) {
        for (String k : keys) {
            if (text.contains(k)) {
                return true;
            }
        }
        return false;
    }

    private static String money(long v) {
        return String.format("%,d", v).replace(',', '.') + " đ";
    }
}
