package com.aiinterviewer.common;

import com.aiinterviewer.entity.AnswerEvaluation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0 数据质量：评分校验 + 加权和一致性重算的单元测试。
 */
class EvaluationValidatorTest {

    private AnswerEvaluation buildEval(double total, double pro, double logic, double com,
                                       double ana, double exp, double job, String ref) {
        AnswerEvaluation e = new AnswerEvaluation();
        e.setTotalScore(BigDecimal.valueOf(total));
        e.setProfessionalScore(BigDecimal.valueOf(pro));
        e.setLogicScore(BigDecimal.valueOf(logic));
        e.setCompletenessScore(BigDecimal.valueOf(com));
        e.setAnalysisScore(BigDecimal.valueOf(ana));
        e.setExpressionScore(BigDecimal.valueOf(exp));
        e.setJobMatchScore(BigDecimal.valueOf(job));
        e.setReferenceAnswer(ref);
        return e;
    }

    @Test
    void consistentWeightedSum_keepsTotal() {
        // 维度分加权和 = 80*0.3 + 80*0.2 + 80*0.15 + 80*0.15 + 80*0.1 + 80*0.1 = 80
        AnswerEvaluation e = buildEval(80, 80, 80, 80, 80, 80, 80, "有效的参考答案内容长度满足要求。");
        EvaluationValidator.validateAndNormalize(e);
        assertEquals(80, e.getTotalScore().intValue(), "一致时综合分应保持不变");
    }

    @Test
    void inconsistentTotal_isRecalculated() {
        // 维度分均为 80 → 加权和 = 80，但 totalScore 给了 99（偏差 > 2）→ 应重算为 80
        AnswerEvaluation e = buildEval(99, 80, 80, 80, 80, 80, 80, "有效的参考答案内容长度满足要求。");
        EvaluationValidator.validateAndNormalize(e);
        assertEquals(80, e.getTotalScore().intValue(), "偏差超容差时应按维度分加权和重算");
    }

    @Test
    void outOfRangeDimensions_areClamped() {
        // professionalScore = 101 → 应被 clamp 到 100
        AnswerEvaluation e = buildEval(0, 101, 50, 50, 50, 50, 50, "有效的参考答案内容长度满足要求。");
        EvaluationValidator.validateAndNormalize(e);
        assertEquals(100, e.getProfessionalScore().intValue(), "越界维度分应被 clamp 到 100");
    }

    @Test
    void nullDimensions_defaultToZero() {
        AnswerEvaluation e = new AnswerEvaluation();
        // 全部为 null
        EvaluationValidator.validateAndNormalize(e);
        assertEquals(0, e.getTotalScore().intValue());
        assertEquals(0, e.getProfessionalScore().intValue());
    }

    @Test
    void negativeScores_areClampedToZero() {
        AnswerEvaluation e = buildEval(0, -5, -3, 50, 50, 50, 50, "有效的参考答案内容长度满足要求。");
        EvaluationValidator.validateAndNormalize(e);
        assertEquals(0, e.getProfessionalScore().intValue());
        assertEquals(0, e.getLogicScore().intValue());
    }

    @Test
    void nullReferenceAnswer_isAllowed() {
        AnswerEvaluation e = buildEval(80, 80, 80, 80, 80, 80, 80, null);
        assertDoesNotThrow(() -> EvaluationValidator.validateAndNormalize(e));
    }
}
