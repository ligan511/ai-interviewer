package com.aiinterviewer.controller.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;

@Data
public class SubmitAnswerRequest {
    @NotNull
    private Long questionId;

    private String answerText;

    private Integer clientDurationSeconds;
}
