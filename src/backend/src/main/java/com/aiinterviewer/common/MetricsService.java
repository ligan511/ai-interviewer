package com.aiinterviewer.common;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * P1 可观测性：AI 调用指标埋点。
 *
 * 指标清单（Micrometer，Prometheus 抓取）：
 *   - ai_call_total        Counter   tags: task_type, status(success/failure)   调用次数
 *   - ai_call_duration    Timer     tags: task_type                              调用耗时（P50/P95/P99）
 *   - ai_service_circuit_open  Gauge tags: service(answer/question)              熔断器状态（0/1）
 *   - thread_pool_active  Gauge tags: pool(ai-task)                             线程池活动线程数
 *
 * 使用方式：
 *   MetricsService metrics;
 *   Timer.Sample sample = metrics.startTimer("answer_evaluate");
 *   try {
 *       ... 调用 AI ...
 *       metrics.recordSuccess("answer_evaluate", sample);
 *   } catch (Exception e) {
 *       metrics.recordFailure("answer_evaluate", sample, e);
 *       throw e;
 *   }
 */
@Component
@RequiredArgsConstructor
public class MetricsService {

    private final MeterRegistry meterRegistry;

    // 已注册的 Timer（按 task_type 缓存，避免重复创建）
    private final ConcurrentMap<String, Timer> timers = new ConcurrentHashMap<>();

    /** 开始计时（在 AI 调用前调用）。 */
    public Timer.Sample startTimer(String taskType) {
        return Timer.start(meterRegistry);
    }

    /** 记录成功调用。 */
    public void recordSuccess(String taskType, Timer.Sample sample) {
        sample.stop(timer(taskType));
        counter(taskType).increment();
    }

    /** 记录失败调用。 */
    public void recordFailure(String taskType, Timer.Sample sample, Throwable cause) {
        sample.stop(timer(taskType));
        counter(taskType + "_failure").increment();
    }

    /** 记录 AI 调用熔断器状态（0=关闭/1=打开），供 Grafana 告警。 */
    public void setCircuitOpen(String serviceName, boolean open) {
        double state = open ? 1.0 : 0.0;
        Gauge.builder("ai_service_circuit_open", () -> state)
                .tags("service", serviceName)
                .description("AI 服务熔断器是否打开（1=已熔断）")
                .strongReference(true)
                .register(meterRegistry);
    }

    private Counter counter(String status) {
        return Counter.builder("ai_call_total")
                .tags("task_type", status)
                .description("AI 调用次数")
                .register(meterRegistry);
    }

    private Timer timer(String taskType) {
        return timers.computeIfAbsent(taskType,
                t -> Timer.builder("ai_call_duration")
                        .tags("task_type", t)
                        .description("AI 调用耗时")
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .register(meterRegistry));
    }
}
