package com.aiinterviewer.service.impl;

import com.aiinterviewer.entity.InterviewQuestion;
import com.aiinterviewer.entity.InterviewSession;
import com.aiinterviewer.entity.Job;
import com.aiinterviewer.entity.UserResume;
import com.aiinterviewer.mapper.AnswerEvaluationMapper;
import com.aiinterviewer.mapper.InterviewAnswerMapper;
import com.aiinterviewer.mapper.InterviewQuestionMapper;
import com.aiinterviewer.mapper.InterviewSessionMapper;
import com.aiinterviewer.mapper.JobMapper;
import com.aiinterviewer.mapper.UserResumeMapper;
import com.aiinterviewer.service.InterviewQuestionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Service
public class InterviewQuestionServiceImpl extends ServiceImpl<InterviewQuestionMapper, InterviewQuestion>
        implements InterviewQuestionService {

    @Value("${ai.service.base-url}")
    private String aiServiceUrl;

    private final RestTemplate restTemplate;
    private final InterviewSessionMapper sessionMapper;
    private final JobMapper jobMapper;
    private final UserResumeMapper resumeMapper;
    private final InterviewAnswerMapper answerMapper;
    private final AnswerEvaluationMapper evaluationMapper;

    public InterviewQuestionServiceImpl(RestTemplate restTemplate, InterviewSessionMapper sessionMapper,
                                        JobMapper jobMapper,
                                        UserResumeMapper resumeMapper, InterviewAnswerMapper answerMapper,
                                        AnswerEvaluationMapper evaluationMapper) {
        this.restTemplate = restTemplate;
        this.sessionMapper = sessionMapper;
        this.jobMapper = jobMapper;
        this.resumeMapper = resumeMapper;
        this.answerMapper = answerMapper;
        this.evaluationMapper = evaluationMapper;
    }

    @Override
    public InterviewQuestion generateNextQuestion(Long sessionId, Long userId, Long lastQuestionId) {
        InterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null || !userId.equals(session.getUserId())) {
            log.warn("Session not found or unauthorized: sessionId={}, userId={}", sessionId, userId);
            return null;
        }
        long currentCount = count(new LambdaQueryWrapper<InterviewQuestion>()
                .eq(InterviewQuestion::getSessionId, sessionId));
        if (currentCount >= session.getQuestionLimit()) return null;

        // 幂等：若 lastQuestionId 之后已有题目（重试场景），直接返回下一道已有题，不再调用 LLM 重复生成
        if (lastQuestionId != null) {
            InterviewQuestion afterLast = getOne(new LambdaQueryWrapper<InterviewQuestion>()
                    .eq(InterviewQuestion::getSessionId, sessionId)
                    .gt(InterviewQuestion::getId, lastQuestionId)
                    .orderByAsc(InterviewQuestion::getId)
                    .last("LIMIT 1"));
            if (afterLast != null) {
                return afterLast;
            }
        }

        // Build resume context
        String resumeContext = "";
        if (session.getResumeId() != null) {
            UserResume resume = resumeMapper.selectById(session.getResumeId());
            if (resume != null && resume.getParsedJson() != null) {
                Map<String, Object> parsed = resume.getParsedJson();
                StringBuilder sb = new StringBuilder();
                if (parsed.get("summary") != null) sb.append("个人简介: ").append(parsed.get("summary")).append("\n");
                if (parsed.get("experience") != null) {
                    sb.append("工作经历:\n");
                    for (Object exp : (List<?>) parsed.get("experience")) {
                        sb.append("  - ").append(exp).append("\n");
                    }
                }
                if (parsed.get("skills") != null) sb.append("技能: ").append(parsed.get("skills")).append("\n");
                if (parsed.get("projects") != null) {
                    sb.append("项目经历:\n");
                    for (Object proj : (List<?>) parsed.get("projects")) {
                        sb.append("  - ").append(proj).append("\n");
                    }
                }
                resumeContext = sb.toString();
            }
        }

        // Build recent scores for difficulty adjustment
        List<Float> recentScores = new ArrayList<>();
        var questions = list(new LambdaQueryWrapper<InterviewQuestion>()
                .eq(InterviewQuestion::getSessionId, sessionId)
                .orderByDesc(InterviewQuestion::getSequenceNo)
                .last("LIMIT 5"));
        for (InterviewQuestion q : questions) {
            var answer = answerMapper.selectOne(
                    new LambdaQueryWrapper<com.aiinterviewer.entity.InterviewAnswer>()
                            .eq(com.aiinterviewer.entity.InterviewAnswer::getQuestionId, q.getId()));
            if (answer != null) {
                var eval = evaluationMapper.selectOne(new LambdaQueryWrapper<com.aiinterviewer.entity.AnswerEvaluation>()
                        .eq(com.aiinterviewer.entity.AnswerEvaluation::getAnswerId, answer.getId()));
                if (eval != null && eval.getTotalScore() != null) {
                    recentScores.add(eval.getTotalScore().floatValue());
                }
            }
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("sessionId", sessionId);
        requestBody.put("userId", userId);
        requestBody.put("jobId", session.getJobId());
        com.aiinterviewer.entity.Job job = jobMapper.selectById(session.getJobId());
        requestBody.put("jobName", job != null ? job.getName() : "");
        requestBody.put("resumeId", session.getResumeId());
        requestBody.put("resumeContext", resumeContext);
        requestBody.put("interviewType", session.getInterviewType());
        requestBody.put("difficulty", session.getDifficulty());
        requestBody.put("currentCount", currentCount);
        requestBody.put("lastQuestionId", lastQuestionId);
        requestBody.put("recentScores", recentScores);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    aiServiceUrl + "/internal/ai/question/generate", entity, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> data = response.getBody();
                InterviewQuestion question = new InterviewQuestion();
                question.setSessionId(sessionId);
                question.setParentQuestionId(lastQuestionId);
                question.setContent((String) data.get("content"));
                question.setQuestionType((String) data.getOrDefault("type", "technical"));
                question.setDifficulty((String) data.getOrDefault("difficulty", session.getDifficulty()));
                question.setTargetSkill((String) data.get("targetSkill"));
                question.setSequenceNo(getNextSequenceNo(sessionId));
                save(question);
                return question;
            }
        } catch (Exception e) {
            log.error("AI service call failed: {}", e.getMessage());
        }
        return generateFallbackQuestion(sessionId, lastQuestionId, session.getDifficulty(), (int) currentCount);
    }

    @Override
    public List<InterviewQuestion> getQuestionsBySession(Long sessionId) {
        return list(new LambdaQueryWrapper<InterviewQuestion>()
                .eq(InterviewQuestion::getSessionId, sessionId)
                .orderByAsc(InterviewQuestion::getSequenceNo));
    }

    @Override
    public InterviewQuestion getQuestion(Long questionId) {
        return getById(questionId);
    }

    @Override
    public Integer getNextSequenceNo(Long sessionId) {
        LambdaQueryWrapper<InterviewQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterviewQuestion::getSessionId, sessionId)
               .orderByDesc(InterviewQuestion::getSequenceNo)
               .last("LIMIT 1");
        InterviewQuestion last = getOne(wrapper);
        return last == null ? 1 : last.getSequenceNo() + 1;
    }

    private static final String[] FALLBACK_QUESTIONS = {
        "请介绍一下您最近负责的一个项目，以及您在其中的主要贡献。",
        "描述一次您在项目中遇到的最大技术挑战，以及您是如何解决的。",
        "如果您接手一个遗留系统，通常会如何分析和规划重构方案？",
        "请谈谈您对微服务架构的理解，以及它适合哪些场景、不适合哪些场景。",
        "请描述一下 RESTful API 的设计原则，以及如何设计一个规范的 API。",
        "在数据库设计中，什么是范式？什么情况下会故意反范式化？",
        "请谈谈分布式系统中 CAP 定理的含义，以及您理解的一致性模型有哪些。",
        "您如何评估一个新引入的技术选型？请描述您的决策流程。",
        "请描述一次您推动技术方案落地的完整经历，包括遇到的阻力及应对。",
        "如果在面试中遇到不会的问题，您会如何应对？",
        "您平时通过什么渠道跟踪技术发展趋势？最近关注了哪些新技术？",
        "请谈谈您对代码可读性和可维护性的理解，以及您如何保证代码质量。",
    };

    private InterviewQuestion generateFallbackQuestion(Long sessionId, Long lastQuestionId, String difficulty, int currentCount) {
        InterviewQuestion question = new InterviewQuestion();
        question.setSessionId(sessionId);
        question.setParentQuestionId(lastQuestionId);
        question.setContent(FALLBACK_QUESTIONS[currentCount % FALLBACK_QUESTIONS.length]);
        question.setQuestionType("technical");
        question.setDifficulty(difficulty != null ? difficulty : "medium");
        question.setTargetSkill("通用技术");
        question.setSequenceNo(getNextSequenceNo(sessionId));
        save(question);
        return question;
    }
}
