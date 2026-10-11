package com.aiinterviewer.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final InternalKeyFilter internalKeyFilter;
    private final UploadAccessFilter uploadAccessFilter;
    private final TraceIdFilter traceIdFilter;

    public SecurityConfig(@Lazy JwtAuthFilter jwtAuthFilter,
                          @Lazy InternalKeyFilter internalKeyFilter,
                          @Lazy UploadAccessFilter uploadAccessFilter,
                          @Lazy TraceIdFilter traceIdFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.internalKeyFilter = internalKeyFilter;
        this.uploadAccessFilter = uploadAccessFilter;
        this.traceIdFilter = traceIdFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/api/v1/internal/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/prometheus", "/actuator/info").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                // /uploads/** 不再 permitAll：改为由 UploadAccessFilter 按文件归属校验鉴权（P0 安全加固）
                .anyRequest().authenticated()
            )
            .addFilterBefore(internalKeyFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            // P0 安全加固：JwtAuthFilter 之后执行，对 /uploads/** 按文件归属校验鉴权
            .addFilterAfter(uploadAccessFilter, UsernamePasswordAuthenticationFilter.class)
            // P1 可观测性：traceId 全链路透传。放在 UsernamePasswordAuthenticationFilter 之前执行，
            // 使 traceId 在所有鉴权/业务日志中可用。DisableAnonymousSessionFilter 在 SS6 已移除，
            // 改用带 @Order 的 UsernamePasswordAuthenticationFilter 作锚点。
            .addFilterBefore(traceIdFilter, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // 支持本地开发（Vite :5173、Node :3000）和 Docker/nginx 部署（:80）
        configuration.setAllowedOrigins(List.of(
            "http://localhost:5173",
            "http://localhost:3000",
            "http://localhost:80",
            "http://localhost:8081"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}