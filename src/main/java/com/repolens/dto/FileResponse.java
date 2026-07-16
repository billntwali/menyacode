package com.repolens.dto;

public record FileResponse(
        String filePath,
        String content,
        String language,
        long size,
        boolean truncated
) {}
