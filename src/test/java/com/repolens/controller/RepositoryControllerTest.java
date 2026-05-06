package com.repolens.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.repolens.dto.ExploreRequest;
import com.repolens.dto.ExploreResponse;
import com.repolens.dto.SummaryRequest;
import com.repolens.dto.SummaryResponse;
import com.repolens.exception.GitHubNotFoundException;
import com.repolens.exception.GitHubRateLimitException;
import com.repolens.exception.GitHubResponseException;
import com.repolens.model.NodeType;
import com.repolens.model.RepositoryInfo;
import com.repolens.model.TraversalEntry;
import com.repolens.model.TreeNode;
import com.repolens.service.RepositoryService;
import com.repolens.service.SummaryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {RepositoryController.class, HealthController.class})
class RepositoryControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean RepositoryService repositoryService;
    @MockBean SummaryService    summaryService;

    // ── Helpers ──────────────────────────────────────────────────

    private ExploreResponse sampleResponse() {
        RepositoryInfo info = new RepositoryInfo("owner", "repo", "main", "https://github.com/owner/repo");
        TreeNode root = new TreeNode("repo", "", NodeType.DIRECTORY, null,
                List.of(new TreeNode("README.md", "README.md", NodeType.FILE, 100, List.of())));
        List<TraversalEntry> entries = List.of(
                new TraversalEntry("", NodeType.DIRECTORY, 0, 1),
                new TraversalEntry("README.md", NodeType.FILE, 1, 2)
        );
        return new ExploreResponse(info, root, "dfs", entries);
    }

    // ── Health ───────────────────────────────────────────────────

    @Test
    void health_returns200WithStatusOk() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    // ── Explore – happy path ──────────────────────────────────────

    @Test
    void explore_validRequest_returns200WithPayload() throws Exception {
        when(repositoryService.explore(any())).thenReturn(sampleResponse());

        mvc.perform(post("/api/repository/explore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"repo_url":"https://github.com/owner/repo","traversal_mode":"dfs"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repository.owner").value("owner"))
                .andExpect(jsonPath("$.repository.name").value("repo"))
                .andExpect(jsonPath("$.repository.default_branch").value("main"))
                .andExpect(jsonPath("$.traversal_mode").value("dfs"))
                .andExpect(jsonPath("$.traversal").isArray())
                .andExpect(jsonPath("$.tree.type").value("directory"));
    }

    @Test
    void explore_defaultsTraversalModeToDfs() throws Exception {
        when(repositoryService.explore(any())).thenReturn(sampleResponse());

        mvc.perform(post("/api/repository/explore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"repo_url":"https://github.com/owner/repo"}
                                """))
                .andExpect(status().isOk());

        verify(repositoryService).explore(argThat(req -> "dfs".equals(req.traversalMode())));
    }

    // ── Explore – validation errors ───────────────────────────────

    @Test
    void explore_missingRepoUrl_returns400WithDetail() throws Exception {
        mvc.perform(post("/api/repository/explore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"repo_url":"","traversal_mode":"dfs"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void explore_blankBody_returns400() throws Exception {
        mvc.perform(post("/api/repository/explore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ── Explore – service exceptions ──────────────────────────────

    @Test
    void explore_repoNotFound_returns404() throws Exception {
        when(repositoryService.explore(any()))
                .thenThrow(new GitHubNotFoundException("Repository not found."));

        mvc.perform(post("/api/repository/explore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"repo_url":"https://github.com/owner/repo","traversal_mode":"dfs"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Repository not found."));
    }

    @Test
    void explore_rateLimitExceeded_returns429() throws Exception {
        when(repositoryService.explore(any()))
                .thenThrow(new GitHubRateLimitException("Rate limit reached."));

        mvc.perform(post("/api/repository/explore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"repo_url":"https://github.com/owner/repo","traversal_mode":"dfs"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value("Rate limit reached."));
    }

    @Test
    void explore_upstreamFailure_returns502() throws Exception {
        when(repositoryService.explore(any()))
                .thenThrow(new GitHubResponseException("GitHub API error."));

        mvc.perform(post("/api/repository/explore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"repo_url":"https://github.com/owner/repo","traversal_mode":"dfs"}
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("GitHub API error."));
    }

    @Test
    void explore_badUrl_returns400() throws Exception {
        when(repositoryService.explore(any()))
                .thenThrow(new IllegalArgumentException("Please provide a valid github.com repository URL."));

        mvc.perform(post("/api/repository/explore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"repo_url":"https://notgithub.com/owner/repo","traversal_mode":"dfs"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").exists());
    }

    // ── Summary – happy path ──────────────────────────────────────

    @Test
    void summary_validRequest_returns200WithSummary() throws Exception {
        when(summaryService.summarize(any()))
                .thenReturn(new SummaryResponse("src/Main.java", "This file contains the entry point.", "gpt-4o-mini", "Java", 1234));

        mvc.perform(post("/api/repository/summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"repo_url":"https://github.com/owner/repo","file_path":"src/Main.java"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.file_path").value("src/Main.java"))
                .andExpect(jsonPath("$.summary").value("This file contains the entry point."));
    }

    // ── Summary – validation errors ───────────────────────────────

    @Test
    void summary_missingFilePath_returns400() throws Exception {
        mvc.perform(post("/api/repository/summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"repo_url":"https://github.com/owner/repo","file_path":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void summary_missingRepoUrl_returns400() throws Exception {
        mvc.perform(post("/api/repository/summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"repo_url":"","file_path":"README.md"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").exists());
    }
}
