package com.aiinterviewer.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ApiResponse<Void> handleAccessDenied(AccessDeniedException e) {
        log.error("Access denied: {}", e.getMessage());
        return ApiResponse.forbidden();
    }

    @ExceptionHandler(MultipartException.class)
    public ApiResponse<Void> handleMultipart(MultipartException e) {
        log.error("Multipart error: {}", e.getMessage());
        // 限制文件大小/类型错误仅返回通用提示，不暴露内部细节
        String message = e.getMessage();
        if (message != null && message.contains("size")) {
            return ApiResponse.error(400, "上传文件过大，请压缩后重试");
        }
        return ApiResponse.error(400, "文件上传格式不正确");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException e) {
        log.error("Invalid argument: {}", e.getMessage());
        return ApiResponse.error(400, "请求参数错误");
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ApiResponse<Void> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("Duplicate key conflict: {}", e.getMostSpecificCause().getMessage());
        return ApiResponse.error(409, "该题已提交过答案，请直接查看下一题");
    }

    @ExceptionHandler(RuntimeException.class)
    public ApiResponse<Void> handleRuntime(RuntimeException e) {
        log.error("Runtime error: {}", e.getMessage(), e);
        return ApiResponse.error(500, "服务器内部错误，请稍后重试");
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("Unexpected error: {}", e.getMessage(), e);
        // 安全性修复：不向前端返回原始异常信息，防止泄漏 SQL/堆栈等敏感信息
        return ApiResponse.error(500, "服务器内部错误，请稍后重试");
    }
}
