package com.repolens.dto;

import jakarta.validation.constraints.NotBlank;

public record ExploreRequest(
        @NotBlank(message = "GitHub URL is required.")
        String repoUrl,
        String traversalMode,
        String branch
) {
    public ExploreRequest {
        if (traversalMode == null || traversalMode.isBlank()) {
            traversalMode = "dfs";
        }
    }
}
