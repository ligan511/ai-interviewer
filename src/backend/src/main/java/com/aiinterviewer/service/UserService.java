package com.aiinterviewer.service;

import com.aiinterviewer.entity.User;
import com.aiinterviewer.mapper.UserMapper;
import com.aiinterviewer.security.JwtService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户服务：注册、登录、加载用户详情。
 * 注意：本类直接用 UserMapper 构造 UserDetails，不再注入 UserDetailsServiceImpl，
 * 避免与 SecurityConfig / JwtAuthFilter 形成循环依赖。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public User register(String username, String email, String password) {
        if (userMapper.selectOne(
            new LambdaQueryWrapper<User>().eq(User::getEmail, email)) != null) {
            throw new RuntimeException("该邮箱已被注册");
        }
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setStatus(1);
        userMapper.insert(user);
        return user;
    }

    public Map<String, Object> login(String email, String password) {
        User user = userMapper.selectOne(
            new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new RuntimeException("邮箱或密码错误");
        }
        if (user.getStatus() != 1) {
            throw new RuntimeException("账号已被禁用");
        }
        // 直接基于查询到的 User 实体构造 UserDetails，避免重复查询数据库
        UserDetails userDetails = toUserDetails(user);
        String jwt = jwtService.generateToken(userDetails);

        Map<String, Object> result = new HashMap<>();
        result.put("token", jwt);
        result.put("userId", user.getId());
        result.put("email", user.getEmail());
        result.put("username", user.getUsername());
        return result;
    }

    /**
     * 根据邮箱加载 UserDetails（供 JwtAuthFilter 使用）。
     * 直接查库构造，不依赖 UserDetailsServiceImpl，打破循环依赖。
     */
    public UserDetails loadUserByEmail(String email) {
        User user = userMapper.selectOne(
            new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (user == null) {
            return null;
        }
        return toUserDetails(user);
    }

    private UserDetails toUserDetails(User user) {
        // 使用全限定名，避免与 com.aiinterviewer.entity.User 同名冲突
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();
    }
}
