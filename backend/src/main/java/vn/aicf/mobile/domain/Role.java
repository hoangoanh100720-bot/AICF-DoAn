package vn.aicf.mobile.domain;

/**
 * Vai trò trong quán (cột users.role). App chỉ dùng NỘI BỘ quán, không có tài khoản khách hàng.
 * <ul>
 *   <li>OWNER   - chủ quán: toàn quyền; cài đặt quán, khóa API AI, quản lý mọi tài khoản</li>
 *   <li>MANAGER - quản lý: thực đơn, bàn, thống kê, quản lý tài khoản nhân viên bán hàng</li>
 *   <li>STAFF   - nhân viên bán hàng: sơ đồ bàn, gọi món, pha chế, thanh toán</li>
 * </ul>
 * Spring Security dùng dạng "ROLE_OWNER"... (xem JwtAuthFilter).
 */
public enum Role {
    OWNER("Chủ quán"),
    MANAGER("Quản lý"),
    STAFF("Nhân viên bán hàng");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    /** Tên tiếng Việt để hiển thị. */
    public String label() {
        return label;
    }

    /** Chủ quán hoặc quản lý (được sửa thực đơn, xem thống kê...). */
    public boolean isManager() {
        return this == OWNER || this == MANAGER;
    }
}
