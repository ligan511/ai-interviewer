package com.aiinterviewer.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P1 可观测性：TraceIdFilter 单元测试。
 * 覆盖：
 *   1. 无 X-Trace-Id 请求头 → 自动生成 traceId 并写入 MDC
 *   2. 带 X-Trace-Id 请求头 → 复用该 traceId
 *   3. 请求结束后 MDC 被清理
 *   4. 响应头回传 traceId 给调用方
 */
class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @Test
    void generatesTraceIdWhenAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(response.getHeader(TraceIdFilter.TRACE_ID_HEADER), "响应应回传 traceId");
        assertFalse(response.getHeader(TraceIdFilter.TRACE_ID_HEADER).isBlank());
        // 请求结束后 MDC 应被清理
        assertNull(MDC.get(TraceIdFilter.MDC_TRACE_ID), "MDC 应在请求结束后清理");
    }

    @Test
    void reusesExistingTraceId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "my-fixed-trace-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals("my-fixed-trace-id", response.getHeader(TraceIdFilter.TRACE_ID_HEADER));
    }

    @Test
    void setsMdcDuringFilterChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        final String[] capturedMdc = new String[1];

        // 在 filterChain 内捕获 MDC 值，验证请求期间 MDC 可用
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                capturedMdc[0] = MDC.get(TraceIdFilter.MDC_TRACE_ID);
            }
        };

        filter.doFilter(request, response, chain);

        assertNotNull(capturedMdc[0], "请求期间 MDC 应包含 traceId");
        assertFalse(capturedMdc[0].isBlank());
    }
}
