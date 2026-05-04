package com.repolens.service;

import com.repolens.client.GitHubClient;
import com.repolens.client.GitHubClient.FileContentsResult;
import com.repolens.client.GitHubClient.ParsedRepo;
import com.repolens.client.OpenAiClient;
import com.repolens.dto.SummaryRequest;
import com.repolens.dto.SummaryResponse;
import com.repolens.exception.BinaryFileException;
import com.repolens.exception.FileTooLargeException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class SummaryService {

    private final GitHubClient githubClient;
    private final OpenAiClient openAiClient;
    private final long         maxFileSizeBytes;
    private final int          maxContentChars;

    public SummaryService(
            GitHubClient githubClient,
            OpenAiClient openAiClient,
            @Value("${repolens.max-file-size-bytes:100000}") long maxFileSizeBytes,
            @Value("${repolens.max-content-chars:80000}") int maxContentChars) {

        this.githubClient     = githubClient;
        this.openAiClient     = openAiClient;
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.maxContentChars  = maxContentChars;
    }

    public SummaryResponse summarize(SummaryRequest request) {
        ParsedRepo parsed = githubClient.parseUrl(request.repoUrl());

        FileContentsResult result = githubClient.fetchFileContents(
                parsed.owner(), parsed.repo(), request.filePath());

        if (result.size() > maxFileSizeBytes) {
            throw new FileTooLargeException(
                    "File '" + request.filePath() + "' is too large for summarization " +
                    "(" + result.size() + " bytes; limit is " + maxFileSizeBytes + " bytes).");
        }

        String textContent = decodeContent(result, request.filePath());

        if (textContent.length() > maxContentChars) {
            textContent = textContent.substring(0, maxContentChars);
        }

        String summary = openAiClient.generateSummary(request.filePath(), textContent);
        return new SummaryResponse(request.filePath(), summary);
    }

    private String decodeContent(FileContentsResult result, String filePath) {
        if (result.content() == null || result.content().isBlank()) {
            return "";
        }

        if (!"base64".equals(result.encoding())) {
            return result.content();
        }

        // GitHub wraps base64 with newlines every 60 chars
        String stripped = result.content().replaceAll("\\s", "");
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(stripped);
        } catch (IllegalArgumentException e) {
            throw new BinaryFileException(
                    "Could not decode file '" + filePath + "'. It may be a binary file.");
        }

        for (byte b : bytes) {
            if (b == 0) {
                throw new BinaryFileException(
                        "File '" + filePath + "' appears to be a binary file and cannot be summarized.");
            }
        }

        return new String(bytes, StandardCharsets.UTF_8);
    }
}
