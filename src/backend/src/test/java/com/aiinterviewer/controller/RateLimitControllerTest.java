package com.aiinterviewer.controller;

import com.aiinterviewer.common.SecurityContext;
import com.aiinterviewer.common.UserRateLimiter;
import com.aiinterviewer.entity.InterviewQuestion;
import com.aiinterviewer.service.InterviewQuestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * P0 并发与限流：控制器层限流行为集成测试（MockMvc + Mock）。
 * 验证：
 *   1. 单用户 QPS 超限 → 返回 429 + 通用消息
 *   2. 正常请求 → 放行并返回 200
 *   3. 限流器重置后额度恢复
 * 注意：standalone MockMvc 不会触发 @ResponseStatus，429 仅体现在响应体 code 字段。
 */
@ExtendWith(MockitoExtension.class)
class RateLimitControllerTest {

    @Mock
    private InterviewQuestionService questionService;
    @Mock
    private SecurityContext securityContext;
    @Mock
    private UserRateLimiter rateLimiter;

    private QuestionController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        controller = new QuestionController(questionService, securityContext, rateLimiter);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void rateLimited_request_returns429Code() throws Exception {
        when(securityContext.resolveUserId(any())).thenReturn(1L);
        when(rateLimiter.tryAcquire(1L)).thenReturn(false);

        mockMvc.perform(post("/api/v1/interviews/{sessionId}/questions/next", 1)
                        .with(user("user1@test.com"))
                        .contentType("application/json"))
                .andExpect(status().isOk()) // standalone MockMvc 不触发 @ResponseStatus，HTTP 状态恒为 200
                .andExpect(jsonPath("$.code").value(429))
                .andExpect(jsonPath("$.message").value("请求过于频繁，请稍后重试"));
    }

    @Test
    void underLimit_request_passesThrough() throws Exception {
        when(securityContext.resolveUserId(any())).thenReturn(1L);
        when(rateLimiter.tryAcquire(1L)).thenReturn(true);
        InterviewQuestion q = new InterviewQuestion();
        q.setId(100L);
        q.setContent("测试题");
        when(questionService.generateNextQuestion(eq(1L), eq(1L), any())).thenReturn(q);

        mockMvc.perform(post("/api/v1/interviews/{sessionId}/questions/next", 1)
                        .with(user("user1@test.com"))
                        .contentType("application/json"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.questionId").value(100));
    }

    @Test
    void limiterRecoversAfterReset() throws Exception {
        // 用真实限流器验证 429 行为
        UserRateLimiter realLimiter = new UserRateLimiter();
        controller = new QuestionController(questionService, securityContext, realLimiter);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        when(securityContext.resolveUserId(any())).thenReturn(5L);

        // 耗尽额度（MAX_QPS=10）
        for (int i = 0; i < 10; i++) {
            when(questionService.generateNextQuestion(eq(1L), eq(5L), any())).thenReturn(dummyQuestion());
            mockMvc.perform(post("/api/v1/interviews/{sessionId}/questions/next", 1)
                            .with(user("user5@test.com")))
                    .andExpect(jsonPath("$.code").value(0));
        }
        // 第 11 次应 429（body code=429）
        mockMvc.perform(post("/api/v1/interviews/{sessionId}/questions/next", 1)
                        .with(user("user5@test.com")))
                .andExpect(jsonPath("$.code").value(429));

        // 重置后恢复
        realLimiter.resetForUser(5L);
        when(questionService.generateNextQuestion(eq(1L), eq(5L), any())).thenReturn(dummyQuestion());
        mockMvc.perform(post("/api/v1/interviews/{sessionId}/questions/next", 1)
                        .with(user("user5@test.com")))
                .andExpect(jsonPath("$.code").value(0));
    }

    private InterviewQuestion dummyQuestion() {
        InterviewQuestion q = new InterviewQuestion();
        q.setId(100L);
        q.setContent("dummy");
        return q;
    }
}
