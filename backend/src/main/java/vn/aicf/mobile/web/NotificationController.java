package vn.aicf.mobile.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.aicf.mobile.config.AuthUser;
import vn.aicf.mobile.service.NotificationService;
import vn.aicf.mobile.web.dto.NotificationDtos.DeviceTokenRequest;
import vn.aicf.mobile.web.dto.NotificationDtos.NotificationResponse;
import vn.aicf.mobile.web.dto.NotificationDtos.UnreadCount;

import java.util.List;
import java.util.Map;

/**
 * API THÔNG BÁO + ĐĂNG KÝ THIẾT BỊ NHẬN FCM (đều cần đăng nhập).
 * <pre>
 *   GET    /api/notifications                 hộp thư của tôi
 *   GET    /api/notifications/unread-count    số chưa đọc (chấm đỏ)
 *   POST   /api/notifications/{id}/read       đánh dấu 1 thông báo đã đọc
 *   POST   /api/notifications/read-all        đánh dấu tất cả đã đọc
 *   POST   /api/devices                       lưu mã FCM của thiết bị
 *   DELETE /api/devices?token=...             xóa mã khi đăng xuất
 * </pre>
 */
@Tag(name = "07. Thông báo & FCM", description = "Hộp thư trong app (đơn mới, đồ đã xong, đơn bị hủy) và đăng ký thiết bị nhận thông báo đẩy Firebase")
@RestController
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping("/api/notifications")
    @Operation(summary = "Hộp thư thông báo của tôi (mới nhất trước)")
    public List<NotificationResponse> list(@AuthenticationPrincipal AuthUser me, @RequestParam(defaultValue = "30") int limit) {
        return notifications.list(me, limit);
    }

    @GetMapping("/api/notifications/unread-count")
    @Operation(summary = "Số thông báo chưa đọc")
    public UnreadCount unread(@AuthenticationPrincipal AuthUser me) {
        return new UnreadCount(notifications.unreadCount(me));
    }

    @PostMapping("/api/notifications/{id}/read")
    @Operation(summary = "Đánh dấu đã đọc 1 thông báo")
    public NotificationResponse read(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return notifications.markRead(me, id);
    }

    @PostMapping("/api/notifications/read-all")
    @Operation(summary = "Đánh dấu đã đọc tất cả")
    public Map<String, Integer> readAll(@AuthenticationPrincipal AuthUser me) {
        return Map.of("marked", notifications.markAllRead(me));
    }

    @PostMapping("/api/devices")
    @Operation(summary = "Lưu mã Firebase Cloud Messaging của thiết bị (gọi sau khi người dùng cho phép thông báo)")
    public ResponseEntity<Void> register(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody DeviceTokenRequest request) {
        notifications.registerDevice(me, request.token(), request.platform());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/devices")
    @Operation(summary = "Xóa mã thiết bị (khi đăng xuất)")
    public ResponseEntity<Void> unregister(@AuthenticationPrincipal AuthUser me, @RequestParam String token) {
        notifications.unregisterDevice(me, token);
        return ResponseEntity.noContent().build();
    }
}
