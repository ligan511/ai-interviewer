package com.aiinterviewer.controller;

import com.aiinterviewer.common.ApiResponse;
import com.aiinterviewer.common.SecurityContext;
import com.aiinterviewer.controller.dto.CreateInterviewRequest;
import com.aiinterviewer.entity.InterviewSession;
import com.aiinterviewer.service.InterviewReportService;
import com.aiinterviewer.service.InterviewSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/interviews")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewSessionService sessionService;
    private final InterviewReportService reportService;
    private final SecurityContext securityContext;

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody CreateInterviewRequest req,
                                                    Authentication auth) {
        Long userId = resolveUserId(auth);
        InterviewSession session = sessionService.createSession(
                userId, req.getJobId(), req.getResumeId(),
                req.getType() != null ? req.getType() : "technical",
                req.getDifficulty() != null ? req.getDifficulty() : "medium",
                req.getQuestionLimit() != null ? req.getQuestionLimit() : 10,
                req.getDurationLimitSeconds() != null ? req.getDurationLimitSeconds() : 1800
        );
        Map<String, Object> data = Map.of(
                "sessionId", session.getId(),
                "status", session.getStatus()
        );
        return ApiResponse.ok(data);
    }

    @GetMapping("/{id}")
    public ApiResponse<InterviewSession> get(@PathVariable Long id, Authentication auth) {
        Long userId = resolveUserId(auth);
        InterviewSession session = sessionService.getSession(id);
        if (session == null || !userId.equals(session.getUserId())) {
            return ApiResponse.notFound();
        }
        return ApiResponse.ok(session);
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<Map<String, Object>> complete(@PathVariable Long id, Authentication auth) {
        Long userId = resolveUserId(auth);
        InterviewSession session = sessionService.completeSession(id, userId);
        // Generate report after completion
        try {
            reportService.generateReport(id);
        } catch (Exception e) {
            log.warn("Report generation failed: {}", e.getMessage());
        }
        return ApiResponse.ok(Map.of(
                "sessionId", session.getId(),
                "status", session.getStatus()
        ));
    }

    @GetMapping("/history")
    public ApiResponse<?> history(Authentication auth,
                                   @RequestParam(defaultValue = "1") int page,
                                   @RequestParam(defaultValue = "10") int pageSize) {
        Long userId = resolveUserId(auth);
        var sessions = sessionService.getHistory(userId, page, pageSize);
        long total = sessionService.countHistory(userId);
        return ApiResponse.ok(Map.of(
                "list", sessions,
                "total", total,
                "page", page,
                "pageSize", pageSize
        ));
    }

    private Long resolveUserId(Authentication auth) {
        return securityContext.resolveUserId(auth);
    }
}
