package com.aiinterviewer.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName(value = "interview_report", autoResultMap = true)
public class InterviewReport {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private java.math.BigDecimal overallScore;
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private java.util.Map<String, Object> dimensionScores;
    private String summary;
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private java.util.List<String> strengths;
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private java.util.List<String> weaknesses;
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private java.util.List<String> suggestions;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public java.math.BigDecimal getOverallScore() { return overallScore; }
    public void setOverallScore(java.math.BigDecimal overallScore) { this.overallScore = overallScore; }
    public java.util.Map<String, Object> getDimensionScores() { return dimensionScores; }
    public void setDimensionScores(java.util.Map<String, Object> dimensionScores) { this.dimensionScores = dimensionScores; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public java.util.List<String> getStrengths() { return strengths; }
    public void setStrengths(java.util.List<String> strengths) { this.strengths = strengths; }
    public java.util.List<String> getWeaknesses() { return weaknesses; }
    public void setWeaknesses(java.util.List<String> weaknesses) { this.weaknesses = weaknesses; }
    public java.util.List<String> getSuggestions() { return suggestions; }
    public void setSuggestions(java.util.List<String> suggestions) { this.suggestions = suggestions; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
