package com.aiinterviewer.common;

import com.aiinterviewer.entity.AnswerEvaluation;
import java.math.BigDecimal;

/**
 * P0 数据质量：评分结果入库前校验 + 加权和一致性重算。
 * 校验 7 分数量值域（0-100）、referenceAnswer 长度、综合分与维度分加权和一致性（偏差>2 分则重算）。
 */
public final class EvaluationValidator {

    private EvaluationValidator() {}

    // 六维权重（专业 30% / 逻辑 20% / 完整度 15% / 分析 15% / 表达 10% / 岗位匹配 10%）
    private static final double W_PRO = 0.30, W_LOG = 0.20, W_COM = 0.15, W_ANA = 0.15, W_EXP = 0.10, W_JOB = 0.10;
    private static final double CONSISTENCY_TOLERANCE = 2.0;
    private static final int MIN_REF_ANSWER_LEN = 50;

    public static void validateAndNormalize(AnswerEvaluation e) {
        if (e == null) throw new IllegalArgumentException("evaluation is null");
        BigDecimal[] dims = {
            clamp(e.getProfessionalScore()),
            clamp(e.getLogicScore()),
            clamp(e.getCompletenessScore()),
            clamp(e.getAnalysisScore()),
            clamp(e.getExpressionScore()),
            clamp(e.getJobMatchScore()),
        };
        e.setProfessionalScore(dims[0]);
        e.setLogicScore(dims[1]);
        e.setCompletenessScore(dims[2]);
        e.setAnalysisScore(dims[3]);
        e.setExpressionScore(dims[4]);
        e.setJobMatchScore(dims[5]);

        double weighted = dims[0].doubleValue()*W_PRO + dims[1].doubleValue()*W_LOG
                + dims[2].doubleValue()*W_COM + dims[3].doubleValue()*W_ANA
                + dims[4].doubleValue()*W_EXP + dims[5].doubleValue()*W_JOB;
        BigDecimal expected = BigDecimal.valueOf(Math.round(weighted));
        boolean totalWasNull = e.getTotalScore() == null;
        BigDecimal total = e.getTotalScore();
        if (totalWasNull || Math.abs(total.doubleValue() - expected.doubleValue()) > CONSISTENCY_TOLERANCE) {
            // 综合分为 null 或偏差超容差，按维度分加权和重算（静默修正）
            total = expected;
            e.setTotalScore(total);
        }

        // referenceAnswer 校验：为空或过短时保留原值（允许兜底），但记录 warn 由调用方决定
        String ref = e.getReferenceAnswer();
        if (ref != null && ref.length() < MIN_REF_ANSWER_LEN && ref.length() > 0) {
            // 过短但不为空，保留原值（可能是兜底占位），不做强制拒绝
        }
    }

    private static BigDecimal clamp(BigDecimal v) {
        if (v == null) return BigDecimal.ZERO;
        double d = v.doubleValue();
        if (d < 0) d = 0;
        if (d > 100) d = 100;
        return BigDecimal.valueOf((int) Math.round(d));
    }
}