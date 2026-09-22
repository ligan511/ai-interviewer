package com.aiinterviewer.common;

import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

/**
 * 文件上传安全校验工具类。
 * 统一处理扩展名白名单、MIME 类型检查、文件大小限制。
 */
public final class FileUploadValidator {

    private FileUploadValidator() {}

    // 各场景允许的扩展名白名单（小写、无前导点）
    private static final Set<String> RESUME_EXTENSIONS = Set.of("pdf", "doc", "docx", "txt", "md");
    private static final Set<String> KNOWLEDGE_EXTENSIONS = Set.of("pdf", "doc", "docx", "txt", "md");
    private static final Set<String> AUDIO_EXTENSIONS = Set.of("webm", "mp3", "wav", "m4a", "ogg");

    // MIME 黑名单：禁止任何可执行/脚本类型
    private static final Set<String> FORBIDDEN_MIME = Set.of(
            "application/x-msdownload",
            "application/x-executable",
            "application/x-sh",
            "application/x-batch",
            "text/x-shellscript"
    );

    // 默认上限：简历/知识库 20MB，音频 10MB
    private static final long MAX_DOC_BYTES = 20L * 1024 * 1024;
    private static final long MAX_AUDIO_BYTES = 10L * 1024 * 1024;

    public static void validateResume(MultipartFile file) {
        validate(file, RESUME_EXTENSIONS, MAX_DOC_BYTES, "简历");
    }

    public static void validateKnowledge(MultipartFile file) {
        validate(file, KNOWLEDGE_EXTENSIONS, MAX_DOC_BYTES, "知识库文档");
    }

    public static void validateAudio(MultipartFile file) {
        validate(file, AUDIO_EXTENSIONS, MAX_AUDIO_BYTES, "语音");
    }

    /**
     * 通用校验：空文件、大小、扩展名白名单、MIME 黑名单。
     * 校验失败抛出 IllegalArgumentException，由 GlobalExceptionHandler 统一处理。
     */
    private static void validate(MultipartFile file, Set<String> allowedExtensions,
                                  long maxBytes, String label) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(label + "文件不能为空");
        }
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException(label + "文件过大，最大允许 " + (maxBytes / 1024 / 1024) + "MB");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.contains(".")) {
            throw new IllegalArgumentException(label + "文件缺少扩展名");
        }
        String ext = originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase();
        if (!allowedExtensions.contains(ext)) {
            throw new IllegalArgumentException(
                    label + "文件类型不支持的扩展名: ." + ext + "，允许: " + allowedExtensions);
        }

        String mime = file.getContentType();
        if (mime != null && FORBIDDEN_MIME.contains(mime.toLowerCase())) {
            throw new IllegalArgumentException(label + "文件类型被禁止");
        }
    }
}
