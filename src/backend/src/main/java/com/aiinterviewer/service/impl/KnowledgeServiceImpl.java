package com.aiinterviewer.service.impl;

import com.aiinterviewer.entity.KnowledgeDocument;
import com.aiinterviewer.mapper.KnowledgeDocumentMapper;
import com.aiinterviewer.service.KnowledgeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl extends ServiceImpl<KnowledgeDocumentMapper, KnowledgeDocument>
        implements KnowledgeService {

    @Value("${ai.upload.dir:./uploads}")
    private String uploadDir;

    @Value("${ai.service.base-url}")
    private String aiServiceUrl;

    private final KnowledgeDocumentMapper documentMapper;
    private final RestTemplate restTemplate;
    private final Executor aiTaskExecutor;

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

            // 触发 AI 文档 Embedding（异步，避免阻塞上传响应）
            embedDocumentAsync(doc.getId(), path, title != null ? title : file.getOriginalFilename(), jobId);

            return doc;
        } catch (IOException e) {
            log.error("Failed to upload knowledge document: {}", e.getMessage());
            throw new RuntimeException("知识库文档上传失败: " + e.getMessage(), e);
        }
    }

    /**
     * 异步调用 AI 服务将文档切片并生成向量索引。
     * Embedding 失败不影响上传流程，仅更新状态便于前端提示。
     */
    private void embedDocumentAsync(Long docId, Path filePath, String title, Long jobId) {
        // 使用线程池替代裸 new Thread
        aiTaskExecutor.execute(() -> {
            try {
                String fileContent;
                try {
                    fileContent = Files.readString(filePath, StandardCharsets.UTF_8);
                } catch (Exception readErr) {
                    byte[] bytes = Files.readAllBytes(filePath);
                    fileContent = Base64.getEncoder().encodeToString(bytes);
                }

                Map<String, Object> requestBody = Map.of(
                        "content", fileContent,
                        "title", title,
                        "job_id", jobId,
                        "doc_id", docId
                );
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

                ResponseEntity<Map> response = restTemplate.postForEntity(
                        aiServiceUrl + "/internal/ai/knowledge/embed", entity, Map.class);
                if (response.getStatusCode().is2xxSuccessful()) {
                    // 更新文档状态为已索引
                    KnowledgeDocument update = new KnowledgeDocument();
                    update.setId(docId);
                    update.setStatus("INDEXED");
                    documentMapper.updateById(update);
                    log.info("Knowledge document {} embedded successfully", docId);
                }
            } catch (Exception e) {
                log.warn("Knowledge embedding failed for id={}: {}", docId, e.getMessage());
                KnowledgeDocument update = new KnowledgeDocument();
                update.setId(docId);
                update.setStatus("EMBED_FAILED");
                documentMapper.updateById(update);
            }
        });
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
