package vn.aicf.mobile.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.aicf.mobile.domain.AppSetting;
import vn.aicf.mobile.repository.AppSettingRepository;

import java.time.LocalTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Đọc / ghi cấu hình trong bảng APP_SETTINGS.
 * Quy tắc ưu tiên: giá trị trong CSDL (Admin sửa trên app) > giá trị mặc định truyền vào (từ application.yml).
 */
@Service
public class SettingsService {

    // Tên các khóa cấu hình dùng trong hệ thống (gom một chỗ, tránh gõ sai chuỗi)
    public static final String SHOP_NAME = "shop.name";
    public static final String SHOP_ADDRESS = "shop.address";
    public static final String SHOP_HOURS = "shop.hours";
    public static final String AI_PROVIDER = "ai.provider";
    public static final String AI_GEMINI_KEY = "ai.gemini_api_key";
    public static final String AI_GEMINI_MODEL = "ai.gemini_model";
    public static final String AI_CLAUDE_KEY = "ai.claude_api_key";
    public static final String AI_CLAUDE_MODEL = "ai.claude_model";

    private final AppSettingRepository repository;

    public SettingsService(AppSettingRepository repository) {
        this.repository = repository;
    }

    /** Lấy giá trị; chưa có hoặc rỗng thì trả {@code defaultValue}. */
    @Transactional(readOnly = true)
    public String get(String key, String defaultValue) {
        return repository.findById(key)
                .map(AppSetting::getValue)
                .filter(v -> v != null && !v.isBlank())
                .orElse(defaultValue == null ? "" : defaultValue);
    }

    /** Ghi (thêm mới hoặc cập nhật) 1 cấu hình. Giá trị null được lưu thành chuỗi rỗng. */
    @Transactional
    public void set(String key, String value) {
        AppSetting s = repository.findById(key).orElseGet(() -> new AppSetting(key, null));
        s.setValue(value == null ? "" : value.trim());
        repository.save(s);
    }

    /** Giờ mở cửa dạng "7:00 - 22:00" (lấy cặp giờ đầu tiên trong chuỗi). */
    private static final Pattern HOURS = Pattern.compile("(\\d{1,2})[:hH](\\d{2})?\\s*[-–]\\s*(\\d{1,2})[:hH](\\d{2})?");

    /**
     * Quán có đang trong giờ mở cửa không, đọc từ chuỗi giờ mở cửa ("7:00 - 22:00 hằng ngày", "6h - 23h30").
     * Mở qua đêm ("18:00 - 2:00") cũng đúng. Không đọc được giờ thì trả null (app không hiện nhãn).
     */
    public static Boolean openNow(String hours, LocalTime now) {
        if (hours == null) {
            return null;
        }
        Matcher m = HOURS.matcher(hours);
        if (!m.find()) {
            return null;
        }
        try {
            LocalTime open = LocalTime.of(Integer.parseInt(m.group(1)) % 24, m.group(2) == null ? 0 : Integer.parseInt(m.group(2)));
            LocalTime close = LocalTime.of(Integer.parseInt(m.group(3)) % 24, m.group(4) == null ? 0 : Integer.parseInt(m.group(4)));
            return close.isAfter(open)
                    ? !now.isBefore(open) && now.isBefore(close)
                    : !now.isBefore(open) || now.isBefore(close);   // mở qua nửa đêm
        } catch (java.time.DateTimeException e) {
            return null;   // vd "25:00"
        }
    }
}
