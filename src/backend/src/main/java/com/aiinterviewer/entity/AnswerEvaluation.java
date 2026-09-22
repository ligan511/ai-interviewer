package com.aiinterviewer.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@TableName(value = "answer_evaluation", autoResultMap = true)
public class AnswerEvaluation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long answerId;
    private BigDecimal totalScore;
    private BigDecimal professionalScore;
    private BigDecimal logicScore;
    private BigDecimal completenessScore;
    private BigDecimal analysisScore;
    private BigDecimal expressionScore;
    private BigDecimal jobMatchScore;
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private List<String> strengths;
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private List<String> weaknesses;
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private List<String> suggestions;
    private String referenceAnswer;
    private String modelName;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAnswerId() { return answerId; }
    public void setAnswerId(Long answerId) { this.answerId = answerId; }
    public BigDecimal getTotalScore() { return totalScore; }
    public void setTotalScore(BigDecimal totalScore) { this.totalScore = totalScore; }
    public BigDecimal getProfessionalScore() { return professionalScore; }
    public void setProfessionalScore(BigDecimal professionalScore) { this.professionalScore = professionalScore; }
    public BigDecimal getLogicScore() { return logicScore; }
    public void setLogicScore(BigDecimal logicScore) { this.logicScore = logicScore; }
    public BigDecimal getCompletenessScore() { return completenessScore; }
    public void setCompletenessScore(BigDecimal completenessScore) { this.completenessScore = completenessScore; }
    public BigDecimal getAnalysisScore() { return analysisScore; }
    public void setAnalysisScore(BigDecimal analysisScore) { this.analysisScore = analysisScore; }
    public BigDecimal getExpressionScore() { return expressionScore; }
    public void setExpressionScore(BigDecimal expressionScore) { this.expressionScore = expressionScore; }
    public BigDecimal getJobMatchScore() { return jobMatchScore; }
    public void setJobMatchScore(BigDecimal jobMatchScore) { this.jobMatchScore = jobMatchScore; }
    public List<String> getStrengths() { return strengths; }
    public void setStrengths(List<String> strengths) { this.strengths = strengths; }
    public List<String> getWeaknesses() { return weaknesses; }
    public void setWeaknesses(List<String> weaknesses) { this.weaknesses = weaknesses; }
    public List<String> getSuggestions() { return suggestions; }
    public void setSuggestions(List<String> suggestions) { this.suggestions = suggestions; }
    public String getReferenceAnswer() { return referenceAnswer; }
    public void setReferenceAnswer(String referenceAnswer) { this.referenceAnswer = referenceAnswer; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
