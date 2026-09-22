package com.aiinterviewer.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName(value = "knowledge_chunk", autoResultMap = true)
public class KnowledgeChunk {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long documentId;
    private Integer chunkNo;
    private String content;
    private String embeddingRef;
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private java.util.Map<String, Object> metadataJson;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public Integer getChunkNo() { return chunkNo; }
    public void setChunkNo(Integer chunkNo) { this.chunkNo = chunkNo; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getEmbeddingRef() { return embeddingRef; }
    public void setEmbeddingRef(String embeddingRef) { this.embeddingRef = embeddingRef; }
    public java.util.Map<String, Object> getMetadataJson() { return metadataJson; }
    public void setMetadataJson(java.util.Map<String, Object> metadataJson) { this.metadataJson = metadataJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
