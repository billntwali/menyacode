package com.repolens.model;

public record RepositoryInfo(
        String owner,
        String name,
        String defaultBranch,
        String htmlUrl
) {}
