package com.repolens.model;

public record RepositoryInfo(
        String owner,
        String name,
        String defaultBranch,
        String htmlUrl,
        String description,
        String language,
        int stars,
        int forks,
        String license,
        String updatedAt,
        java.util.List<String> topics
) {}
