package com.aiinterviewer.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 校验 /api/v1/internal/** 请求头中的 X-AI-Internal-Key 与配置的 ai.internal.key 是否匹配。
 * 该 Filter 仅对内部端点生效，其余路径直接放行。
 */
@Slf4j
@Component
public class InternalKeyFilter extends OncePerRequestFilter {

    @Value("${ai.internal.key}")
    private String aiInternalKey;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        String provided = request.getHeader("X-AI-Internal-Key");
        if (provided == null || provided.isBlank() || !provided.equals(aiInternalKey)) {
            log.warn("Internal key mismatch for path: {}", request.getRequestURI());
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":401,\"message\":\"Invalid internal key\",\"data\":null}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
