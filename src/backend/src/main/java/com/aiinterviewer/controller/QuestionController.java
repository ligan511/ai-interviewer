package com.aiinterviewer.controller;

import com.aiinterviewer.common.ApiResponse;
import com.aiinterviewer.service.InterviewQuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/interviews/{sessionId}/questions")
@RequiredArgsConstructor
public class QuestionController {

    private final InterviewQuestionService questionService;
    private final com.aiinterviewer.mapper.UserMapper userMapper;

    @PostMapping("/next")
    public ApiResponse<Map<String, Object>> nextQuestion(@PathVariable Long sessionId,
                                                          @RequestParam(required = false) Long lastQuestionId,
                                                          Authentication auth) {
        Long userId = resolveUserId(auth);
        var question = questionService.generateNextQuestion(sessionId, userId, lastQuestionId);
        if (question == null) {
            return ApiResponse.error(409, "No more questions available");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("questionId", question.getId());
        data.put("parentQuestionId", question.getParentQuestionId());
        data.put("content", question.getContent());
        data.put("type", question.getQuestionType());
        data.put("difficulty", question.getDifficulty());
        data.put("targetSkill", question.getTargetSkill());
        data.put("reason", "考察相关知识点与项目应用能力");
        return ApiResponse.ok(data);
    }

    @GetMapping
    public ApiResponse<?> list(@PathVariable Long sessionId, Authentication auth) {
        Long userId = resolveUserId(auth);
        var questions = questionService.getQuestionsBySession(sessionId);
        return ApiResponse.ok(questions);
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
