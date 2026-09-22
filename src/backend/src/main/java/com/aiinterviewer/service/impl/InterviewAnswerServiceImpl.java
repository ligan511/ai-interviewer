package com.aiinterviewer.service.impl;

import com.aiinterviewer.entity.*;
import com.aiinterviewer.mapper.*;
import com.aiinterviewer.service.InterviewAnswerService;
import com.aiinterviewer.service.InterviewReportService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewAnswerServiceImpl extends ServiceImpl<InterviewAnswerMapper, InterviewAnswer>
        implements InterviewAnswerService {

    private final InterviewAnswerMapper answerMapper;
    private final AnswerEvaluationMapper evaluationMapper;
    private final InterviewQuestionMapper questionMapper;
    private final InterviewSessionMapper sessionMapper;
    private final UserResumeMapper userResumeMapper;
    private final InterviewReportService reportService;
    private final RestTemplate restTemplate;
    private final Executor aiTaskExecutor;

    @Value("${ai.service.base-url}")
    private String aiServiceUrl;

    @Override
    @Transactional
    public Map<String, Object> submitAnswer(Long sessionId, Long userId, Long questionId,
                                              String answerText, Integer clientDurationSeconds) {
        InterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null || !userId.equals(session.getUserId())) {
            throw new RuntimeException("Session not found or not authorized");
        }

        // 幂等：同一题目已有答案时直接复用/更新，避免触发 uk_answer_question 唯一键冲突
        Long existingAnswerId = findExistingAnswerId(questionId);
        InterviewAnswer answer;
        if (existingAnswerId != null) {
            answer = answerMapper.selectById(existingAnswerId);
            if (answer == null || "EVALUATION_FAILED".equals(answer.getStatus())) {
                // 允许重新提交（更新原记录）
                answer.setAnswerText(answerText);
                answer.setDurationSeconds(clientDurationSeconds);
                answer.setStatus("SUBMITTED");
                answer.setSubmittedAt(LocalDateTime.now());
                answerMapper.updateById(answer);
                evaluateAnswerAsync(answer.getId(), questionId, answerText, session);
            } else if ("EVALUATED".equals(answer.getStatus())) {
                // 已完成评分，直接返回，不再重复评分
                return Map.of(
                        "answerId", answer.getId(),
                        "evaluationStatus", "EVALUATED",
                        "nextAction", "FOLLOW_UP"
                );
            } else {
                // 评分处理中（SUBMITTED），直接返回，避免重复插入
                return Map.of(
                        "answerId", answer.getId(),
                        "evaluationStatus", "PROCESSING",
                        "nextAction", "FOLLOW_UP"
                );
            }
        } else {
            answer = new InterviewAnswer();
            answer.setQuestionId(questionId);
            answer.setAnswerText(answerText);
            answer.setDurationSeconds(clientDurationSeconds);
            answer.setStatus("SUBMITTED");
            answer.setSubmittedAt(LocalDateTime.now());
            answerMapper.insert(answer);
            evaluateAnswerAsync(answer.getId(), questionId, answerText, session);
        }

        return Map.of(
                "answerId", answer.getId(),
                "evaluationStatus", "PROCESSING",
                "nextAction", "FOLLOW_UP"
        );
    }

    /** 语音答案无文字转写时，用占位文本供 AI 评分参考（P1 接入 ASR 后可替换为真实转写） */
    private static final String AUDIO_PLACEHOLDER = "（语音回答，已录音提交，暂未接入语音转文字，AI 将基于问题本身进行评分）";

    @Override
    @Transactional
    public Map<String, Object> submitAnswerWithAudio(Long sessionId, Long userId, Long questionId,
                                                      String answerText, Integer clientDurationSeconds, String audioUrl) {
        InterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null || !userId.equals(session.getUserId())) {
            throw new RuntimeException("Session not found or not authorized");
        }

        // 语音答案：answerText 为空时用占位文本，避免 AI 评分拿到空字符串
        String evalText = (answerText == null || answerText.isBlank()) ? AUDIO_PLACEHOLDER : answerText;

        // 幂等：同一题目已有答案时直接复用/更新，避免触发 uk_answer_question 唯一键冲突
        Long existingAnswerId = findExistingAnswerId(questionId);
        InterviewAnswer answer;
        if (existingAnswerId != null) {
            answer = answerMapper.selectById(existingAnswerId);
            if (answer == null || "EVALUATION_FAILED".equals(answer.getStatus())) {
                answer.setAnswerText(evalText);
                answer.setAudioUrl(audioUrl);
                answer.setDurationSeconds(clientDurationSeconds);
                answer.setStatus("SUBMITTED");
                answer.setSubmittedAt(LocalDateTime.now());
                answerMapper.updateById(answer);
                evaluateAnswerAsync(answer.getId(), questionId, evalText, session);
            } else if ("EVALUATED".equals(answer.getStatus())) {
                return Map.of(
                        "answerId", answer.getId(),
                        "evaluationStatus", "EVALUATED",
                        "nextAction", "FOLLOW_UP"
                );
            } else {
                return Map.of(
                        "answerId", answer.getId(),
                        "evaluationStatus", "PROCESSING",
                        "nextAction", "FOLLOW_UP"
                );
            }
        } else {
            answer = new InterviewAnswer();
            answer.setQuestionId(questionId);
            answer.setAnswerText(evalText);
            answer.setAudioUrl(audioUrl);
            answer.setDurationSeconds(clientDurationSeconds);
            answer.setStatus("SUBMITTED");
            answer.setSubmittedAt(LocalDateTime.now());
            answerMapper.insert(answer);
            evaluateAnswerAsync(answer.getId(), questionId, evalText, session);
        }

        return Map.of(
                "answerId", answer.getId(),
                "evaluationStatus", "PROCESSING",
                "nextAction", "FOLLOW_UP"
        );
    }

    /** 查询某题是否已有答案记录，有则返回 answerId，无则返回 null */
    private Long findExistingAnswerId(Long questionId) {
        InterviewAnswer existing = answerMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InterviewAnswer>()
                        .eq(InterviewAnswer::getQuestionId, questionId));
        return existing != null ? existing.getId() : null;
    }

    private void evaluateAnswerAsync(Long answerId, Long questionId, String answerText, InterviewSession session) {
        // 使用线程池替代裸 new Thread，避免无限制创建线程、便于资源管控
        aiTaskExecutor.execute(() -> {
            try {
                InterviewQuestion question = questionMapper.selectById(questionId);
                if (question == null) return;

                // 构建简历上下文：从 session.resumeId 读取并解析为文本
                String resumeContext = "";
                if (session.getResumeId() != null) {
                    try {
                        var resume = userResumeMapper.selectById(session.getResumeId());
                        if (resume != null && resume.getParsedJson() != null) {
                            java.util.Map<String, Object> parsed = resume.getParsedJson();
                            StringBuilder sb = new StringBuilder();
                            if (parsed.get("summary") != null) sb.append("个人简介: ").append(parsed.get("summary")).append("\n");
                            if (parsed.get("skills") != null) sb.append("技能: ").append(parsed.get("skills")).append("\n");
                            if (parsed.get("experience") != null) sb.append("工作经历: ").append(parsed.get("experience")).append("\n");
                            if (parsed.get("projects") != null) sb.append("项目经历: ").append(parsed.get("projects")).append("\n");
                            resumeContext = sb.toString();
                        }
                    } catch (Exception e) {
                        log.debug("Failed to load resume context: {}", e.getMessage());
                    }
                }

                Map<String, Object> evalResult = callAiEvaluate(question.getContent(), answerText, session.getJobId(), session.getDifficulty(), resumeContext);

                AnswerEvaluation evaluation = new AnswerEvaluation();
                evaluation.setAnswerId(answerId);
                Number ns = (Number) evalResult.getOrDefault("totalScore", 0);
                evaluation.setTotalScore(BigDecimal.valueOf(ns.doubleValue()));
                ns = (Number) evalResult.getOrDefault("professionalScore", 0);
                evaluation.setProfessionalScore(BigDecimal.valueOf(ns.doubleValue()));
                ns = (Number) evalResult.getOrDefault("logicScore", 0);
                evaluation.setLogicScore(BigDecimal.valueOf(ns.doubleValue()));
                ns = (Number) evalResult.getOrDefault("completenessScore", 0);
                evaluation.setCompletenessScore(BigDecimal.valueOf(ns.doubleValue()));
                ns = (Number) evalResult.getOrDefault("analysisScore", 0);
                evaluation.setAnalysisScore(BigDecimal.valueOf(ns.doubleValue()));
                ns = (Number) evalResult.getOrDefault("expressionScore", 0);
                evaluation.setExpressionScore(BigDecimal.valueOf(ns.doubleValue()));
                ns = (Number) evalResult.getOrDefault("jobMatchScore", 0);
                evaluation.setJobMatchScore(BigDecimal.valueOf(ns.doubleValue()));
                evaluation.setStrengths((List<String>) evalResult.getOrDefault("strengths", Collections.emptyList()));
                evaluation.setWeaknesses((List<String>) evalResult.getOrDefault("weaknesses", Collections.emptyList()));
                evaluation.setSuggestions((List<String>) evalResult.getOrDefault("suggestions", Collections.emptyList()));
                evaluation.setReferenceAnswer((String) evalResult.getOrDefault("referenceAnswer", ""));
                evaluation.setModelName((String) evalResult.get("modelName"));
                evaluation.setCreatedAt(LocalDateTime.now());
                evaluationMapper.insert(evaluation);

                InterviewAnswer answer = answerMapper.selectById(answerId);
                if (answer != null) {
                    answer.setStatus("EVALUATED");
                    answerMapper.updateById(answer);
                }
                log.info("Answer {} evaluated with total score: {}", answerId, evaluation.getTotalScore());
                // 所有题目评分完成后，自动触发报告生成（幂等：已存在则跳过）
                maybeGenerateReport(session.getId());
            } catch (Exception e) {
                log.error("Failed to evaluate answer: {}", e.getMessage());
                // 失败时标记为 EVALUATION_FAILED，避免答案永久卡在 SUBMITTED
                try {
                    AnswerEvaluation evaluation = new AnswerEvaluation();
                    evaluation.setAnswerId(answerId);
                    evaluation.setTotalScore(BigDecimal.valueOf(0));
                    evaluation.setProfessionalScore(BigDecimal.valueOf(0));
                    evaluation.setLogicScore(BigDecimal.valueOf(0));
                    evaluation.setCompletenessScore(BigDecimal.valueOf(0));
                    evaluation.setAnalysisScore(BigDecimal.valueOf(0));
                    evaluation.setExpressionScore(BigDecimal.valueOf(0));
                    evaluation.setJobMatchScore(BigDecimal.valueOf(0));
                    evaluation.setReferenceAnswer("评分失败，请重新提交答案");
                    evaluation.setCreatedAt(LocalDateTime.now());
                    evaluationMapper.insert(evaluation);

                    InterviewAnswer answer = answerMapper.selectById(answerId);
                    if (answer != null) {
                        answer.setStatus("EVALUATION_FAILED");
                        answerMapper.updateById(answer);
                    }
                } catch (Exception inner) {
                    log.error("Failed to mark answer {} as EVALUATION_FAILED: {}", answerId, inner.getMessage());
                }
            }
        });
    }

    /**
     * 检查 session 内所有题目是否都已完成评分（EVALUATED），若是且报告尚未生成，则触发生成。
     * 在异步线程中调用，幂等：报告已存在时跳过，避免重复生成。
     */
    private void maybeGenerateReport(Long sessionId) {
        try {
            List<InterviewQuestion> questions = questionMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InterviewQuestion>()
                            .eq(InterviewQuestion::getSessionId, sessionId));
            if (questions.isEmpty()) return;
            boolean allEvaluated = questions.stream().allMatch(q -> {
                InterviewAnswer a = answerMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InterviewAnswer>()
                                .eq(InterviewAnswer::getQuestionId, q.getId()));
                return a != null && "EVALUATED".equals(a.getStatus());
            });
            if (!allEvaluated) return;
            // 评分全部完成：重新生成报告（幂等替换，覆盖早期因评分未完成而生成的高概率报告）
            log.info("All answers evaluated for session {}, generating/updating report", sessionId);
            reportService.generateReport(sessionId);
        } catch (Exception e) {
            log.warn("Report check/generate failed for session {}: {}", sessionId, e.getMessage());
        }
    }

    private Map<String, Object> callAiEvaluate(String question, String answer, Long jobId, String difficulty,
                                               String resumeContext) {
        // Build knowledge context by calling retrieve endpoint
        String knowledgeContext = "";
        try {
            Map<String, Object> retrieveBody = new HashMap<>();
            retrieveBody.put("question", question);
            retrieveBody.put("answer", answer);
            retrieveBody.put("job_skills", Collections.emptyList());
            HttpHeaders retrieveHeaders = new HttpHeaders();
            retrieveHeaders.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> retrieveEntity = new HttpEntity<>(retrieveBody, retrieveHeaders);
            ResponseEntity<Map> retrieveResp = restTemplate.postForEntity(
                    aiServiceUrl + "/internal/ai/knowledge/retrieve", retrieveEntity, Map.class);
            // 修复 RAG 链路：读取 retrieve 返回值，提取 top chunks 文本作为上下文
            if (retrieveResp.getStatusCode().is2xxSuccessful() && retrieveResp.getBody() != null) {
                Object chunksObj = retrieveResp.getBody().get("chunks");
                if (chunksObj instanceof List) {
                    List<?> chunks = (List<?>) chunksObj;
                    if (!chunks.isEmpty()) {
                        StringBuilder sb = new StringBuilder();
                        for (Object chunk : chunks) {
                            if (chunk instanceof Map) {
                                Object content = ((Map<?, ?>) chunk).get("content");
                                if (content != null) sb.append(content.toString()).append("\n");
                            }
                        }
                        if (sb.length() > 0) knowledgeContext = sb.toString();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Knowledge retrieval failed: {}", e.getMessage());
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("question", question);
        requestBody.put("answer", answer);
        requestBody.put("jobId", jobId);
        requestBody.put("difficulty", difficulty);
        requestBody.put("resumeContext", resumeContext == null ? "" : resumeContext);
        requestBody.put("knowledgeContext", knowledgeContext);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    aiServiceUrl + "/internal/ai/answer/evaluate", entity, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.warn("AI evaluation failed, using fallback: {}", e.getMessage());
        }

        Map<String, Object> fallback = new HashMap<>();
        int baseScore = Math.min(100, 50 + answer.length() / 10);
        fallback.put("totalScore", baseScore);
        fallback.put("professionalScore", Math.max(0, baseScore));
        fallback.put("logicScore", Math.max(0, baseScore - 5));
        fallback.put("completenessScore", Math.max(0, baseScore - 3));
        fallback.put("analysisScore", Math.max(0, baseScore));
        fallback.put("expressionScore", Math.max(0, baseScore - 2));
        fallback.put("jobMatchScore", Math.max(0, baseScore + 2));
        fallback.put("strengths", Collections.singletonList("回答内容较完整，覆盖了基本要点"));
        fallback.put("weaknesses", Collections.singletonList("由于 AI 评分服务暂不可用，本次评分为系统兜底结果，详细维度分析请参考参考答案"));
        fallback.put("suggestions", Collections.singletonList("建议结合 STAR 法则（情境、任务、行动、结果）组织回答，并增加具体项目案例支撑"));
        // 兜底时保留用户原始回答作为参考，至少让报告有实际内容
        String truncatedAnswer = answer.length() > 300 ? answer.substring(0, 300) + "..." : answer;
        fallback.put("referenceAnswer", "（AI 评分服务暂不可用，以下为系统兜底评分，您的原始回答）\n" + truncatedAnswer);
        return fallback;
    }

    @Override
    public InterviewAnswer getAnswer(Long answerId) {
        return answerMapper.selectById(answerId);
    }

    @Override
    public Map<String, Object> getEvaluation(Long answerId) {
        AnswerEvaluation evaluation = evaluationMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AnswerEvaluation>()
                .eq(AnswerEvaluation::getAnswerId, answerId));
        if (evaluation == null) return null;

        Map<String, Object> result = new HashMap<>();
        result.put("totalScore", evaluation.getTotalScore());
        result.put("professionalScore", evaluation.getProfessionalScore());
        result.put("logicScore", evaluation.getLogicScore());
        result.put("completenessScore", evaluation.getCompletenessScore());
        result.put("analysisScore", evaluation.getAnalysisScore());
        result.put("expressionScore", evaluation.getExpressionScore());
        result.put("jobMatchScore", evaluation.getJobMatchScore());
        result.put("strengths", evaluation.getStrengths());
        result.put("weaknesses", evaluation.getWeaknesses());
        result.put("suggestions", evaluation.getSuggestions());
        result.put("referenceAnswer", evaluation.getReferenceAnswer());
        return result;
    }
}
