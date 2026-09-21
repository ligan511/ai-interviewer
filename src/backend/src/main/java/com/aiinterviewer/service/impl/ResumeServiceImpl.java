package com.aiinterviewer.service.impl;

import com.aiinterviewer.entity.UserResume;
import com.aiinterviewer.mapper.UserResumeMapper;
import com.aiinterviewer.service.ResumeService;
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
public class ResumeServiceImpl extends ServiceImpl<UserResumeMapper, UserResume>
        implements ResumeService {

    @Value("${ai.upload.dir:./uploads}")
    private String uploadDir;

    private final UserResumeMapper resumeMapper;

    @Override
    public UserResume upload(MultipartFile file, Long userId, String fileName) {
        try {
            String ext = "";
            if (fileName != null && fileName.contains(".")) {
                ext = fileName.substring(fileName.lastIndexOf("."));
            }
            String safeName = UUID.randomUUID().toString() + ext;
            Path dir = Paths.get(uploadDir, "resumes");
            Files.createDirectories(dir);
            Path path = dir.resolve(safeName);
            file.transferTo(path.toFile());

            UserResume resume = new UserResume();
            resume.setUserId(userId);
            resume.setFileName(fileName != null ? fileName : safeName);
            resume.setFileUrl("/uploads/resumes/" + safeName);
            resumeMapper.insert(resume);
            return resume;
        } catch (IOException e) {
            log.error("Failed to upload resume: {}", e.getMessage());
            throw new RuntimeException("简历上传失败: " + e.getMessage(), e);
        }
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
