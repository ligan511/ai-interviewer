package com.aiinterviewer.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtService 单元测试。
 * 通过反射注入 @Value 字段，避免加载完整 ApplicationContext。
 * 覆盖：生成 → 解析 → 校验；篡改签名；过期判定。
 */
class JwtServiceTest {

    private JwtService jwtService;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() throws Exception {
        jwtService = new JwtService();
        // 反射注入配置
        setField(jwtService, "secretKey", "test-secret-key-for-unit-tests-only");
        setField(jwtService, "expirationMs", 60_000L);

        userDetails = User.withUsername("user@test.com")
                .password("any")
                .authorities(List.of(() -> "ROLE_USER"))
                .build();
    }

    private static void setField(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    void generateAndValidateToken_success() {
        String token = jwtService.generateToken(userDetails);
        assertNotNull(token);
        assertEquals("user@test.com", jwtService.extractEmail(token));
        assertTrue(jwtService.isTokenValid(token, userDetails));
    }

    @Test
    void isTokenValid_wrongSubject_returnsFalse() {
        String token = jwtService.generateToken(userDetails);
        UserDetails other = User.withUsername("other@test.com")
                .password("any")
                .authorities(List.of(() -> "ROLE_USER"))
                .build();
        assertFalse(jwtService.isTokenValid(token, other));
    }

    @Test
    void extractEmail_tamperedToken_throws() {
        String token = jwtService.generateToken(userDetails) + "tampered";
        assertThrows(SignatureException.class, () -> jwtService.extractEmail(token));
    }

    @Test
    void isTokenValid_expiredToken_returnsFalse() throws Exception {
        setField(jwtService, "expirationMs", -1000L); // 已过期
        String token = jwtService.generateToken(userDetails);
        // extractEmail 会在过期时抛 ExpiredJwtException，isTokenValid 调用它会抛出
        assertThrows(ExpiredJwtException.class,
                () -> jwtService.isTokenValid(token, userDetails));
    }
}
