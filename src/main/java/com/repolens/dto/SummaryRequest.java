package com.repolens.dto;

import jakarta.validation.constraints.NotBlank;

public record SummaryRequest(
        @NotBlank(message = "GitHub URL is required.")
        String repoUrl,
        @NotBlank(message = "File path is required.")
        String filePath
) {}
