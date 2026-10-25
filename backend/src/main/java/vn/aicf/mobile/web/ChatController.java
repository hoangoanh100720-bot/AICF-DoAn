package vn.aicf.mobile.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.aicf.mobile.config.AuthUser;
import vn.aicf.mobile.service.ai.ChatService;
import vn.aicf.mobile.web.dto.ChatDtos.ChatReply;
import vn.aicf.mobile.web.dto.ChatDtos.ChatRequest;
import vn.aicf.mobile.web.dto.ChatDtos.ChatStatus;
import vn.aicf.mobile.web.dto.ChatDtos.MessageResponse;

import java.util.List;

/**
 * API CHAT AI (cần đăng nhập; mọi vai trò dùng được, công cụ dữ liệu khác nhau theo vai trò:
 * nhân viên bán hàng không xem được doanh thu).
 * <pre>
 *   GET    /api/chat/status    AI thật hay chế độ cơ bản + câu hỏi gợi ý
 *   POST   /api/chat           gửi câu hỏi, nhận câu trả lời
 *   GET    /api/chat           lịch sử chat
 *   DELETE /api/chat           xóa lịch sử
 * </pre>
 */
@Tag(name = "08. Chat AI", description = "Trợ lý AI trả lời dựa trên dữ liệu thật của quán (Gemini / Claude, hoặc chế độ cơ bản khi chưa có khóa API)")
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chat;

    public ChatController(ChatService chat) {
        this.chat = chat;
    }

    @GetMapping("/status")
    @Operation(summary = "Trạng thái Trợ lý AI")
    public ChatStatus status(@AuthenticationPrincipal AuthUser me) {
        return chat.status(me);
    }

    @PostMapping
    @Operation(summary = "Hỏi Trợ lý AI")
    public ChatReply ask(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody ChatRequest request) {
        return chat.chat(me, request.message());
    }

    @GetMapping
    @Operation(summary = "Lịch sử chat (cũ trước, mới sau)")
    public List<MessageResponse> history(@AuthenticationPrincipal AuthUser me, @RequestParam(defaultValue = "50") int limit) {
        return chat.history(me, limit);
    }

    @DeleteMapping
    @Operation(summary = "Xóa lịch sử chat của tôi")
    public ResponseEntity<Void> clear(@AuthenticationPrincipal AuthUser me) {
        chat.clear(me);
        return ResponseEntity.noContent().build();
    }
}
