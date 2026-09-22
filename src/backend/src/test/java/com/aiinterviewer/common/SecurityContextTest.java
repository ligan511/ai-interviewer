package com.aiinterviewer.common;

import com.aiinterviewer.entity.User;
import com.aiinterviewer.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * SecurityContext 单元测试。
 * 覆盖：未登录、用户不存在、正常解析 + ThreadLocal 写入。
 */
class SecurityContextTest {

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private SecurityContext securityContext;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        SecurityContext.clear();
    }

    @AfterEach
    void tearDown() throws Exception {
        SecurityContext.clear();
        mocks.close();
    }

    @Test
    void resolveUserId_nullAuth_throws() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> securityContext.resolveUserId(null));
        assertTrue(ex.getMessage().contains("未登录"));
    }

    @Test
    void resolveUserId_wrongPrincipalType_throws() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("string-principal", null);
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> securityContext.resolveUserId(auth));
        assertTrue(ex.getMessage().contains("未登录"));
    }

    @Test
    void resolveUserId_userNotFound_throws() {
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername("nobody@test.com")
                .password("any")
                .authorities(List.of(() -> "ROLE_USER"))
                .build();
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> securityContext.resolveUserId(auth));
        assertTrue(ex.getMessage().contains("用户不存在"));
    }

    @Test
    void resolveUserId_success_setsThreadLocal() {
        User user = new User();
        user.setId(42L);
        user.setEmail("user@test.com");

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername("user@test.com")
                .password("any")
                .authorities(listOfUserRole())
                .build();
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        Long userId = securityContext.resolveUserId(auth);

        assertEquals(42L, userId);
        assertEquals(42L, SecurityContext.getCurrentUserId());
        assertEquals("user@test.com", SecurityContext.getCurrentUserEmail());
    }

    @Test
    void setCurrentUser_threadLocalIsolation() {
        SecurityContext.setCurrentUser(7L, "a@b.com");
        assertEquals(7L, SecurityContext.getCurrentUserId());
        assertEquals("a@b.com", SecurityContext.getCurrentUserEmail());
        SecurityContext.clear();
        assertNull(SecurityContext.getCurrentUserId());
        assertNull(SecurityContext.getCurrentUserEmail());
    }

    private static List<SimpleGrantedAuthority> listOfUserRole() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }
}
