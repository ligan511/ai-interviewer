package com.aiinterviewer.service;

import com.aiinterviewer.entity.User;
import com.aiinterviewer.mapper.UserMapper;
import com.aiinterviewer.security.JwtService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * UserService 单元测试。
 * 覆盖：注册成功/重复邮箱、登录成功/密码错误/账号禁用。
 */
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private UserService userService;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void register_newUser_success() {
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(passwordEncoder.encode("P@ssw0rd")).thenReturn("hashed-pwd");
        when(userMapper.insert(any(User.class))).thenReturn(1);

        User result = userService.register("alice", "alice@test.com", "P@ssw0rd");

        assertEquals("alice", result.getUsername());
        assertEquals("alice@test.com", result.getEmail());
        assertEquals("hashed-pwd", result.getPasswordHash());
        assertEquals(1, result.getStatus());
        verify(userMapper).insert(any(User.class));
    }

    @Test
    void register_duplicateEmail_throws() {
        User existing = new User();
        existing.setEmail("alice@test.com");
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.register("alice", "alice@test.com", "P@ssw0rd"));
        assertTrue(ex.getMessage().contains("已被注册"));
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void login_validCredentials_returnsJwtAndUser() {
        User user = new User();
        user.setId(99L);
        user.setEmail("alice@test.com");
        user.setUsername("alice");
        user.setPasswordHash("hashed-pwd");
        user.setStatus(1);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.matches("P@ssw0rd", "hashed-pwd")).thenReturn(true);
        when(jwtService.generateToken(any())).thenReturn("mock-jwt");

        Map<String, Object> result = userService.login("alice@test.com", "P@ssw0rd");

        assertEquals("mock-jwt", result.get("token"));
        assertEquals(99L, result.get("userId"));
        assertEquals("alice@test.com", result.get("email"));
        assertEquals("alice", result.get("username"));
    }

    @Test
    void login_wrongPassword_throws() {
        User user = new User();
        user.setEmail("alice@test.com");
        user.setPasswordHash("hashed-pwd");
        user.setStatus(1);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.matches("wrong", "hashed-pwd")).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.login("alice@test.com", "wrong"));
        assertTrue(ex.getMessage().contains("邮箱或密码错误"));
    }

    @Test
    void login_userNotFound_throws() {
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.login("ghost@test.com", "any"));
        assertTrue(ex.getMessage().contains("邮箱或密码错误"));
    }

    @Test
    void login_disabledAccount_throws() {
        User user = new User();
        user.setEmail("alice@test.com");
        user.setPasswordHash("hashed-pwd");
        user.setStatus(0); // 禁用
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.login("alice@test.com", "P@ssw0rd"));
        assertTrue(ex.getMessage().contains("已被禁用"));
    }

    @Test
    void loadUserByEmail_notFound_returnsNull() {
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertNull(userService.loadUserByEmail("ghost@test.com"));
    }

    @Test
    void loadUserByEmail_found_returnsUserDetails() {
        User user = new User();
        user.setEmail("alice@test.com");
        user.setPasswordHash("hashed-pwd");
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        org.springframework.security.core.userdetails.UserDetails details =
                userService.loadUserByEmail("alice@test.com");

        assertNotNull(details);
        assertEquals("alice@test.com", details.getUsername());
        assertEquals("hashed-pwd", details.getPassword());
    }
}
