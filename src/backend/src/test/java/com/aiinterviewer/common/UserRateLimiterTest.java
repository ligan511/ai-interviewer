package com.aiinterviewer.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0 并发与限流：UserRateLimiter 单元测试。
 * 覆盖：正常放行、超限拒绝、窗口重置、null 用户放行、重置清理。
 */
class UserRateLimiterTest {

    @Test
    void nullUserAlwaysPasses() {
        UserRateLimiter limiter = new UserRateLimiter();
        for (int i = 0; i < 100; i++) {
            assertTrue(limiter.tryAcquire(null), "未认证请求应放行（由 Security 层处理）");
        }
    }

    @Test
    void underLimitPasses() {
        UserRateLimiter limiter = new UserRateLimiter();
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire(1L), "5 次请求未超限应放行");
        }
    }

    @Test
    void overLimitRejected() {
        UserRateLimiter limiter = new UserRateLimiter();
        // 默认 MAX_QPS=10，连续 11 次请求中第 11 次应被拒
        boolean lastResult = false;
        for (int i = 0; i < 10; i++) {
            lastResult = limiter.tryAcquire(1L);
            assertTrue(lastResult, "前 10 次请求应放行");
        }
        boolean rejected = limiter.tryAcquire(1L);
        assertFalse(rejected, "第 11 次请求应被限流拒绝");
    }

    @Test
    void windowResetAllowsAfterTimeout() throws InterruptedException {
        UserRateLimiter limiter = new UserRateLimiter();
        // 耗尽额度
        for (int i = 0; i < 10; i++) {
            limiter.tryAcquire(2L);
        }
        assertFalse(limiter.tryAcquire(2L), "额度耗尽应拒绝");
        // 等待窗口过期（WINDOW_MS=1000ms，等 1.1s）
        Thread.sleep(1100);
        assertTrue(limiter.tryAcquire(2L), "窗口重置后应放行");
    }

    @Test
    void resetForUserClearsCounter() {
        UserRateLimiter limiter = new UserRateLimiter();
        for (int i = 0; i < 10; i++) {
            limiter.tryAcquire(3L);
        }
        assertFalse(limiter.tryAcquire(3L));
        limiter.resetForUser(3L);
        assertTrue(limiter.tryAcquire(3L), "重置后应放行");
    }

    @Test
    void differentUsersHaveIndependentCounters() {
        UserRateLimiter limiter = new UserRateLimiter();
        // 耗尽用户 10 的额度
        for (int i = 0; i < 10; i++) {
            limiter.tryAcquire(10L);
        }
        assertFalse(limiter.tryAcquire(10L), "用户 10 应被拒");
        assertTrue(limiter.tryAcquire(11L), "用户 11 不受用户 10 影响");
    }
}
