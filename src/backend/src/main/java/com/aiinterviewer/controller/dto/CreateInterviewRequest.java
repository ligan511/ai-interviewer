package com.aiinterviewer.controller.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;

@Data
public class CreateInterviewRequest {
    @NotNull
    private Long jobId;

    private Long resumeId;

    private String type;

    private String difficulty;

    private Integer questionLimit;

    private Integer durationLimitSeconds;
}
