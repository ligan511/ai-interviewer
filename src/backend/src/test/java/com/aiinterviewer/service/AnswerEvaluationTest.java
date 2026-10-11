package com.aiinterviewer.service;

import com.aiinterviewer.entity.InterviewAnswer;
import com.aiinterviewer.entity.InterviewQuestion;
import com.aiinterviewer.entity.InterviewSession;
import com.aiinterviewer.entity.UserResume;
import com.aiinterviewer.mapper.AnswerEvaluationMapper;
import com.aiinterviewer.mapper.InterviewAnswerMapper;
import com.aiinterviewer.mapper.InterviewQuestionMapper;
import com.aiinterviewer.mapper.InterviewSessionMapper;
import com.aiinterviewer.mapper.UserResumeMapper;
import com.aiinterviewer.service.impl.InterviewAnswerServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * P0 测试基线 + 并发与限流：InterviewAnswerServiceImpl 单元测试。
 * 覆盖：
 *   1. 幂等：同一题已有 EVALUATED 答案 → 直接返回不重复评分
 *   2. 幂等：同一题已有 SUBMITTED 答案 → 返回 PROCESSING 不重复插入
 *   3. 新答案提交 → 插入并触发异步评分
 *   4. 会话归属校验：非本用户会话 → 抛异常
 *   5. AI 服务熔断：连续 5 次失败后熔断打开，跳过 LLM 调用
 *   6. AI 故障 fallback：AI 不可达时用 Java 启发式评分，不抛异常
 */
@ExtendWith(MockitoExtension.class)
class AnswerEvaluationTest {

    @Mock
    private InterviewAnswerMapper answerMapper;
    @Mock
    private AnswerEvaluationMapper evaluationMapper;
    @Mock
    private InterviewQuestionMapper questionMapper;
    @Mock
    private InterviewSessionMapper sessionMapper;
    @Mock
    private UserResumeMapper userResumeMapper;
    @Mock
    private com.aiinterviewer.service.InterviewReportService reportService;
    @Mock
    private RestTemplate restTemplate;
    @Mock
    private Executor aiTaskExecutor;

    private InterviewAnswerServiceImpl service;

    private InterviewSession session;
    private InterviewQuestion question;

    @BeforeEach
    void setUp() {
        // @InjectMocks 无法注入 @Value 字段 aiServiceUrl，手动构造 + 反射注入
        service = new InterviewAnswerServiceImpl(
                answerMapper, evaluationMapper, questionMapper, sessionMapper,
                userResumeMapper, reportService, restTemplate, aiTaskExecutor);
        ReflectionTestUtils.setField(service, "aiServiceUrl", "http://localhost:8001");

        session = new InterviewSession();
        session.setId(1L);
        session.setUserId(100L);
        session.setJobId(1L);
        session.setDifficulty("medium");
        session.setResumeId(null);

        question = new InterviewQuestion();
        question.setId(50L);
        question.setSessionId(1L);
        question.setContent("什么是 Redis？");
        question.setSequenceNo(1);
    }

    /** 让 aiTaskExecutor 同步执行任务，避免测试中等待异步。 */
    private void syncExecutor() {
        doAnswer(inv -> {
            Runnable r = inv.getArgument(0);
            r.run();
            return null;
        }).when(aiTaskExecutor).execute(any(Runnable.class));
    }

