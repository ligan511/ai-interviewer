package com.aiinterviewer.service;

import com.aiinterviewer.entity.UserResume;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ResumeService {
    UserResume upload(MultipartFile file, Long userId, String fileName);
    List<UserResume> listByUser(Long userId);
    UserResume get(Long id, Long userId);
    void delete(Long id, Long userId);
}
