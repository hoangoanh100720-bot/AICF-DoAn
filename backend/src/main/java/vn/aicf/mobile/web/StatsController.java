package vn.aicf.mobile.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.aicf.mobile.service.StatsService;
import vn.aicf.mobile.web.dto.StatsDtos.Dashboard;

import java.time.LocalDate;

/** API THỐNG KÊ (OWNER, MANAGER): GET /api/stats/dashboard?date=YYYY-MM-DD (mặc định hôm nay). */
@Tag(name = "09. Thống kê", description = "Tổng quan doanh thu cho chủ quán / quản lý")
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService stats;

    public StatsController(StatsService stats) {
        this.stats = stats;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Doanh thu (tách tiền mặt / chuyển khoản), số đơn, món bán chạy trong ngày + doanh thu 7 ngày (OWNER, MANAGER)")
    public Dashboard dashboard(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return stats.dashboard(date == null ? LocalDate.now() : date);
    }
}
