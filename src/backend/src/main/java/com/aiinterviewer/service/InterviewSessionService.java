package com.aiinterviewer.service;

import com.aiinterviewer.entity.InterviewSession;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface InterviewSessionService extends IService<InterviewSession> {
    InterviewSession createSession(Long userId, Long jobId, Long resumeId,
                                   String type, String difficulty,
                                   int questionLimit, int durationLimitSeconds);
    InterviewSession getSession(Long sessionId);
    InterviewSession startSession(Long sessionId, Long userId);
    InterviewSession completeSession(Long sessionId, Long userId);
    List<InterviewSession> getHistory(Long userId, int page, int pageSize);
    long countHistory(Long userId);
}
