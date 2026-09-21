package com.aiinterviewer.controller;

import com.aiinterviewer.common.ApiResponse;
import com.aiinterviewer.entity.KnowledgeDocument;
import com.aiinterviewer.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final com.aiinterviewer.mapper.UserMapper userMapper;

    @PostMapping
    public ApiResponse<Map<String, Object>> upload(
            @RequestParam("jobId") Long jobId,
            @RequestParam("title") String title,
            @RequestParam("file") MultipartFile file,
            Authentication auth) {
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
        String email = ((UserDetails) auth.getPrincipal()).getUsername();
        var user = userMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.aiinterviewer.entity.User>()
                .eq(com.aiinterviewer.entity.User::getEmail, email));
        if (user == null) throw new RuntimeException("User not found");
        return user.getId();
    }
}
