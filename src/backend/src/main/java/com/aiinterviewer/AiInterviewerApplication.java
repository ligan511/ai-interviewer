package com.aiinterviewer;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.aiinterviewer.mapper")
public class AiInterviewerApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiInterviewerApplication.class, args);
    }
}
