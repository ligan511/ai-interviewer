package com.aiinterviewer.service.impl;

import com.aiinterviewer.entity.InterviewSession;
import com.aiinterviewer.mapper.InterviewSessionMapper;
import com.aiinterviewer.service.InterviewSessionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class InterviewSessionServiceImpl extends ServiceImpl<InterviewSessionMapper, InterviewSession>
        implements InterviewSessionService {

    @Override
    public InterviewSession createSession(Long userId, Long jobId, Long resumeId,
                                           String type, String difficulty,
                                           int questionLimit, int durationLimitSeconds) {
        InterviewSession session = new InterviewSession();
        session.setUserId(userId);
        session.setJobId(jobId);
        session.setResumeId(resumeId);
        session.setInterviewType(type);
        session.setDifficulty(difficulty);
        session.setQuestionLimit(questionLimit);
        session.setDurationLimitSeconds(durationLimitSeconds);
        session.setStatus("CREATED");
        save(session);
        return session;
    }

    @Override
    public InterviewSession getSession(Long sessionId) {
        return getById(sessionId);
    }

    @Override
    public InterviewSession startSession(Long sessionId, Long userId) {
        LambdaQueryWrapper<InterviewSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterviewSession::getId, sessionId)
               .eq(InterviewSession::getUserId, userId);
        InterviewSession session = getOne(wrapper);
        if (session == null) throw new IllegalArgumentException("Session not found or not authorized");
        if (!"CREATED".equals(session.getStatus())) throw new IllegalStateException("Session not in CREATED status");
        session.setStatus("IN_PROGRESS");
        session.setStartedAt(LocalDateTime.now());
        updateById(session);
        return session;
    }

    @Override
    public InterviewSession completeSession(Long sessionId, Long userId) {
        LambdaQueryWrapper<InterviewSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterviewSession::getId, sessionId)
               .eq(InterviewSession::getUserId, userId);
        InterviewSession session = getOne(wrapper);
        if (session == null) throw new IllegalArgumentException("Session not found or not authorized");
        session.setStatus("COMPLETED");
        session.setEndedAt(LocalDateTime.now());
        updateById(session);
        return session;
    }

    @Override
    public List<InterviewSession> getHistory(Long userId, int page, int pageSize) {
        Page<InterviewSession> pageResult = page(new Page<>(page, pageSize),
                new LambdaQueryWrapper<InterviewSession>()
                        .eq(InterviewSession::getUserId, userId)
                        .orderByDesc(InterviewSession::getCreatedAt));
        return pageResult.getRecords();
    }

    @Override
    public long countHistory(Long userId) {
        return count(new LambdaQueryWrapper<InterviewSession>()
                .eq(InterviewSession::getUserId, userId));
    }
}
