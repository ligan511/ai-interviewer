package com.aiinterviewer.service;

import com.aiinterviewer.entity.User;
import com.aiinterviewer.security.JwtService;
import com.aiinterviewer.security.UserDetailsServiceImpl;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class UserService {

    private final com.aiinterviewer.mapper.UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;

    public UserService(com.aiinterviewer.mapper.UserMapper userMapper,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       UserDetailsServiceImpl userDetailsService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    public User register(String username, String email, String password) {
        if (userMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getEmail, email)) != null) {
            throw new RuntimeException("Email already registered");
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
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getEmail, email)
        );
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new RuntimeException("Invalid email or password");
        }
        if (user.getStatus() != 1) {
            throw new RuntimeException("Account is disabled");
        }
        UserDetails userDetails = userDetailsService.loadUserByEmail(email);
        String jwt = jwtService.generateToken(userDetails);

        Map<String, Object> result = new HashMap<>();
        result.put("token", jwt);
        result.put("userId", user.getId());
        result.put("email", user.getEmail());
        result.put("username", user.getUsername());
        return result;
    }

    public UserDetails loadUserByEmail(String email) {
        return userDetailsService.loadUserByEmail(email);
    }
}
