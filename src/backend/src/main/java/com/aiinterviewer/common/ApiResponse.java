package com.aiinterviewer.common;

import lombok.Data;

@Data
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.code = 0;
        response.message = "ok";
        response.data = data;
        return response;
    }

    public static <T> ApiResponse<T> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.code = code;
        response.message = message;
        response.data = null;
        return response;
    }

    public static <T> ApiResponse<T> BadRequest(String message) {
        return error(400, message);
    }

    public static <T> ApiResponse<T> Unauthorized() {
        return error(401, "Unauthorized");
    }

    public static <T> ApiResponse<T> Forbidden() {
        return error(403, "Forbidden");
    }

    public static <T> ApiResponse<T> NotFound() {
        return error(404, "Resource not found");
    }
}
