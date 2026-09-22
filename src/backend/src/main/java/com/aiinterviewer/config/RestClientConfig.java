package com.aiinterviewer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

@Configuration
public class RestClientConfig {

    @Value("${ai.service.base-url}")
    private String aiServiceUrl;

    @Value("${ai.internal.key}")
    private String aiInternalKey;

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(55000);
        RestTemplate restTemplate = new RestTemplate(factory);
        // 统一为所有指向 AI service 的 /internal/ai/* 请求添加鉴权头
        ClientHttpRequestInterceptor authInterceptor = (HttpRequest request, byte[] body,
                                                        org.springframework.http.client.ClientHttpRequestExecution execution) -> {
            String uri = request.getURI().toString();
            if (uri.startsWith(aiServiceUrl + "/internal/ai/")) {
                request.getHeaders().set("X-AI-Internal-Key", aiInternalKey);
            }
            return execution.execute(request, body);
        };
        restTemplate.setInterceptors(Collections.singletonList(authInterceptor));
        return restTemplate;
    }
}
