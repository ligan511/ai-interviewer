package com.aiinterviewer.controller;

import com.aiinterviewer.common.ApiResponse;
import com.aiinterviewer.common.FileUploadValidator;
import com.aiinterviewer.common.SecurityContext;
import com.aiinterviewer.controller.dto.SubmitAnswerRequest;
import com.aiinterviewer.service.InterviewAnswerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/interviews/{sessionId}/answers")
@RequiredArgsConstructor
public class AnswerController {

    private final InterviewAnswerService answerService;
    private final SecurityContext securityContext;

    @Value("${ai.upload.dir:./uploads}")
    private String uploadDir;

    @PostMapping
    public ApiResponse<Map<String, Object>> submit(@PathVariable Long sessionId,
                                                     @Valid @RequestBody SubmitAnswerRequest req,
                                                     Authentication auth) {
        Long userId = resolveUserId(auth);
        var result = answerService.submitAnswer(sessionId, userId, req.getQuestionId(), req.getAnswerText(),
                req.getClientDurationSeconds());
        return ApiResponse.ok(Map.of(
                "answerId", result.get("answerId"),
                "evaluationStatus", result.get("evaluationStatus"),
                "nextAction", result.get("nextAction")
        ));
    }

    @PostMapping(value = "/audio", consumes = "multipart/form-data")
    public ApiResponse<Map<String, Object>> submitAudio(@PathVariable Long sessionId,
                                                         @RequestParam("questionId") Long questionId,
                                                         @RequestParam("audio") MultipartFile audioFile,
                                                         Authentication auth) {
        Long userId = resolveUserId(auth);
        // 安全校验：扩展名白名单 + MIME 黑名单 + 大小限制
        FileUploadValidator.validateAudio(audioFile);
        try {
            String ext = "";
            if (audioFile.getOriginalFilename() != null && audioFile.getOriginalFilename().contains(".")) {
                ext = audioFile.getOriginalFilename().substring(audioFile.getOriginalFilename().lastIndexOf("."));
            }
            String safeName = UUID.randomUUID().toString() + ext;
            Path dir = Paths.get(uploadDir, "audios");
            Files.createDirectories(dir);
            Path path = dir.resolve(safeName);
            audioFile.transferTo(path.toFile());

            // Store audio URL in DB via the answer service
            // We reuse submitAnswer but set audioUrl separately
            var result = answerService.submitAnswerWithAudio(sessionId, userId, questionId, null, null, "/uploads/audios/" + safeName);
            return ApiResponse.ok(Map.of(
                    "answerId", result.get("answerId"),
                    "evaluationStatus", result.get("evaluationStatus"),
                    "audioUrl", "/uploads/audios/" + safeName,
                    "nextAction", result.get("nextAction")
            ));
        } catch (IOException e) {
            return ApiResponse.error(500, "音频上传失败: " + e.getMessage());
        }
    }

    private Long resolveUserId(Authentication auth) {
        return securityContext.resolveUserId(auth);
    }
}

