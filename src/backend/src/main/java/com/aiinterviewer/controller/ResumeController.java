package com.aiinterviewer.controller;

import com.aiinterviewer.common.ApiResponse;
import com.aiinterviewer.common.FileUploadValidator;
import com.aiinterviewer.common.SecurityContext;
import com.aiinterviewer.entity.UserResume;
import com.aiinterviewer.service.ResumeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/resumes")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;
    private final SecurityContext securityContext;

    @PostMapping
    public ApiResponse<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "fileName", required = false) String fileName,
            Authentication auth) {
        Long userId = resolveUserId(auth);
        // 安全校验：扩展名白名单 + MIME 黑名单 + 大小限制
        FileUploadValidator.validateResume(file);
        UserResume resume = resumeService.upload(file, userId, fileName);
        return ApiResponse.ok(Map.of(
                "id", resume.getId(),
                "fileName", resume.getFileName(),
                "fileUrl", resume.getFileUrl(),
                "createdAt", resume.getCreatedAt()
        ));
    }

    @GetMapping
    public ApiResponse<List<UserResume>> list(Authentication auth) {
        Long userId = resolveUserId(auth);
        return ApiResponse.ok(resumeService.listByUser(userId));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserResume> get(@PathVariable Long id, Authentication auth) {
        Long userId = resolveUserId(auth);
        UserResume resume = resumeService.get(id, userId);
        if (resume == null) return ApiResponse.notFound();
        return ApiResponse.ok(resume);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, Authentication auth) {
        Long userId = resolveUserId(auth);
        resumeService.delete(id, userId);
        return ApiResponse.ok(null);
    }

    private Long resolveUserId(Authentication auth) {
        return securityContext.resolveUserId(auth);
    }
}
