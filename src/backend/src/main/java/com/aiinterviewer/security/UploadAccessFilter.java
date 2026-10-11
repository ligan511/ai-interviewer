package com.aiinterviewer.security;

import com.aiinterviewer.common.SecurityContext;
import com.aiinterviewer.entity.InterviewAnswer;
import com.aiinterviewer.entity.InterviewQuestion;
import com.aiinterviewer.entity.InterviewSession;
import com.aiinterviewer.entity.KnowledgeDocument;
import com.aiinterviewer.entity.UserResume;
import com.aiinterviewer.mapper.InterviewAnswerMapper;
import com.aiinterviewer.mapper.InterviewQuestionMapper;
import com.aiinterviewer.mapper.InterviewSessionMapper;
import com.aiinterviewer.mapper.KnowledgeDocumentMapper;
import com.aiinterviewer.mapper.UserResumeMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * P0 安全加固：/uploads/** 按文件归属校验鉴权。
 *
 * 背景：原 SecurityConfig 将 /uploads/** 设为 permitAll，导致任何人可下载所有
 * 用户上传的简历（含个人信息）、面试音频、知识库文档。本 Filter 在 JwtAuthFilter
 * 之后执行，按请求路径解析出文件对应的业务记录，校验其归属的 userId 是否等于
 * 当前认证用户：
 *   - /uploads/audios/{name}    → interview_answer.audio_url → interview_question → interview_session → user_id
 *   - /uploads/resumes/{name}   → user_resume.file_url → user_resume.user_id
 *   - /uploads/knowledge/{name} → knowledge_document.file_url（共享资源，仅要求已登录）
 * 校验不通过返回 404（不泄露资源存在性）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UploadAccessFilter extends OncePerRequestFilter {

    private final UserResumeMapper userResumeMapper;
    private final InterviewAnswerMapper answerMapper;
    private final InterviewQuestionMapper questionMapper;
    private final InterviewSessionMapper sessionMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final SecurityContext securityContext;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/uploads/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":401,\"message\":\"未登录\",\"data\":null}");
            return;
        }

        String uri = request.getRequestURI();
        // 提取 /uploads/{category}/{fileName}
        int firstSlash = uri.indexOf('/', "/uploads/".length());
        if (firstSlash < 0) {
            deny(response);
            return;
        }
        String category = uri.substring("/uploads/".length(), firstSlash);
        String fileName = uri.substring(firstSlash + 1);

        boolean allowed;
        try {
            switch (category) {
                case "resumes":
                    allowed = checkResumeAccess(fileName, auth);
                    break;
                case "audios":
                    allowed = checkAudioAccess(fileName, auth);
                    break;
                case "knowledge":
                    // 知识库为共享资源，仅要求已登录 + 文档存在
                    allowed = knowledgeDocumentMapper.selectOne(
                            new LambdaQueryWrapper<KnowledgeDocument>()
                                    .like(KnowledgeDocument::getFileUrl, "/uploads/knowledge/" + fileName)) != null;
                    break;
                default:
                    allowed = false;
            }
        } catch (Exception e) {
            log.debug("Upload access check error for {}: {}", uri, e.getMessage());
            allowed = false;
        }

        if (!allowed) {
            log.debug("Upload access denied: {} (auth={})", uri, auth.getName());
            deny(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean checkResumeAccess(String fileName, Authentication auth) {
        UserResume resume = userResumeMapper.selectOne(new LambdaQueryWrapper<UserResume>()
                .eq(UserResume::getFileUrl, "/uploads/resumes/" + fileName));
        if (resume == null) return false;
        Long currentUserId = resolveUserIdSafely(auth);
        return currentUserId != null && currentUserId.equals(resume.getUserId());
    }

    private boolean checkAudioAccess(String fileName, Authentication auth) {
        InterviewAnswer answer = answerMapper.selectOne(new LambdaQueryWrapper<InterviewAnswer>()
                .eq(InterviewAnswer::getAudioUrl, "/uploads/audios/" + fileName));
        if (answer == null || answer.getQuestionId() == null) return false;
        InterviewQuestion question = questionMapper.selectById(answer.getQuestionId());
        if (question == null || question.getSessionId() == null) return false;
        InterviewSession session = sessionMapper.selectById(question.getSessionId());
        if (session == null) return false;
        Long currentUserId = resolveUserIdSafely(auth);
        return currentUserId != null && currentUserId.equals(session.getUserId());
    }

    private Long resolveUserIdSafely(Authentication auth) {
        try {
            return securityContext.resolveUserId(auth);
        } catch (Exception e) {
            return null;
        }
    }

    private void deny(HttpServletResponse response) throws java.io.IOException {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":404,\"message\":\"资源不存在\",\"data\":null}");
    }
}
