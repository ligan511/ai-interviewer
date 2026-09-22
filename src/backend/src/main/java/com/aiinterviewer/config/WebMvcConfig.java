package com.aiinterviewer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 静态资源映射配置。
 * 让 /uploads/** 路径能访问到上传的简历、知识库文档、音频文件。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${ai.upload.dir:./uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 将 /uploads/** URL 前缀映射到本地文件系统上传目录
        String location = uploadDir.startsWith("/") || uploadDir.contains(":")
                ? uploadDir.endsWith("/") ? uploadDir : uploadDir + "/"
                : "file:" + (uploadDir.endsWith("/") ? uploadDir : uploadDir + "/");
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location);
    }
}
