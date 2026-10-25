package vn.aicf.mobile.service.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import vn.aicf.mobile.common.ApiException;
import vn.aicf.mobile.config.AuthUser;
import vn.aicf.mobile.domain.ChatMessage;
import vn.aicf.mobile.repository.ChatMessageRepository;
import vn.aicf.mobile.repository.UserRepository;
import vn.aicf.mobile.service.SettingsService;
import vn.aicf.mobile.web.dto.ChatDtos.ChatReply;
import vn.aicf.mobile.web.dto.ChatDtos.ChatStatus;
import vn.aicf.mobile.web.dto.ChatDtos.MessageResponse;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ĐIỀU PHỐI CHAT AI (tận dụng thiết kế "AssistantChatService" + "AssistantLlm" của dự án AICF):
 * <ol>
 *   <li>Lưu câu hỏi vào bảng chat_messages.</li>
 *   <li>Nếu đã có khóa API của dịch vụ đang chọn -> hỏi AI thật ({@link LlmProvider}).
 *       AI thật lỗi (mất mạng, hết hạn mức...) -> tự chuyển sang chế độ cơ bản và báo cho người dùng.</li>
 *   <li>Chưa có khóa -> trả lời bằng {@link OfflineAssistant}.</li>
 *   <li>Lưu câu trả lời.</li>
 * </ol>
 * Gọi AI qua mạng có thể mất vài giây nên KHÔNG đặt trong giao dịch CSDL; mỗi lần đọc/ghi chỉ mở 1 giao dịch ngắn
 * (TransactionTemplate) để SQLite không bị khóa lâu.
 */
@Slf4j
@Service
public class ChatService {

    private static final int MAX_STORED = 8000;
    private static final String[] DAY_NAMES = {"Thứ hai", "Thứ ba", "Thứ tư", "Thứ năm", "Thứ sáu", "Thứ bảy", "Chủ nhật"};

    private final ChatMessageRepository messages;
    private final UserRepository users;
    private final AiToolRegistry tools;
    private final OfflineAssistant offline;
    private final AiSettings aiSettings;
    private final SettingsService settings;
    private final TransactionTemplate tx;
    private final Map<String, LlmProvider> providers = new LinkedHashMap<>();

    /** Spring tự tiêm DANH SÁCH mọi bean LlmProvider (Gemini, Claude...) -> ghép thành bảng tra theo id. */
    public ChatService(ChatMessageRepository messages, UserRepository users, AiToolRegistry tools, OfflineAssistant offline,
                       AiSettings aiSettings, SettingsService settings, TransactionTemplate tx, List<LlmProvider> providers) {
        this.messages = messages;
        this.users = users;
        this.tools = tools;
        this.offline = offline;
        this.aiSettings = aiSettings;
        this.settings = settings;
        this.tx = tx;
        providers.forEach(p -> this.providers.put(p.id(), p));
    }

    public ChatReply chat(AuthUser user, String message) {
        String msg = message == null ? "" : message.trim();
        if (msg.isEmpty()) {
            throw ApiException.badRequest("Hãy nhập câu hỏi");
        }
        List<ChatTurn> history = recentTurns(user.id(), 12);
        save(user.id(), ChatMessage.USER, msg, null);

        String reply = null;
        String mode = "OFFLINE";
        String notice = null;
        LlmProvider provider = provider();
        if (provider != null && provider.hasKey()) {
            try {
                reply = provider.chat(new LlmRequest(user, history, msg, systemPrompt(user), tools.availableFor(user)));
                mode = "LLM";
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                log.warn("AI thật chưa trả lời được, dùng chế độ cơ bản: {}", e.toString());
                notice = "AI đang gặp sự cố (" + shorten(e.getMessage()) + ") nên mình trả lời bằng chế độ cơ bản.";
            }
        }
        if (reply == null) {
            reply = offline.answer(user, msg);
        }
        save(user.id(), ChatMessage.ASSISTANT, reply, mode);
        return new ChatReply(reply, mode, notice, offline.suggestions(user));
    }

