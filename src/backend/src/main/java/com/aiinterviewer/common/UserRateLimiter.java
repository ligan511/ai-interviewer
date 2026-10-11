package com.aiinterviewer.common;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * P0 并发与限流：基于滑动窗口计数器的用户级 QPS 限流器。
 *
 * 设计目标：保护外部 LLM 调用与内部服务，避免单用户高频触发造成：
 *   - 线程池队列堆积（评分/出题任务雪崩）
 *   - 外部模型 API 限流（重试风暴）
 *
 * 实现说明：
 *   - 每用户独立滑动窗口（默认 1 秒内允许 N 次请求）
 *   - 超限返回 false，调用方按 429 响应处理
 *   - 使用 ConcurrentMap + 周期性清理过期用户，避免内存泄漏
 *   - 无外部依赖（不引入 Bucket4j / Redis），适合 V1 单机部署
 */
@Component
public class UserRateLimiter {

    /** 每用户默认 QPS（每秒允许请求数） */
    private static final int MAX_QPS = 10;
    /** 滑动窗口时长（毫秒） */
    private static final long WINDOW_MS = 1000L;
    /** 过期用户清理阈值（毫秒） */
    private static final long CLEANUP_INTERVAL_MS = 60_000L;

    private final Map<Long, WindowCounter> users = new ConcurrentHashMap<>();
    private volatile long lastCleanup = System.currentTimeMillis();

    /** 滑动窗口计数器 */
    private static final class WindowCounter {
        final AtomicInteger count = new AtomicInteger(0);
        volatile long windowStart = System.currentTimeMillis();

        /** 尝试通过限流，返回 true 表示放行。窗口过期时重置。 */
        boolean tryAcquire(int max, long windowMs) {
            long now = System.currentTimeMillis();
            if (now - windowStart >= windowMs) {
                windowStart = now;
                count.set(1);
                return true;
            }
            return count.incrementAndGet() <= max;
        }

        long lastAccess() {
            return windowStart;
        }
    }

    /**
     * 尝试为指定用户获取一次请求许可。
     *
     * @param userId 当前请求用户 ID
     * @return true 表示放行，false 表示超限（调用方应返回 429）
     */
    public boolean tryAcquire(Long userId) {
        if (userId == null) return true; // 未认证请求由 Security 层拦截，不在此限流
        maybeCleanup();
        WindowCounter c = users.computeIfAbsent(userId, k -> new WindowCounter());
        return c.tryAcquire(MAX_QPS, WINDOW_MS);
    }

    /** 周期性清理长时间未活跃的用户，防止内存泄漏。 */
    private void maybeCleanup() {
        long now = System.currentTimeMillis();
        if (now - lastCleanup < CLEANUP_INTERVAL_MS) return;
        lastCleanup = now;
        users.entrySet().removeIf(e -> now - e.getValue().lastAccess() > CLEANUP_INTERVAL_MS);
    }

    /** 测试用：重置指定用户的计数器（避免测试间互相影响）。 */
    public void resetForUser(Long userId) {
        if (userId != null) users.remove(userId);
    }
}
