package vn.aicf.mobile.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.aicf.mobile.common.ApiException;
import vn.aicf.mobile.config.AuthUser;
import vn.aicf.mobile.domain.DeviceToken;
import vn.aicf.mobile.domain.Notification;
import vn.aicf.mobile.domain.Role;
import vn.aicf.mobile.domain.User;
import vn.aicf.mobile.repository.DeviceTokenRepository;
import vn.aicf.mobile.repository.NotificationRepository;
import vn.aicf.mobile.repository.UserRepository;
import vn.aicf.mobile.service.firebase.PushSender;
import vn.aicf.mobile.web.dto.NotificationDtos.NotificationResponse;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Thông báo cho người dùng theo 2 kênh:
 * <ol>
 *   <li>Lưu vào bảng NOTIFICATIONS (hộp thư trong app) - luôn chạy.</li>
 *   <li>Đẩy qua Firebase Cloud Messaging tới các thiết bị đã đăng ký - chỉ khi đã cấu hình Firebase.</li>
 * </ol>
 * Các service khác (vd. OrderService) chỉ cần gọi {@link #notifyUsers}, {@link #notifyTeam} hoặc {@link #notifyManagers}.
 */
@Service
public class NotificationService {

    private final NotificationRepository notifications;
    private final DeviceTokenRepository devices;
    private final UserRepository users;
    private final PushSender push;

    public NotificationService(NotificationRepository notifications, DeviceTokenRepository devices,
                               UserRepository users, PushSender push) {
        this.notifications = notifications;
        this.devices = devices;
        this.users = users;
        this.push = push;
    }

    // ===================== Gửi thông báo =====================

    /** Gửi cho một nhóm người dùng theo id. */
    @Transactional
    public void notifyUsers(Collection<Long> userIds, String title, String body, Long orderId) {
        if (userIds.isEmpty()) {
            return;
        }
        for (Long uid : userIds) {
            Notification n = new Notification();
            n.setUser(users.getReferenceById(uid));   // getReferenceById: chỉ cần id, không SELECT cả người dùng
            n.setTitle(title);
            n.setBody(body);
            n.setOrderId(orderId);
            notifications.save(n);
        }
        Map<String, String> data = new HashMap<>();
        if (orderId != null) {
            data.put("orderId", String.valueOf(orderId));
        }
        // Lấy danh sách mã trong giao dịch, việc gửi chạy nền (PushSender là @Async)
        push.send(devices.findTokensOfUsers(userIds), title, body, data);
    }

    /** Gửi cho cả quán trừ người vừa thao tác (vd. "Đơn mới · Bàn 02" để quầy pha chế biết). */
    @Transactional
    public void notifyTeam(Long exceptUserId, String title, String body, Long orderId) {
        notifyRoles(Set.of(Role.OWNER, Role.MANAGER, Role.STAFF), exceptUserId, title, body, orderId);
    }

    /** Gửi cho chủ quán + quản lý trừ người vừa thao tác (vd. "Đơn ... đã hủy"). */
    @Transactional
    public void notifyManagers(Long exceptUserId, String title, String body, Long orderId) {
        notifyRoles(Set.of(Role.OWNER, Role.MANAGER), exceptUserId, title, body, orderId);
    }

    private void notifyRoles(Set<Role> roles, Long exceptUserId, String title, String body, Long orderId) {
        List<Long> ids = users.findByRoleInAndActiveTrue(roles).stream().map(User::getId)
                .filter(id -> !id.equals(exceptUserId)).toList();
        notifyUsers(ids, title, body, orderId);
    }

    // ===================== Hộp thư của người dùng =====================

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(AuthUser me, int limit) {
        int size = Math.max(1, Math.min(limit, 100));
        return notifications.findByUserIdOrderByIdDesc(me.id(), PageRequest.of(0, size)).stream()
                .map(NotificationResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(AuthUser me) {
        return notifications.countByUserIdAndReadFalse(me.id());
    }

    @Transactional
    public NotificationResponse markRead(AuthUser me, Long id) {
        Notification n = notifications.findById(id)
                .filter(x -> x.getUser().getId().equals(me.id()))   // chỉ được đánh dấu thông báo của chính mình
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy thông báo #" + id));
        n.setRead(true);
        return NotificationResponse.of(n);
    }

    @Transactional
    public int markAllRead(AuthUser me) {
        return notifications.markAllRead(me.id());
    }

    // ===================== Đăng ký thiết bị nhận FCM =====================

    /**
     * Lưu mã FCM của thiết bị. Mã đã tồn tại (vd. đăng xuất rồi đăng nhập tài khoản khác trên cùng máy)
     * thì chuyển sang người dùng hiện tại để thông báo không gửi nhầm người.
     */
    @Transactional
    public void registerDevice(AuthUser me, String token, String platform) {
        DeviceToken d = devices.findByToken(token).orElseGet(DeviceToken::new);
        d.setToken(token);
        d.setUser(users.getReferenceById(me.id()));
        d.setPlatform(platform == null || platform.isBlank() ? "WEB" : platform);
        devices.save(d);
    }

    /** Xóa mã khi người dùng đăng xuất trên thiết bị đó. */
    @Transactional
    public void unregisterDevice(AuthUser me, String token) {
        devices.deleteTokenOfUser(token, me.id());
    }
}