    public ChatStatus status(AuthUser user) {
        LlmProvider p = provider();
        boolean llm = p != null && p.hasKey();
        return new ChatStatus(llm ? "LLM" : "OFFLINE", llm ? p.id() + " / " + p.model() : null, offline.suggestions(user));
    }

    /** Lịch sử chat (cũ trước, mới sau) để app hiển thị. */
    public List<MessageResponse> history(AuthUser user, int limit) {
        List<ChatMessage> latest = tx.execute(s ->
                messages.findByUserIdOrderByIdDesc(user.id(), PageRequest.of(0, Math.max(1, Math.min(limit, 200)))));
        List<MessageResponse> out = new ArrayList<>();
        for (ChatMessage m : latest == null ? List.<ChatMessage>of() : latest) {
            out.add(new MessageResponse(m.getId(), m.getRole(), m.getContent(), m.getMode(), m.getCreatedAt()));
        }
        Collections.reverse(out);
        return out;
    }

    public void clear(AuthUser user) {
        tx.executeWithoutResult(s -> messages.deleteAllOfUser(user.id()));
    }

    /** true nếu dịch vụ AI đang chọn đã có khóa (dùng cho /api/shop). */
    public boolean llmReady() {
        LlmProvider p = provider();
        return p != null && p.hasKey();
    }

    // ------------------------------------------------------------------

    private LlmProvider provider() {
        return providers.get(aiSettings.provider());
    }

    /** Lời dặn hệ thống: AI là ai, đang nói chuyện với ai, hôm nay ngày mấy, quy tắc trả lời. */
    private String systemPrompt(AuthUser user) {
        LocalDate today = LocalDate.now();
        String who = user.fullName() + " (" + user.role().label().toLowerCase() + " của quán)";
        return "Bạn là Trợ lý AI của quán cà phê \"" + settings.get(SettingsService.SHOP_NAME, "AICF Coffee")
                + "\", đang trò chuyện trong app bán hàng nội bộ của quán với " + who + ".\n"
                + "Hôm nay là " + DAY_NAMES[today.getDayOfWeek().getValue() - 1] + ", ngày " + today + " (giờ Việt Nam). Tiền tệ: đồng (đ).\n"
                + "Quy tắc:\n"
                + "- Trả lời bằng tiếng Việt, thân thiện, ngắn gọn (tối đa khoảng 120 từ), phù hợp màn hình điện thoại.\n"
                + "- Thực đơn, giá, đơn hàng, doanh thu: CHỈ dùng số liệu lấy từ công cụ, không bịa. Công cụ báo lỗi hoặc không có quyền thì nói thẳng.\n"
                + "- Cần khoảng ngày thì tự đổi \"hôm nay / hôm qua / tuần này\" thành ngày YYYY-MM-DD dựa vào ngày hôm nay.\n"
                + "- Bạn chỉ ĐỌC dữ liệu. Muốn gọi món, hủy đơn, thu tiền, sửa giá... thì hướng dẫn người dùng thao tác trong app.\n"
                + "- Không nhắc tới khóa API, mật khẩu hay chi tiết kỹ thuật nội bộ.";
    }

    private List<ChatTurn> recentTurns(Long userId, int n) {
        List<ChatMessage> latest = tx.execute(s -> messages.findByUserIdOrderByIdDesc(userId, PageRequest.of(0, n)));
        List<ChatTurn> out = new ArrayList<>();
        for (ChatMessage m : latest == null ? List.<ChatMessage>of() : latest) {
            out.add(new ChatTurn(m.getRole(), m.getContent()));
        }
        Collections.reverse(out);
        return out;
    }

    private void save(Long userId, String role, String content, String mode) {
        tx.executeWithoutResult(s -> {
            ChatMessage m = new ChatMessage();
            m.setUser(users.getReferenceById(userId));
            m.setRole(role);
            m.setContent(content.length() <= MAX_STORED ? content : content.substring(0, MAX_STORED - 1) + "…");
            m.setMode(mode);
            messages.save(m);
        });
    }

    private static String shorten(String s) {
        if (s == null) {
            return "lỗi không rõ";
        }
        return s.length() <= 140 ? s : s.substring(0, 139) + "…";
    }
}
