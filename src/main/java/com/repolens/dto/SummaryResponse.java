package com.repolens.dto;

public record SummaryResponse(
        String filePath,
        String summary,
        String model,
        String language,
        int analyzedChars
) {}
