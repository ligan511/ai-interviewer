package com.aiinterviewer.controller;

import com.aiinterviewer.common.ApiResponse;
import com.aiinterviewer.controller.dto.SubmitAnswerRequest;
import com.aiinterviewer.service.InterviewAnswerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
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
    private final com.aiinterviewer.mapper.UserMapper userMapper;

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
        String email = ((UserDetails) auth.getPrincipal()).getUsername();
        var user = userMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.aiinterviewer.entity.User>()
                .eq(com.aiinterviewer.entity.User::getEmail, email));
        if (user == null) {
            throw new RuntimeException("User not found");
        }
        return user.getId();
    }
}

