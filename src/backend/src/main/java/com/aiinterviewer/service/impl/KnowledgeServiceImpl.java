package com.aiinterviewer.service.impl;

import com.aiinterviewer.entity.KnowledgeDocument;
import com.aiinterviewer.mapper.KnowledgeDocumentMapper;
import com.aiinterviewer.service.KnowledgeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl extends ServiceImpl<KnowledgeDocumentMapper, KnowledgeDocument>
        implements KnowledgeService {

    @Value("${ai.upload.dir:./uploads}")
    private String uploadDir;

    private final KnowledgeDocumentMapper documentMapper;

    @Override
    public KnowledgeDocument upload(Long jobId, MultipartFile file, String title) {
        try {
            String ext = "";
            if (file.getOriginalFilename() != null && file.getOriginalFilename().contains(".")) {
                ext = file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf("."));
            }
            String safeName = UUID.randomUUID().toString() + ext;
            Path dir = Paths.get(uploadDir, "knowledge");
            Files.createDirectories(dir);
            Path path = dir.resolve(safeName);
            file.transferTo(path.toFile());

            KnowledgeDocument doc = new KnowledgeDocument();
            doc.setJobId(jobId);
            doc.setTitle(title != null ? title : file.getOriginalFilename());
            doc.setFileUrl("/uploads/knowledge/" + safeName);
            doc.setVersion("1.0");
            doc.setStatus("UPLOADED");
            documentMapper.insert(doc);
            return doc;
        } catch (IOException e) {
            log.error("Failed to upload knowledge document: {}", e.getMessage());
            throw new RuntimeException("知识库文档上传失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<KnowledgeDocument> listByJob(Long jobId) {
        return documentMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDocument>()
                        .eq(KnowledgeDocument::getJobId, jobId)
                        .orderByDesc(KnowledgeDocument::getCreatedAt));
    }

    @Override
    public List<KnowledgeDocument> listAll() {
        return documentMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDocument>()
                        .orderByDesc(KnowledgeDocument::getCreatedAt));
    }

    @Override
    public void delete(Long id) {
        documentMapper.deleteById(id);
    }
}
