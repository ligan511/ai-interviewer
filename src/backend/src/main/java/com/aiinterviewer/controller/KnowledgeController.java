package com.aiinterviewer.controller;

import com.aiinterviewer.common.ApiResponse;
import com.aiinterviewer.common.FileUploadValidator;
import com.aiinterviewer.common.SecurityContext;
import com.aiinterviewer.entity.KnowledgeDocument;
import com.aiinterviewer.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final SecurityContext securityContext;

    @PostMapping
    public ApiResponse<Map<String, Object>> upload(
            @RequestParam("jobId") Long jobId,
            @RequestParam("title") String title,
            @RequestParam("file") MultipartFile file,
            Authentication auth) {
        // 安全校验：扩展名白名单 + MIME 黑名单 + 大小限制
        FileUploadValidator.validateKnowledge(file);
        KnowledgeDocument doc = knowledgeService.upload(jobId, file, title);
        return ApiResponse.ok(Map.of(
                "id", doc.getId(),
                "jobId", doc.getJobId(),
                "title", doc.getTitle(),
                "status", doc.getStatus(),
                "fileUrl", doc.getFileUrl()
        ));
    }

    @GetMapping
    public ApiResponse<List<KnowledgeDocument>> list(
            @RequestParam(required = false) Long jobId,
            Authentication auth) {
        if (jobId != null) {
            return ApiResponse.ok(knowledgeService.listByJob(jobId));
        }
        return ApiResponse.ok(knowledgeService.listAll());
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, Authentication auth) {
        knowledgeService.delete(id);
        return ApiResponse.ok(null);
    }

    private Long resolveUserId(Authentication auth) {
        return securityContext.resolveUserId(auth);
    }
}
