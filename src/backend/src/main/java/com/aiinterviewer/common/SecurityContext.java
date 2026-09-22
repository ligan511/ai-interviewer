package com.aiinterviewer.common;

import com.aiinterviewer.entity.User;
import com.aiinterviewer.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * 安全上下文工具：统一从 Authentication 解析当前用户 ID，消除各 Controller 中的重复实现。
 * 同时保留 ThreadLocal 形式以便异步任务中传递用户信息。
 */
@Component
@RequiredArgsConstructor
public class SecurityContext {

    private final UserMapper userMapper;

    private static final ThreadLocal<Long> CURRENT_USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_USER_EMAIL = new ThreadLocal<>();

    public static void setCurrentUser(Long userId, String email) {
        CURRENT_USER_ID.set(userId);
        CURRENT_USER_EMAIL.set(email);
    }

    public static Long getCurrentUserId() {
        return CURRENT_USER_ID.get();
    }

    public static String getCurrentUserEmail() {
        return CURRENT_USER_EMAIL.get();
    }

    public static void clear() {
        CURRENT_USER_ID.remove();
        CURRENT_USER_EMAIL.remove();
    }

    public static void setFromAuthentication(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof UserDetails) {
            UserDetails userDetails = (UserDetails) auth.getPrincipal();
            CURRENT_USER_EMAIL.set(userDetails.getUsername());
        }
    }

    /**
     * 从 Authentication 解析当前用户 ID。
     * 所有 Controller 统一调用此方法，避免在每个 Controller 中重复实现。
     */
    public Long resolveUserId(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof UserDetails)) {
            throw new RuntimeException("未登录或会话已失效");
        }
        String email = ((UserDetails) auth.getPrincipal()).getUsername();
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        // 同步到 ThreadLocal，便于后续异步任务取用
        setCurrentUser(user.getId(), email);
        return user.getId();
    }
}