    @Test
    void submitAnswer_sessionNotOwned_throws() {
        when(sessionMapper.selectById(1L)).thenReturn(session);
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.submitAnswer(1L, 999L, 50L, "回答", null));
        assertTrue(ex.getMessage().contains("not authorized"));
    }

    @Test
    void submitAnswer_existingEvaluated_noDuplicate() {
        InterviewAnswer existing = new InterviewAnswer();
        existing.setId(200L);
        existing.setQuestionId(50L);
        existing.setStatus("EVALUATED");
        when(sessionMapper.selectById(1L)).thenReturn(session);
        when(answerMapper.selectOne(any())).thenReturn(existing);
        when(answerMapper.selectById(200L)).thenReturn(existing);

        Map<String, Object> result = service.submitAnswer(1L, 100L, 50L, "新回答", null);

        assertEquals("EVALUATED", result.get("evaluationStatus"));
        // 不应再插入新答案
        verify(answerMapper, never()).insert(any());
        verify(evaluationMapper, never()).insert(any());
    }

    @Test
    void submitAnswer_existingProcessing_noDuplicate() {
        InterviewAnswer existing = new InterviewAnswer();
        existing.setId(201L);
        existing.setQuestionId(50L);
        existing.setStatus("SUBMITTED");
        when(sessionMapper.selectById(1L)).thenReturn(session);
        when(answerMapper.selectOne(any())).thenReturn(existing);
        when(answerMapper.selectById(201L)).thenReturn(existing);

        Map<String, Object> result = service.submitAnswer(1L, 100L, 50L, "回答", null);

        assertEquals("PROCESSING", result.get("evaluationStatus"));
        verify(answerMapper, never()).insert(any());
    }

    @Test
    void submitAnswer_newAnswer_triggersInsert() {
        syncExecutor();
        when(sessionMapper.selectById(1L)).thenReturn(session);
        when(answerMapper.selectOne(any())).thenReturn(null);
        when(questionMapper.selectById(50L)).thenReturn(question);
        // 模拟 insert 回填自增 ID，避免 answer.getId() 为 null 导致 NPE
        doAnswer(inv -> {
            InterviewAnswer a = inv.getArgument(0);
            a.setId(300L);
            return 1;
        }).when(answerMapper).insert(any(InterviewAnswer.class));
        when(answerMapper.selectById(300L)).thenAnswer(inv -> {
            InterviewAnswer a = new InterviewAnswer();
            a.setId(300L);
            a.setQuestionId(50L);
            a.setStatus("SUBMITTED");
            return a;
        });
        // 让 AI 调用抛异常以走 Java 端兜底评分（无需真实 AI 服务）
        doThrow(new ResourceAccessException("AI unreachable"))
                .when(restTemplate).postForEntity(anyString(), any(HttpEntity.class), eq(Map.class));

        Map<String, Object> result = service.submitAnswer(1L, 100L, 50L, "这是一个测试回答内容，足够长以触发启发式评分。", null);

        assertEquals("PROCESSING", result.get("evaluationStatus"));
        assertNotNull(result.get("answerId"));
        verify(answerMapper, times(1)).insert(any(InterviewAnswer.class));
    }

    /**
     * P0 并发与限流：AI 服务熔断器行为验证。
     * 通过反射访问 service 内部的 AiServiceCircuitBreaker，
     * 连续记录 5 次失败后 isOpen() 应返回 true。
     */
    @Test
    void circuitBreaker_opensAfterFiveFailures() throws Exception {
        // 通过 service 实例访问 inner class（绕过包私有访问限制）
        InterviewAnswerServiceImpl s = newInterviewAnswerServiceImpl();
        var clazz = Class.forName("com.aiinterviewer.service.impl.InterviewAnswerServiceImpl$AiServiceCircuitBreaker");
        var getField = InterviewAnswerServiceImpl.class.getDeclaredField("aiServiceCircuitBreaker");
        getField.setAccessible(true);
        Object cb = getField.get(s);
        var isOpenM = clazz.getDeclaredMethod("isOpen");
        isOpenM.setAccessible(true);
        var recordFailureM = clazz.getDeclaredMethod("recordFailure");
        recordFailureM.setAccessible(true);
        var recordSuccessM = clazz.getDeclaredMethod("recordSuccess");
        recordSuccessM.setAccessible(true);

        assertFalse((Boolean) isOpenM.invoke(cb), "初始状态熔断器关闭");
        for (int i = 0; i < 4; i++) {
            recordFailureM.invoke(cb);
        }
        assertFalse((Boolean) isOpenM.invoke(cb), "4 次失败尚未达到阈值 5");
        recordFailureM.invoke(cb);
        assertTrue((Boolean) isOpenM.invoke(cb), "5 次失败后熔断器打开");

        recordSuccessM.invoke(cb);
        assertFalse((Boolean) isOpenM.invoke(cb), "成功后熔断器关闭");
    }

    private InterviewAnswerServiceImpl newInterviewAnswerServiceImpl() {
        InterviewAnswerServiceImpl s = new InterviewAnswerServiceImpl(
                answerMapper, evaluationMapper, questionMapper, sessionMapper,
                userResumeMapper, reportService, restTemplate, aiTaskExecutor);
        ReflectionTestUtils.setField(s, "aiServiceUrl", "http://localhost:8001");
        return s;
    }

    @Test
    void submitAnswerWithAudio_emptyText_usesPlaceholderForEvaluation() {
        syncExecutor();
        when(sessionMapper.selectById(1L)).thenReturn(session);
        when(answerMapper.selectOne(any())).thenReturn(null);
        when(questionMapper.selectById(50L)).thenReturn(question);
        // 模拟 answerMapper.insert 回填自增 ID，避免 answer.getId() 为 null 导致后续 NPE
        doAnswer(inv -> {
            InterviewAnswer a = inv.getArgument(0);
            a.setId(300L);
            return 1;
        }).when(answerMapper).insert(any(InterviewAnswer.class));
        when(answerMapper.selectById(300L)).thenAnswer(inv -> {
            InterviewAnswer a = new InterviewAnswer();
            a.setId(300L);
            a.setQuestionId(50L);
            a.setStatus("SUBMITTED");
            return a;
        });
        doThrow(new ResourceAccessException("AI unreachable"))
                .when(restTemplate).postForEntity(anyString(), any(HttpEntity.class), eq(Map.class));

        Map<String, Object> result = service.submitAnswerWithAudio(
                1L, 100L, 50L, "", null, "/uploads/audios/x.webm");

        assertEquals("PROCESSING", result.get("evaluationStatus"));
        verify(answerMapper, times(1)).insert(any(InterviewAnswer.class));
    }
}
