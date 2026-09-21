package com.aiinterviewer.service;

import com.aiinterviewer.entity.KnowledgeDocument;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface KnowledgeService {
    KnowledgeDocument upload(Long jobId, MultipartFile file, String title);
    List<KnowledgeDocument> listByJob(Long jobId);
    List<KnowledgeDocument> listAll();
    void delete(Long id);
}
