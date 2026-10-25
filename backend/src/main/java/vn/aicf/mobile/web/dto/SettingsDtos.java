package vn.aicf.mobile.web.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** DTO cho thông tin quán (/api/shop) và cấu hình AI (/api/settings/ai). */
public final class SettingsDtos {

    private SettingsDtos() {
    }

    /**
     * GET /api/shop - thông tin công khai, app gọi lúc mở để biết tên quán và tính năng nào đang bật.
     *
     * @param openNow         đang trong giờ mở cửa không (đọc từ hours); null nếu không đọc được giờ
     * @param firebaseEnabled backend đã cấu hình Firebase (bật nút đăng nhập Google, thông báo đẩy)
     * @param aiMode          "LLM" nếu có khóa API AI, ngược lại "OFFLINE"
     */
    public record ShopInfo(String name, String address, String hours, Boolean openNow, boolean firebaseEnabled, String aiMode) {
    }

    /** PUT /api/settings/shop */
    public record ShopInfoRequest(@Size(max = 100) String name, @Size(max = 200) String address, @Size(max = 100) String hours) {
    }

    /**
     * PUT /api/settings/ai - Chủ quán nhập khóa API ngay trên app.
     * Trường khóa để trống = giữ nguyên khóa cũ (app không bao giờ nhận lại khóa để tránh lộ).
     */
    public record AiSettingsRequest(
            @Pattern(regexp = "gemini|claude", message = "chỉ nhận gemini hoặc claude") String provider,
            @Size(max = 300) String geminiApiKey,
            @Size(max = 100) String geminiModel,
            @Size(max = 300) String claudeApiKey,
            @Size(max = 100) String claudeModel) {
    }

    /** Cấu hình AI trả về: chỉ cho biết ĐÃ CÓ khóa hay chưa, không trả khóa. */
    public record AiSettingsResponse(String provider, String geminiModel, boolean geminiKeySet,
                                     String claudeModel, boolean claudeKeySet) {
    }
}
