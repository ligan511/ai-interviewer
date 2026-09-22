package com.aiinterviewer.service.impl;

import com.aiinterviewer.entity.AnswerEvaluation;
import com.aiinterviewer.entity.InterviewQuestion;
import com.aiinterviewer.entity.InterviewReport;
import com.aiinterviewer.entity.InterviewSession;
import com.aiinterviewer.mapper.*;
import com.aiinterviewer.service.InterviewReportService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewReportServiceImpl extends ServiceImpl<InterviewReportMapper, InterviewReport>
        implements InterviewReportService {

    private final InterviewQuestionMapper questionMapper;
    private final InterviewAnswerMapper answerMapper;
    private final AnswerEvaluationMapper evaluationMapper;
    private final InterviewSessionMapper sessionMapper;

    @Override
    @Transactional
    public InterviewReport generateReport(Long sessionId) {
        InterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new RuntimeException("Session not found");
        }

        List<InterviewQuestion> questions = questionMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InterviewQuestion>()
                        .eq(InterviewQuestion::getSessionId, sessionId)
                        .orderByAsc(InterviewQuestion::getSequenceNo));

        // Calculate overall scores
        double totalScore = 0;
        double professionalTotal = 0;
        double logicTotal = 0;
        double completenessTotal = 0;
        double analysisTotal = 0;
        double expressionTotal = 0;
        double jobMatchTotal = 0;
        int count = 0;

        List<String> allStrengths = new ArrayList<>();
        List<String> allWeaknesses = new ArrayList<>();
        List<String> allSuggestions = new ArrayList<>();

        for (InterviewQuestion q : questions) {
            var answer = answerMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.aiinterviewer.entity.InterviewAnswer>()
                            .eq(com.aiinterviewer.entity.InterviewAnswer::getQuestionId, q.getId()));
            if (answer == null) continue;

            var evaluation = evaluationMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AnswerEvaluation>()
                            .eq(AnswerEvaluation::getAnswerId, answer.getId()));
            if (evaluation == null) continue;

            count++;
            totalScore += evaluation.getTotalScore().doubleValue();
            professionalTotal += evaluation.getProfessionalScore().doubleValue();
            logicTotal += evaluation.getLogicScore().doubleValue();
            completenessTotal += evaluation.getCompletenessScore().doubleValue();
            analysisTotal += evaluation.getAnalysisScore().doubleValue();
            expressionTotal += evaluation.getExpressionScore().doubleValue();
            jobMatchTotal += evaluation.getJobMatchScore().doubleValue();

            if (evaluation.getStrengths() != null) allStrengths.addAll(evaluation.getStrengths());
            if (evaluation.getWeaknesses() != null) allWeaknesses.addAll(evaluation.getWeaknesses());
            if (evaluation.getSuggestions() != null) allSuggestions.addAll(evaluation.getSuggestions());
        }

        if (count == 0) {
            return null;
        }

        double avgScore = totalScore / count;
        Map<String, Object> dimensionScores = new HashMap<>();
        dimensionScores.put("professionalScore", round(professionalTotal / count));
        dimensionScores.put("logicScore", round(logicTotal / count));
        dimensionScores.put("completenessScore", round(completenessTotal / count));
        dimensionScores.put("analysisScore", round(analysisTotal / count));
        dimensionScores.put("expressionScore", round(expressionTotal / count));
        dimensionScores.put("jobMatchScore", round(jobMatchTotal / count));

        InterviewReport report = new InterviewReport();
        report.setSessionId(sessionId);
        report.setOverallScore(BigDecimal.valueOf(round(avgScore)));
        report.setDimensionScores(dimensionScores);
        String summary = buildSummary(avgScore, questions.size());
        // 若所有评分均为兜底结果（总分全为 0），在摘要中追加提示
        if (count > 0 && totalScore == 0) {
            summary += " 注意：本次面试的 AI 评分服务不可用，所有评分均为系统兜底结果，仅供参考。";
        }
        report.setSummary(summary);
        report.setStrengths(distinctAll(allStrengths));
        report.setWeaknesses(distinctAll(allWeaknesses));
        report.setSuggestions(distinctAll(allSuggestions));
        report.setCreatedAt(LocalDateTime.now());
        // 幂等替换：同一 session 的报告可被多次重新生成（评分完成后数据更全），先删除旧报告再插入
        this.remove(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InterviewReport>()
                .eq(InterviewReport::getSessionId, sessionId));
        save(report);
        return report;
    }

    @Override
    public InterviewReport getReport(Long sessionId) {
        // 取最新一条（generateReport 为幂等替换，正常只有一条；防御性取最新）
        List<InterviewReport> list = list(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InterviewReport>()
                .eq(InterviewReport::getSessionId, sessionId)
                .orderByDesc(InterviewReport::getId));
        return list.isEmpty() ? null : list.get(0);
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

    private String buildSummary(double score, int questionCount) {
        String level = score >= 85 ? "优秀" : score >= 70 ? "良好" : score >= 60 ? "及格" : "待提高";
        return String.format("本次面试共回答 %d 道题，综合得分 %.0f 分，整体表现%s。", questionCount, score, level);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private List<String> distinctAll(List<String> items) {
        return new ArrayList<>(new LinkedHashSet<>(items));
    }
}
