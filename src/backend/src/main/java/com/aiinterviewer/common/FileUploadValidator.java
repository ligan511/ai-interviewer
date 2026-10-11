package com.aiinterviewer.common;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * 文件上传安全校验工具类。
 * 统一处理扩展名白名单、MIME 类型检查、文件大小限制、文件签名（magic bytes）校验。
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
     * 通用校验：空文件、大小、扩展名白名单、MIME 黑名单、文件签名（magic bytes）。
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

        // P0 安全加固：校验文件签名（magic bytes），防止仅改扩展名绕过校验
        try (InputStream is = file.getInputStream()) {
            byte[] head = new byte[16];
            int read = is.read(head);
            if (read >= 4) {
                checkMagicBytes(head, ext, label);
            }
        } catch (IOException e) {
            // 读取失败不阻断（部分 MultipartFile 实现 getInputStream 可能不可重复读）
        }
    }

    /**
     * 按扩展名校验文件签名（magic bytes）。
     * 仅对二进制格式做严格校验；.txt/.md 为纯文本不做签名校验。
     */
    private static void checkMagicBytes(byte[] head, String ext, String label) {
        switch (ext) {
            case "pdf":
                // %PDF-
                if (!(startsWith(head, 0, new byte[]{'%', 'P', 'D', 'F'}))) {
                    throw new IllegalArgumentException(label + "文件签名校验失败：不是有效的 PDF 文件");
                }
                break;
            case "doc", "docx":
                // OOXML (.docx) 以 PK\x03\x04 开头；旧版 .doc 以 D0 CF 11 E0 开头
                byte[] zipPrefix = {'P', 'K', 0x03, 0x04};
                byte[] olePrefix = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0};
                boolean isZip = startsWith(head, 0, zipPrefix);
                boolean isOle = startsWith(head, 0, olePrefix);
                if (!isZip && !isOle) {
                    throw new IllegalArgumentException(label + "文件签名校验失败：不是有效的 Office 文档");
                }
                break;
            case "webm":
                // EBML header: 0x1A45DFA3
                byte[] webmPrefix = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3};
                if (!startsWith(head, 0, webmPrefix)) {
                    throw new IllegalArgumentException(label + "文件签名校验失败：不是有效的 WebM 文件");
                }
                break;
            case "mp3":
                // MPEG frame sync: 0xFF 0xFB / 0xFF 0xF3 / 0xFF 0xF2 等，或 ID3 tag
                boolean isMp3Sync = head[0] == (byte) 0xFF && (head[1] & 0xE0) == (byte) 0xC0;
                boolean isId3 = startsWith(head, 0, new byte[]{'I', 'D', '3'});
                if (!isMp3Sync && !isId3) {
                    throw new IllegalArgumentException(label + "文件签名校验失败：不是有效的 MP3 文件");
                }
                break;
            case "wav":
                // RIFF....WAVE
                if (!startsWith(head, 0, new byte[]{'R', 'I', 'F', 'F'})
                        || !startsWith(head, 8, new byte[]{'W', 'A', 'V', 'E'})) {
                    throw new IllegalArgumentException(label + "文件签名校验失败：不是有效的 WAV 文件");
                }
                break;
            case "m4a", "ogg":
                // m4a: ISO BMFF (ftyp box)；ogg: "OggS"
                if ("m4a".equals(ext)) {
                    byte[] ftypPrefix = {'f', 't', 'y', 'p'};
                    if (!startsWith(head, 4, ftypPrefix)) {
                        throw new IllegalArgumentException(label + "文件签名校验失败：不是有效的 M4A 文件");
                    }
                } else {
                    byte[] oggPrefix = {'O', 'g', 'g', 'S'};
                    if (!startsWith(head, 0, oggPrefix)) {
                        throw new IllegalArgumentException(label + "文件签名校验失败：不是有效的 OGG 文件");
                    }
                }
                break;
            case "txt", "md":
                // 纯文本不做签名校验（允许任意内容）
                break;
            default:
                break;
        }
    }

    private static boolean startsWith(byte[] data, int offset, byte[] prefix) {
        if (offset + prefix.length > data.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[offset + i] != prefix[i]) return false;
        }
        return true;
    }
}
