package vn.aicf.mobile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * KIỂM THỬ TÍCH HỢP REST API của app bán hàng (chủ quán / quản lý / nhân viên) trên CSDL SQLite thật (file tạm, xóa sau khi chạy):
 * khởi động cả ứng dụng Spring, gọi API qua MockMvc (giả lập HTTP, không mở cổng mạng).
 * Chạy:  mvnw test
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiFlowTest {

    private static Path dataDir;

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    /** Trỏ CSDL vào thư mục tạm riêng cho lần chạy test (không đụng dữ liệu thật). */
    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) throws Exception {
        dataDir = Files.createTempDirectory("aicf-test-");
        r.add("app.data-dir", () -> dataDir.toString().replace('\\', '/'));
        r.add("app.frontend-dir", () -> "");
        r.add("app.ai.gemini-api-key", () -> "");   // test luôn dùng chế độ cơ bản, không gọi AI thật
        r.add("app.ai.claude-api-key", () -> "");
    }

    // ---------- tiện ích ----------

    private String login(String user, String pass) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"" + pass + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("token").asText();
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private JsonNode read(MockHttpServletRequestBuilder b, int expectedStatus) throws Exception {
        String body = mvc.perform(b).andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return body.isEmpty() ? null : json.readTree(body);
    }

    // ---------- các bài kiểm thử ----------

    @Test
    void menuAndShopInfo() throws Exception {
        // Thông tin quán mở tự do (màn đăng nhập cần tên quán); thực đơn phải đăng nhập
        mvc.perform(get("/api/shop")).andExpect(status().isOk()).andExpect(jsonPath("$.aiMode").value("OFFLINE"))
                .andExpect(jsonPath("$.firebaseEnabled").value(false));
        mvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
        String staff = login("nhanvien", "nhanvien123");
        mvc.perform(auth(get("/api/categories"), staff)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4));
        mvc.perform(auth(get("/api/products"), staff).param("categoryId", "1")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoryName").value("Cà phê"))
                .andExpect(jsonPath("$[0].bestSeller").isBoolean());
    }

    @Test
    void securityRulesByRole() throws Exception {
        mvc.perform(get("/api/orders/active")).andExpect(status().isUnauthorized());
        String staff = login("nhanvien", "nhanvien123");
        mvc.perform(auth(get("/api/stats/dashboard"), staff)).andExpect(status().isForbidden());
        mvc.perform(auth(post("/api/categories"), staff).content("{\"name\":\"X\"}")).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/users"), staff)).andExpect(status().isForbidden());
        String manager = login("quanly", "quanly123");
        mvc.perform(auth(get("/api/stats/dashboard"), manager)).andExpect(status().isOk());
        mvc.perform(auth(get("/api/settings/ai"), manager)).andExpect(status().isForbidden());   // chỉ chủ quán
        mvc.perform(auth(get("/api/settings/ai"), login("chu", "chu123"))).andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"nhanvien\",\"password\":\"sai\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void tableOrderFullFlow() throws Exception {
        String staff = login("nhanvien", "nhanvien123");
        // Mở bill bàn 1: 2 cà phê sữa đá + 1 cà phê đen
        JsonNode order = read(auth(post("/api/orders"), staff).content("""
                {"orderType":"DINE_IN","tableId":1,"items":[{"productId":1,"quantity":2,"note":"ít đá"},{"productId":2,"quantity":1}]}
                """), 201);
        long id = order.path("id").asLong();
        assertThat(order.path("status").asText()).isEqualTo("NEW");
        assertThat(order.path("totalAmount").asLong()).isEqualTo(2 * 29000 + 25000);   // giá lấy từ CSDL
        assertThat(order.path("code").asText()).startsWith("DH").endsWith(String.format("%04d", id));
        assertThat(order.path("createdByName").asText()).isEqualTo("Nhân viên bán hàng");

        // Bàn đang có bill thì không mở bill thứ 2
        read(auth(post("/api/orders"), staff).content("{\"orderType\":\"DINE_IN\",\"tableId\":1,\"items\":[{\"productId\":1,\"quantity\":1}]}"), 409);

        // Gọi thêm 1 bạc xỉu, bớt cà phê đen gọi nhầm
        JsonNode more = read(auth(post("/api/orders/" + id + "/items"), staff).content("{\"items\":[{\"productId\":3,\"quantity\":1}]}"), 200);
        assertThat(more.path("items").size()).isEqualTo(3);
        long blackId = more.path("items").get(1).path("id").asLong();
        JsonNode less = read(auth(delete("/api/orders/" + id + "/items/" + blackId), staff), 200);
        assertThat(less.path("totalAmount").asLong()).isEqualTo(2 * 29000 + 32000);

        // Pha chế đi đúng luồng; nhảy cóc sang SERVED / chuyển tay COMPLETED bị từ chối
        read(auth(patch("/api/orders/" + id + "/status"), staff).content("{\"status\":\"SERVED\"}"), 409);
        read(auth(patch("/api/orders/" + id + "/status"), staff).content("{\"status\":\"COMPLETED\"}"), 400);
        for (String s : new String[]{"PREPARING", "READY"}) {
            read(auth(patch("/api/orders/" + id + "/status"), staff).content("{\"status\":\"" + s + "\"}"), 200);
        }
        // Đã pha: không bớt món được nữa; nhân viên không hủy được nữa
        read(auth(delete("/api/orders/" + id + "/items/" + less.path("items").get(0).path("id").asLong()), staff), 409);
        read(auth(patch("/api/orders/" + id + "/status"), staff).content("{\"status\":\"CANCELLED\"}"), 403);
        read(auth(patch("/api/orders/" + id + "/status"), staff).content("{\"status\":\"SERVED\"}"), 200);

        // Thu tiền mặt: đưa thiếu -> 400; đưa 100.000 -> thối 10.000, bill hoàn tất, bàn trống lại
        read(auth(post("/api/orders/" + id + "/pay"), staff).content("{\"paymentMethod\":\"CASH\",\"amountReceived\":50000}"), 400);
        JsonNode paid = read(auth(post("/api/orders/" + id + "/pay"), staff).content("{\"paymentMethod\":\"CASH\",\"amountReceived\":100000}"), 200);
        assertThat(paid.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(paid.path("changeAmount").asLong()).isEqualTo(100000 - 90000);
        read(auth(post("/api/orders/" + id + "/pay"), staff).content("{\"paymentMethod\":\"CASH\"}"), 409);
        read(auth(post("/api/orders"), staff).content("{\"orderType\":\"DINE_IN\",\"tableId\":1,\"items\":[{\"productId\":4,\"quantity\":1}]}"), 201);

        // Quản lý nhận thông báo đơn mới; tổng quan có doanh thu tiền mặt
        String manager = login("quanly", "quanly123");
        JsonNode unread = read(auth(get("/api/notifications/unread-count"), manager), 200);
        assertThat(unread.path("unread").asLong()).isGreaterThanOrEqualTo(1);
        JsonNode dash = read(auth(get("/api/stats/dashboard"), manager), 200);
        assertThat(dash.path("revenue").asLong()).isGreaterThanOrEqualTo(90000);
        assertThat(dash.path("cashRevenue").asLong()).isGreaterThanOrEqualTo(90000);
    }

    @Test
    void takeawayPrepaidAndCancelRules() throws Exception {
        String staff = login("nhanvien", "nhanvien123");
        // Mang đi trả trước bằng chuyển khoản: đã thu tiền nhưng chưa giao -> vẫn mở
        JsonNode o = read(auth(post("/api/orders"), staff).content("{\"orderType\":\"TAKEAWAY\",\"items\":[{\"productId\":5,\"quantity\":1}]}"), 201);
        long id = o.path("id").asLong();
        JsonNode paid = read(auth(post("/api/orders/" + id + "/pay"), staff).content("{\"paymentMethod\":\"BANK_TRANSFER\"}"), 200);
        assertThat(paid.path("status").asText()).isEqualTo("NEW");
        assertThat(paid.path("paid").asBoolean()).isTrue();
        read(auth(patch("/api/orders/" + id + "/status"), staff).content("{\"status\":\"CANCELLED\"}"), 409);   // đã thu tiền
        read(auth(patch("/api/orders/" + id + "/status"), staff).content("{\"status\":\"READY\"}"), 200);
        JsonNode done = read(auth(patch("/api/orders/" + id + "/status"), staff).content("{\"status\":\"SERVED\"}"), 200);
        assertThat(done.path("status").asText()).isEqualTo("COMPLETED");

        // Đơn chưa pha: nhân viên hủy được; đơn đang pha: chỉ quản lý hủy được
        long a = read(auth(post("/api/orders"), staff).content("{\"orderType\":\"TAKEAWAY\",\"items\":[{\"productId\":6,\"quantity\":1}]}"), 201).path("id").asLong();
        read(auth(patch("/api/orders/" + a + "/status"), staff).content("{\"status\":\"CANCELLED\",\"cancelReason\":\"Khách đổi ý\"}"), 200);
        long b = read(auth(post("/api/orders"), staff).content("{\"orderType\":\"TAKEAWAY\",\"items\":[{\"productId\":6,\"quantity\":1}]}"), 201).path("id").asLong();
        read(auth(patch("/api/orders/" + b + "/status"), staff).content("{\"status\":\"PREPARING\"}"), 200);
        JsonNode c = read(auth(patch("/api/orders/" + b + "/status"), login("quanly", "quanly123")).content("{\"status\":\"CANCELLED\"}"), 200);
        assertThat(c.path("cancelReason").asText()).contains("Quản lý ca");
    }

    @Test
    void userManagementRules() throws Exception {
        String owner = login("chu", "chu123");
        String manager = login("quanly", "quanly123");
        // Quản lý tạo được nhân viên, không tạo được quản lý
        JsonNode lan = read(auth(post("/api/users"), manager).content("""
                {"username":"lan","password":"lan123","fullName":"Nguyễn Thị Lan","role":"STAFF"}
                """), 201);
        read(auth(post("/api/users"), manager).content("""
                {"username":"hung","password":"hung123","fullName":"Hùng","role":"MANAGER"}
                """), 403);
        read(auth(post("/api/users"), owner).content("""
                {"username":"lan","password":"lan123","fullName":"Trùng tên","role":"STAFF"}
                """), 409);
        // Nhân viên mới đăng nhập được; bị khóa thì không
        login("lan", "lan123");
        long lanId = lan.path("id").asLong();
        read(auth(put("/api/users/" + lanId), manager).content("{\"fullName\":\"Nguyễn Thị Lan\",\"role\":\"STAFF\",\"active\":false}"), 200);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"lan\",\"password\":\"lan123\"}")).andExpect(status().isForbidden());
        // Chủ quán không tự khóa / tự hạ quyền mình được
        long ownerId = read(auth(get("/api/users/me"), owner), 200).path("id").asLong();
        read(auth(put("/api/users/" + ownerId), owner).content("{\"fullName\":\"Chủ quán\",\"role\":\"STAFF\",\"active\":true}"), 400);
    }

    @Test
    void validationAndCrudRules() throws Exception {
        String staff = login("nhanvien", "nhanvien123");
        read(auth(post("/api/orders"), staff).content("{\"orderType\":\"TAKEAWAY\",\"items\":[]}"), 400);   // chưa chọn món
        read(auth(post("/api/orders"), staff).content("{\"orderType\":\"DINE_IN\",\"items\":[{\"productId\":1,\"quantity\":1}]}"), 400);

        String manager = login("quanly", "quanly123");
        read(auth(delete("/api/categories/1"), manager), 409);   // danh mục còn món
        JsonNode t = read(auth(post("/api/tables"), manager).content("{\"name\":\"Bàn test\",\"seats\":2,\"active\":true}"), 201);
        read(auth(post("/api/tables"), manager).content("{\"name\":\"Bàn test\",\"seats\":2,\"active\":true}"), 409); // trùng tên
        long tid = t.path("id").asLong();
        JsonNode upd = read(auth(put("/api/tables/" + tid), manager).content("{\"name\":\"Bàn test 2\",\"seats\":6,\"active\":false}"), 200);
        assertThat(upd.path("seats").asInt()).isEqualTo(6);
        read(auth(delete("/api/tables/" + tid), manager), 204);
        read(auth(get("/api/tables/" + tid), manager), 404);
    }

    @Test
    void chatAnswersInOfflineModeWithRealData() throws Exception {
        String manager = login("quanly", "quanly123");
        JsonNode r = read(auth(post("/api/chat"), manager).content("{\"message\":\"Thực đơn có gì?\"}"), 200);
        assertThat(r.path("mode").asText()).isEqualTo("OFFLINE");
        assertThat(r.path("reply").asText()).contains("Cà phê sữa đá");
        JsonNode history = read(auth(get("/api/chat"), manager), 200);
        assertThat(history.size()).isEqualTo(2);   // câu hỏi + câu trả lời được lưu vào chat_messages
        // Nhân viên hỏi doanh thu -> bị từ chối bằng lời, không lộ số
        JsonNode s = read(auth(post("/api/chat"), login("nhanvien", "nhanvien123")).content("{\"message\":\"Doanh thu hôm nay?\"}"), 200);
        assertThat(s.path("reply").asText()).contains("chỉ chủ quán");
    }

    @Test
    void shopOpeningHoursParser() {
        java.time.LocalTime noon = java.time.LocalTime.NOON;
        assertThat(vn.aicf.mobile.service.SettingsService.openNow("7:00 - 22:00 hằng ngày", noon)).isTrue();
        assertThat(vn.aicf.mobile.service.SettingsService.openNow("6h - 11h30", noon)).isFalse();
        assertThat(vn.aicf.mobile.service.SettingsService.openNow("18:00 - 2:00", java.time.LocalTime.of(1, 0))).isTrue();
        assertThat(vn.aicf.mobile.service.SettingsService.openNow("Liên hệ", noon)).isNull();
    }

    @Test
    void swaggerListsAll47Apis() throws Exception {
        JsonNode doc = read(get("/v3/api-docs"), 200);
        int ops = 0;
        for (JsonNode path : doc.path("paths")) {
            ops += path.size();
        }
        assertThat(ops).isEqualTo(47);
        assertThat(doc.path("tags").size()).isEqualTo(10);
        JsonNode list = doc.path("paths").path("/api/products").path("get");
        assertThat(list.path("summary").asText()).isEqualTo("Lấy danh sách món");
        assertThat(list.path("parameters").size()).isEqualTo(3);
        assertThat(doc.path("paths").path("/api/tables/{id}").path("delete").path("summary").asText()).isEqualTo("Xóa bàn (OWNER, MANAGER)");
    }
}
