package com.aiinterviewer.service;

import com.aiinterviewer.entity.InterviewQuestion;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface InterviewQuestionService extends IService<InterviewQuestion> {
    InterviewQuestion generateNextQuestion(Long sessionId, Long userId, Long lastQuestionId);
    List<InterviewQuestion> getQuestionsBySession(Long sessionId);
    InterviewQuestion getQuestion(Long questionId);
    Integer getNextSequenceNo(Long sessionId);
}
