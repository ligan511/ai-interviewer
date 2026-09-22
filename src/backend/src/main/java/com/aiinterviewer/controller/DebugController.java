package com.aiinterviewer.controller;

import com.aiinterviewer.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 调试接口：仅在 dev profile 下激活，生产环境不加载。
 * 启动时加 -Dspring.profiles.active=dev 启用。
 */
@Profile("dev")
@RestController
@RequestMapping("/api/v1/debug")
@RequiredArgsConstructor
public class DebugController {

    @PostMapping("/test")
    public ApiResponse<String> testPost(Authentication auth) {
        return ApiResponse.ok("POST works! user=" + (auth != null ? auth.getName() : "null"));
    }

    @GetMapping("/test")
    public ApiResponse<String> testGet(Authentication auth) {
        return ApiResponse.ok("GET works! user=" + (auth != null ? auth.getName() : "null"));
    }
}
