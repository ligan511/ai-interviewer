package com.aiinterviewer.service;

import com.aiinterviewer.entity.InterviewAnswer;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Map;

public interface InterviewAnswerService extends IService<InterviewAnswer> {
    Map<String, Object> submitAnswer(Long sessionId, Long userId, Long questionId,
                                       String answerText, Integer clientDurationSeconds);
    Map<String, Object> submitAnswerWithAudio(Long sessionId, Long userId, Long questionId,
                                               String answerText, Integer clientDurationSeconds, String audioUrl);
    InterviewAnswer getAnswer(Long answerId);
    Map<String, Object> getEvaluation(Long answerId);
}
