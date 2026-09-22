package com.aiinterviewer.common;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FileUploadValidator 单元测试。
 * 覆盖：空文件、超大、扩展名白名单、MIME 黑名单、正常路径。
 */
class FileUploadValidatorTest {

    @Test
    void validateResume_emptyFile_throws() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", new byte[]{});
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> FileUploadValidator.validateResume(file));
        assertTrue(ex.getMessage().contains("不能为空"));
    }

    @Test
    void validateResume_forbiddenExtension_throws() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "evil.exe", "application/octet-stream", new byte[]{1, 2, 3});
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> FileUploadValidator.validateResume(file));
        assertTrue(ex.getMessage().contains("不支持的扩展名"));
    }

    @Test
    void validateResume_forbiddenMime_throws() {
        // 扩展名合法但 MIME 是 shellscript → 应拒绝
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.txt", "text/x-shellscript", new byte[]{1, 2, 3});
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> FileUploadValidator.validateResume(file));
        assertTrue(ex.getMessage().contains("被禁止"));
    }

    @Test
    void validateResume_tooLarge_throws() {
        byte[] big = new byte[(int) (20L * 1024 * 1024) + 1];
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", big);
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> FileUploadValidator.validateResume(file));
        assertTrue(ex.getMessage().contains("过大"));
    }

    @Test
    void validateResume_validPdf_passes() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", new byte[]{1, 2, 3});
        assertDoesNotThrow(() -> FileUploadValidator.validateResume(file));
    }

    @Test
    void validateAudio_validWebm_passes() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "voice.webm", "audio/webm", new byte[]{1, 2, 3});
        assertDoesNotThrow(() -> FileUploadValidator.validateAudio(file));
    }

    @Test
    void validateKnowledge_missingExtension_throws() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "noext", "application/octet-stream", new byte[]{1, 2, 3});
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> FileUploadValidator.validateKnowledge(file));
        assertTrue(ex.getMessage().contains("缺少扩展名"));
    }
}
