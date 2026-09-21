package com.aiinterviewer.service;

import com.aiinterviewer.entity.InterviewReport;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Map;

public interface InterviewReportService extends IService<InterviewReport> {
    InterviewReport generateReport(Long sessionId);
    InterviewReport getReport(Long sessionId);
    Map<String, Object> getEvaluation(Long answerId);
}
