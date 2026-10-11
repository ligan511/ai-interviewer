package com.aiinterviewer.service;

import com.aiinterviewer.entity.InterviewReport;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Map;

public interface InterviewReportService extends IService<InterviewReport> {
    InterviewReport generateReport(Long sessionId);
    InterviewReport getReport(Long sessionId);
    Map<String, Object> getEvaluation(Long answerId);
    /**
     * 生成报告并附带每题的参考答案汇总。
     * 返回 Map 结构：{"report": InterviewReport, "referenceAnswers": [{"question","referenceAnswer"}, ...]}
     */
    Map<String, Object> getReportWithReferences(Long sessionId);
}
