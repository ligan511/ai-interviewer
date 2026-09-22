package com.aiinterviewer.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName(value = "user_resume", autoResultMap = true)
public class UserResume {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String fileName;
    private String fileUrl;
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private java.util.Map<String, Object> parsedJson;
    private String summary;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }
    public java.util.Map<String, Object> getParsedJson() { return parsedJson; }
    public void setParsedJson(java.util.Map<String, Object> parsedJson) { this.parsedJson = parsedJson; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
