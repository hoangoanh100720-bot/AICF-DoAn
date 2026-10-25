package vn.aicf.mobile.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import vn.aicf.mobile.config.AuthUser;

import java.util.function.BiFunction;

/**
 * Một "CÔNG CỤ" (function / tool) mà AI được phép gọi để LẤY DỮ LIỆU THẬT từ hệ thống.
 *
 * <p>Ý tưởng (function calling): AI không biết thực đơn hay doanh thu của quán. Khi cần, AI trả về
 * "hãy gọi hàm get_menu" -> backend chạy hàm, gửi kết quả JSON lại cho AI -> AI dựa vào đó trả lời.
 * Nhờ vậy AI không bịa số, và chỉ ĐỌC được dữ liệu trong phạm vi quyền của người hỏi.
 *
 * @param name        tên hàm AI sẽ gọi, vd "get_menu"
 * @param description mô tả cho AI biết khi nào nên dùng
 * @param schema      JSON Schema mô tả tham số ("{}" = không có tham số)
 * @param managerOnly true = chỉ chủ quán / quản lý được dùng (vd. doanh thu)
 * @param handler     hàm Java thực thi: nhận tham số JSON + người hỏi, trả đối tượng bất kỳ (sẽ đổi sang JSON)
 */
public record AiTool(String name, String description, String schema, boolean managerOnly,
                     BiFunction<JsonNode, AuthUser, Object> handler) {
}
