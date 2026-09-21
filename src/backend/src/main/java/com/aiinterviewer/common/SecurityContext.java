package com.aiinterviewer.common;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;

public class SecurityContext {
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
}
