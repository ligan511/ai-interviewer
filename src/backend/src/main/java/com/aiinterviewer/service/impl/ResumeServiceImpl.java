package com.aiinterviewer.service.impl;

import com.aiinterviewer.entity.UserResume;
import com.aiinterviewer.mapper.UserResumeMapper;
import com.aiinterviewer.service.ResumeService;
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
public class ResumeServiceImpl extends ServiceImpl<UserResumeMapper, UserResume>
        implements ResumeService {

    @Value("${ai.upload.dir:./uploads}")
    private String uploadDir;

    @Value("${ai.service.base-url}")
    private String aiServiceUrl;

    private final UserResumeMapper resumeMapper;
    private final RestTemplate restTemplate;
    private final Executor aiTaskExecutor;

    @Override
    public UserResume upload(MultipartFile file, Long userId, String fileName) {
        try {
            String ext = "";
            if (fileName != null && fileName.contains(".")) {
                ext = fileName.substring(fileName.lastIndexOf("."));
            }
            String safeName = UUID.randomUUID().toString() + ext;
            Path dir = Paths.get(uploadDir, "resumes").toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Path path = dir.resolve(safeName);
            file.transferTo(path);

            UserResume resume = new UserResume();
            resume.setUserId(userId);
            resume.setFileName(fileName != null ? fileName : safeName);
            resume.setFileUrl("/uploads/resumes/" + safeName);
            resumeMapper.insert(resume);

            // 触发 AI 简历解析（异步，避免阻塞上传响应）
            parseResumeAsync(resume.getId(), path, ext);

            return resume;
        } catch (IOException e) {
            log.error("Failed to upload resume: {}", e.getMessage());
            throw new RuntimeException("简历上传失败: " + e.getMessage(), e);
        }
    }

    /**
     * 异步调用 AI 服务解析简历，将结构化结果写回 parsedJson 字段。
     * 解析失败不影响上传流程，仅记录日志。
     */
    private void parseResumeAsync(Long resumeId, Path filePath, String ext) {
        // 使用线程池替代裸 new Thread
        aiTaskExecutor.execute(() -> {
            try {
                // 读取文件内容并 base64 编码（兼容 .txt / .md 等文本简历）
                // 对 .pdf / .docx 等二进制格式，AI service 端需自行处理
                String fileContent;
                try {
                    fileContent = Files.readString(filePath, StandardCharsets.UTF_8);
                } catch (Exception readErr) {
                    // 二进制文件用 base64 包裹
                    byte[] bytes = Files.readAllBytes(filePath);
                    fileContent = Base64.getEncoder().encodeToString(bytes);
                }

                Map<String, Object> requestBody = Map.of(
                        "file_content", fileContent,
                        "file_type", ext.replace(".", "")
                );
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

                ResponseEntity<Map> response = restTemplate.postForEntity(
                        aiServiceUrl + "/internal/ai/resume/parse", entity, Map.class);
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    UserResume update = new UserResume();
                    update.setId(resumeId);
                    update.setParsedJson((Map<String, Object>) response.getBody());
                    Object summary = response.getBody().get("summary");
                    if (summary != null) update.setSummary(summary.toString());
                    resumeMapper.updateById(update);
                    log.info("Resume {} parsed successfully", resumeId);
                }
            } catch (Exception e) {
                log.warn("Resume parse failed for id={}: {}", resumeId, e.getMessage());
                // 解析失败时更新状态，便于前端提示
                UserResume update = new UserResume();
                update.setId(resumeId);
                update.setSummary("简历解析失败，请检查文件格式");
                resumeMapper.updateById(update);
            }
        });
    }

    @Override
    public List<UserResume> listByUser(Long userId) {
        return resumeMapper.selectList(
                new LambdaQueryWrapper<UserResume>()
                        .eq(UserResume::getUserId, userId)
                        .orderByDesc(UserResume::getCreatedAt));
    }

    @Override
    public UserResume get(Long id, Long userId) {
        UserResume resume = resumeMapper.selectById(id);
        if (resume == null || !userId.equals(resume.getUserId())) {
            return null;
        }
        return resume;
    }

    @Override
    public void delete(Long id, Long userId) {
        UserResume resume = resumeMapper.selectById(id);
        if (resume == null || !userId.equals(resume.getUserId())) {
            throw new RuntimeException("Resume not found or not authorized");
        }
        resumeMapper.deleteById(id);
    }
}
