package com.aiinterviewer.controller;

import com.aiinterviewer.common.ApiResponse;
import com.aiinterviewer.controller.dto.LoginRequest;
import com.aiinterviewer.controller.dto.RegisterRequest;
import com.aiinterviewer.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        try {
            com.aiinterviewer.entity.User user = userService.register(
                request.getUsername(), request.getEmail(), request.getPassword()
            );
            Map<String, Object> data = Map.of(
                "userId", user.getId(),
                "email", user.getEmail(),
                "username", user.getUsername()
            );
            return ApiResponse.ok(data);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        try {
            Map<String, Object> result = userService.login(request.getEmail(), request.getPassword());
            return ApiResponse.ok(result);
        } catch (RuntimeException e) {
            return ApiResponse.error(401, e.getMessage());
        }
    }
}
