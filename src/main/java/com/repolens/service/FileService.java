package com.repolens.service;

import com.repolens.client.GitHubClient;
import com.repolens.dto.FileRequest;
import com.repolens.dto.FileResponse;
import com.repolens.exception.BinaryFileException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class FileService {
    private static final int PREVIEW_LIMIT = 120_000;
    private final GitHubClient github;
    private final LanguageService languages;

    public FileService(GitHubClient github, LanguageService languages) {
        this.github = github;
        this.languages = languages;
    }

    public FileResponse preview(FileRequest request) {
        var repo = github.parseUrl(request.repoUrl());
        var result = github.fetchFileContents(repo.owner(), repo.repo(), request.filePath());
        String content = decode(result, request.filePath());
        boolean truncated = content.length() > PREVIEW_LIMIT;
        if (truncated) content = content.substring(0, PREVIEW_LIMIT);
        return new FileResponse(request.filePath(), content, languages.detect(request.filePath()), result.size(), truncated);
    }

    static String decode(GitHubClient.FileContentsResult result, String path) {
        if (result.content() == null || result.content().isBlank()) return "";
        if (!"base64".equals(result.encoding())) return result.content();
        try {
            byte[] bytes = Base64.getDecoder().decode(result.content().replaceAll("\\s", ""));
            for (byte b : bytes) if (b == 0) throw new BinaryFileException("File '" + path + "' is binary and cannot be previewed.");
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new BinaryFileException("Could not decode file '" + path + "'.");
        }
    }
}
