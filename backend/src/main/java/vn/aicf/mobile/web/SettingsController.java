package vn.aicf.mobile.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vn.aicf.mobile.service.SettingsService;
import vn.aicf.mobile.service.ai.AiSettings;
import vn.aicf.mobile.service.ai.ChatService;
import vn.aicf.mobile.service.firebase.FirebaseService;
import vn.aicf.mobile.web.dto.SettingsDtos.AiSettingsRequest;
import vn.aicf.mobile.web.dto.SettingsDtos.AiSettingsResponse;
import vn.aicf.mobile.web.dto.SettingsDtos.ShopInfo;
import vn.aicf.mobile.web.dto.SettingsDtos.ShopInfoRequest;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

/**
 * API HỆ THỐNG & CÀI ĐẶT.
 * <pre>
 *   GET /api/health          kiểm tra máy chủ còn sống (app dùng khi nhập địa chỉ máy chủ)   công khai
 *   GET /api/shop            thông tin quán + tính năng đang bật                               công khai
 *   PUT /api/settings/shop   sửa tên / địa chỉ / giờ mở cửa                                     OWNER
 *   GET /api/settings/ai     xem cấu hình AI (không trả khóa)                                   OWNER
 *   PUT /api/settings/ai     đổi dịch vụ AI, model, khóa API                                    OWNER
 * </pre>
 */
@Tag(name = "10. Hệ thống & cài đặt", description = "Kiểm tra máy chủ, thông tin quán; chủ quán sửa thông tin quán và cấu hình Trợ lý AI")
@RestController
public class SettingsController {

    private final SettingsService settings;
    private final AiSettings ai;
    private final ChatService chat;
    private final FirebaseService firebase;

    public SettingsController(SettingsService settings, AiSettings ai, ChatService chat, FirebaseService firebase) {
        this.settings = settings;
        this.ai = ai;
        this.chat = chat;
        this.firebase = firebase;
    }

    @GetMapping("/api/health")
    @SecurityRequirements
    @Operation(summary = "Kiểm tra máy chủ (app gọi khi nhập địa chỉ máy chủ)")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "time", LocalDateTime.now().toString());
    }

    @GetMapping("/api/shop")
    @SecurityRequirements
    @Operation(summary = "Thông tin quán, đang mở cửa không (openNow) và tính năng đang bật")
    public ShopInfo shop() {
        return new ShopInfo(
                settings.get(SettingsService.SHOP_NAME, "AICF Coffee"),
                settings.get(SettingsService.SHOP_ADDRESS, ""),
                settings.get(SettingsService.SHOP_HOURS, ""),
                SettingsService.openNow(settings.get(SettingsService.SHOP_HOURS, ""), LocalTime.now()),
                firebase.isEnabled(),
                chat.llmReady() ? "LLM" : "OFFLINE");
    }

    @PutMapping("/api/settings/shop")
    @Operation(summary = "Sửa thông tin quán (OWNER)")
    public ShopInfo updateShop(@Valid @RequestBody ShopInfoRequest r) {
        if (r.name() != null && !r.name().isBlank()) {
            settings.set(SettingsService.SHOP_NAME, r.name());
        }
        if (r.address() != null) {
            settings.set(SettingsService.SHOP_ADDRESS, r.address());
        }
        if (r.hours() != null) {
            settings.set(SettingsService.SHOP_HOURS, r.hours());
        }
        return shop();
    }

    @GetMapping("/api/settings/ai")
    @Operation(summary = "Cấu hình AI hiện tại (OWNER)")
    public AiSettingsResponse getAi() {
        return new AiSettingsResponse(ai.provider(), ai.geminiModel(), !ai.geminiKey().isBlank(),
                ai.claudeModel(), !ai.claudeKey().isBlank());
    }

    @PutMapping("/api/settings/ai")
    @Operation(summary = "Đổi cấu hình AI; khóa để trống = giữ nguyên (OWNER)")
    public AiSettingsResponse updateAi(@Valid @RequestBody AiSettingsRequest r) {
        if (r.provider() != null) {
            settings.set(SettingsService.AI_PROVIDER, r.provider());
        }
        if (r.geminiApiKey() != null && !r.geminiApiKey().isBlank()) {
            settings.set(SettingsService.AI_GEMINI_KEY, r.geminiApiKey());
        }
        if (r.geminiModel() != null && !r.geminiModel().isBlank()) {
            settings.set(SettingsService.AI_GEMINI_MODEL, r.geminiModel());
        }
        if (r.claudeApiKey() != null && !r.claudeApiKey().isBlank()) {
            settings.set(SettingsService.AI_CLAUDE_KEY, r.claudeApiKey());
        }
        if (r.claudeModel() != null && !r.claudeModel().isBlank()) {
            settings.set(SettingsService.AI_CLAUDE_MODEL, r.claudeModel());
        }
        return getAi();
    }
}
