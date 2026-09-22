package com.aiinterviewer.controller;

import com.aiinterviewer.common.ApiResponse;
import com.aiinterviewer.service.InterviewReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReportController {

    private final InterviewReportService reportService;

    @GetMapping("/interviews/{sessionId}/report")
    public ApiResponse<?> getReport(@PathVariable Long sessionId) {
        // 始终按需重新生成，保证报告反映最新的评分数据（评分为异步，可能存在滞后）
        var report = reportService.generateReport(sessionId);
        if (report == null) {
            return ApiResponse.notFound();
        }
        return ApiResponse.ok(report);
    }

    @GetMapping("/answers/{answerId}/evaluation")
    public ApiResponse<?> getEvaluation(@PathVariable Long answerId) {
        var evaluation = reportService.getEvaluation(answerId);
        if (evaluation == null) {
            return ApiResponse.notFound();
        }
        return ApiResponse.ok(evaluation);
    }
}
