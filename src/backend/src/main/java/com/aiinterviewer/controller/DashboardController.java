package com.aiinterviewer.controller;

import com.aiinterviewer.common.ApiResponse;
import com.aiinterviewer.common.SecurityContext;
import com.aiinterviewer.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final SecurityContext securityContext;
    private final com.aiinterviewer.mapper.InterviewSessionMapper sessionMapper;
    private final com.aiinterviewer.mapper.InterviewQuestionMapper questionMapper;
    private final com.aiinterviewer.mapper.InterviewAnswerMapper answerMapper;
    private final com.aiinterviewer.mapper.AnswerEvaluationMapper evaluationMapper;

    @GetMapping("/trends")
    public ApiResponse<Map<String, Object>> getTrends(Authentication auth) {
        Long userId = resolveUserId(auth);

        List<InterviewSession> sessions = sessionMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InterviewSession>()
                        .eq(InterviewSession::getUserId, userId)
                        .eq(InterviewSession::getStatus, "COMPLETED")
                        .orderByDesc(InterviewSession::getCreatedAt)
                        .last("LIMIT 10"));

        if (sessions.isEmpty()) {
            return ApiResponse.ok(Map.of("trends", Collections.emptyList(), "count", 0));
        }

        List<Map<String, Object>> trends = new ArrayList<>();
        for (InterviewSession session : sessions) {
            List<InterviewQuestion> questions = questionMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InterviewQuestion>()
                            .eq(InterviewQuestion::getSessionId, session.getId()));

            double professional = 0, logic = 0, completeness = 0;
            double analysis = 0, expression = 0, jobMatch = 0;
            int count = 0;

            for (InterviewQuestion q : questions) {
                InterviewAnswer answer = answerMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InterviewAnswer>()
                                .eq(InterviewAnswer::getQuestionId, q.getId()));
                if (answer == null) continue;
                AnswerEvaluation eval = evaluationMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AnswerEvaluation>()
                                .eq(AnswerEvaluation::getAnswerId, answer.getId()));
                if (eval == null || eval.getTotalScore() == null) continue;
                professional += eval.getProfessionalScore().doubleValue();
                logic += eval.getLogicScore().doubleValue();
                completeness += eval.getCompletenessScore().doubleValue();
                analysis += eval.getAnalysisScore().doubleValue();
                expression += eval.getExpressionScore().doubleValue();
                jobMatch += eval.getJobMatchScore().doubleValue();
                count++;
            }
            if (count == 0) continue;

            Map<String, Object> point = new HashMap<>();
            point.put("date", session.getCreatedAt().toString().substring(0, 10));
            point.put("totalScore", Math.round((professional + logic + completeness + analysis + expression + jobMatch) / (6 * count)));
            point.put("professionalScore", Math.round(professional / count));
            point.put("logicScore", Math.round(logic / count));
            point.put("completenessScore", Math.round(completeness / count));
            point.put("analysisScore", Math.round(analysis / count));
            point.put("expressionScore", Math.round(expression / count));
            point.put("jobMatchScore", Math.round(jobMatch / count));
            trends.add(point);
        }

        Collections.reverse(trends);
        return ApiResponse.ok(Map.of("trends", trends, "count", trends.size()));
    }

    private Long resolveUserId(Authentication auth) {
        return securityContext.resolveUserId(auth);
    }
}
