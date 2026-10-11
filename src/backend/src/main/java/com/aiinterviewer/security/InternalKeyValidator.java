package com.aiinterviewer.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * P0 安全加固：启动时强校验 AI_INTERNAL_KEY。
 *
 * 背景：application.yml 中 ai.internal.key 使用 :? 强制注入，缺少时 Spring 启动即失败。
 * 但 AI_INTERNAL_KEY 配置过短（<32 字符）或为已知弱值时，会被暴力破解。本校验器
 * 在应用启动后做二次校验：长度 < 32 时打 WARN 日志并 fail-fast（拒绝启动），
 * 避免生产环境使用弱密钥。
 */
@Slf4j
@Component
public class InternalKeyValidator implements ApplicationRunner {

    @Value("${ai.internal.key}")
    private String aiInternalKey;

    @Value("${ai.jwt.secret}")
    private String jwtSecret;

    @Override
    public void run(ApplicationArguments args) {
        // AI_INTERNAL_KEY 长度校验
        if (aiInternalKey == null || aiInternalKey.length() < 32) {
            String msg = "AI_INTERNAL_KEY 长度不足 32 字符（当前 "
                    + (aiInternalKey == null ? 0 : aiInternalKey.length())
                    + "），存在被暴力破解风险，请设置强随机密钥（如 openssl rand -hex 32）";
            log.error(msg);
            throw new IllegalStateException(msg);
        }

        // JWT_SECRET 长度校验（HMAC-SHA 要求 256 位 = 32 字节）
        if (jwtSecret == null || jwtSecret.length() < 32) {
            String msg = "JWT_SECRET 长度不足 32 字符（当前 "
                    + (jwtSecret == null ? 0 : jwtSecret.length())
                    + "），jjwt 要求 256 位以上，否则所有 JWT 校验失败，请设置强随机密钥";
            log.error(msg);
            throw new IllegalStateException(msg);
        }

        log.info("Internal key & JWT secret validation passed (key len={}, secret len={})",
                aiInternalKey.length(), jwtSecret.length());
    }
}
