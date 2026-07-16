package com.repolens.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repolens.exception.DirectorySelectedException;
import com.repolens.exception.GitHubNotFoundException;
import com.repolens.exception.GitHubRateLimitException;
import com.repolens.exception.GitHubResponseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;

@Component
public class GitHubClient {

    private static final Set<String> ALLOWED_HOSTS = Set.of("github.com", "www.github.com");

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GitHubClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${github.token:}") String token) {

        this.objectMapper = objectMapper;

        RestClient.Builder b = builder
                .baseUrl("https://api.github.com")
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .defaultHeader("User-Agent", "repolens/1.0");

        if (!token.isBlank()) {
            b = b.defaultHeader("Authorization", "Bearer " + token);
        }

        this.restClient = b.build();
    }

    // ──────────────────────────────────────────────────────────
    // URL parsing
    // ──────────────────────────────────────────────────────────

    public ParsedRepo parseUrl(String repoUrl) {
        String normalized = repoUrl == null ? "" : repoUrl.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("GitHub URL is required.");
        }
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://" + normalized;
        }

        URI uri;
        try {
            uri = new URI(normalized);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Please provide a valid github.com repository URL.");
        }

        String host = uri.getHost();
        if (host == null || !ALLOWED_HOSTS.contains(host.toLowerCase())) {
            throw new IllegalArgumentException("Please provide a valid github.com repository URL.");
        }

        String rawPath = uri.getPath() == null ? "" : uri.getPath();
        String[] parts = rawPath.split("/");
        List<String> segments = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) segments.add(part);
        }

        if (segments.size() < 2) {
            throw new IllegalArgumentException("GitHub URL must include owner and repository name.");
        }

        String owner = segments.get(0).strip();
        String repo  = segments.get(1).strip();
        if (repo.endsWith(".git")) {
            repo = repo.substring(0, repo.length() - 4);
        }

        if (owner.isEmpty() || repo.isEmpty()) {
            throw new IllegalArgumentException("GitHub URL must include owner and repository name.");
        }

        return new ParsedRepo(owner, repo);
    }

    // ──────────────────────────────────────────────────────────
    // Repository info
    // ──────────────────────────────────────────────────────────

    public RepoInfo fetchRepoInfo(String owner, String repo) {
        JsonNode node = get("/repos/{owner}/{repo}", owner, repo);

        String defaultBranch = textOrNull(node, "default_branch");
        if (defaultBranch == null || defaultBranch.isBlank()) {
            throw new GitHubResponseException("Could not determine repository default branch.");
        }

        String htmlUrl  = node.has("html_url")  ? node.get("html_url").asText()  : "https://github.com/" + owner + "/" + repo;
        String repoName = node.has("name")       ? node.get("name").asText()      : repo;

        String license = node.path("license").path("spdx_id").asText("");
        List<String> topics = node.path("topics").isArray()
                ? StreamSupport.stream(node.path("topics").spliterator(), false).map(JsonNode::asText).toList()
                : List.of();
        return new RepoInfo(owner, repoName, defaultBranch, htmlUrl,
                textOrNull(node, "description"), textOrNull(node, "language"),
                node.path("stargazers_count").asInt(), node.path("forks_count").asInt(),
                license, textOrNull(node, "updated_at"), topics);
    }

    // ──────────────────────────────────────────────────────────
    // Repository tree
    // ──────────────────────────────────────────────────────────

    public List<GitHubTreeItem> fetchTree(String owner, String repo, String branch) {
        JsonNode node = get("/repos/{owner}/{repo}/git/trees/{branch}?recursive=1", owner, repo, branch);

        if (node.has("truncated") && node.get("truncated").asBoolean()) {
            throw new GitHubResponseException(
                    "Repository tree is too large for a single request. Try a smaller repository.");
        }

        JsonNode treeNode = node.get("tree");
        if (treeNode == null || !treeNode.isArray()) {
            throw new GitHubResponseException("Unexpected GitHub API response while fetching tree.");
        }

        List<GitHubTreeItem> items = new ArrayList<>();
        for (JsonNode item : treeNode) {
            String type = textOrNull(item, "type");
            if (!"tree".equals(type) && !"blob".equals(type)) continue;

            String path = textOrNull(item, "path");
            if (path == null || path.isBlank()) continue;

            Integer size = item.has("size") && !item.get("size").isNull()
                    ? item.get("size").asInt()
                    : null;

            items.add(new GitHubTreeItem(path, type, size));
        }
        return items;
    }

    // ──────────────────────────────────────────────────────────
    // File contents (for summary)
    // ──────────────────────────────────────────────────────────

    public FileContentsResult fetchFileContents(String owner, String repo, String filePath) {
        String rawBody;
        try {
            rawBody = restClient.get()
                    .uri("/repos/{owner}/{repo}/contents/{path}", owner, repo, filePath)
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (req, res) -> {
                        throw new GitHubNotFoundException(
                                "File not found: " + filePath + ". Verify the path and repository.");
                    })
                    .onStatus(status -> isRateLimit(status.value(), null), (req, res) -> {
                        String reset = res.getHeaders().getFirst("X-RateLimit-Reset");
                        throw new GitHubRateLimitException(rateLimitMessage(reset));
                    })
                    .onStatus(status -> status.value() == 403, (req, res) -> {
                        String remaining = res.getHeaders().getFirst("X-RateLimit-Remaining");
                        if ("0".equals(remaining)) {
                            String reset = res.getHeaders().getFirst("X-RateLimit-Reset");
                            throw new GitHubRateLimitException(rateLimitMessage(reset));
                        }
                        throw new GitHubResponseException("GitHub API request forbidden.");
                    })
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), (req, res) -> {
                        throw new GitHubResponseException(
                                "GitHub API request failed (" + res.getStatusCode().value() + ").");
                    })
                    .body(String.class);
        } catch (ResourceAccessException e) {
            throw new GitHubResponseException(
                    "Could not connect to GitHub API. Please try again shortly.");
        }

        if (rawBody == null) {
            throw new GitHubResponseException("Empty response from GitHub API.");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            throw new GitHubResponseException("Could not parse GitHub API response.");
        }

        if (root.isArray()) {
            throw new DirectorySelectedException(
                    "'" + filePath + "' is a directory. Please select a file to summarize.");
        }

        String type = textOrNull(root, "type");
        if ("dir".equals(type) || "submodule".equals(type) || "symlink".equals(type)) {
            throw new DirectorySelectedException(
                    "'" + filePath + "' is not a regular file. Please select a file to summarize.");
        }

        long size = root.has("size") ? root.get("size").asLong() : 0L;
        String encoding = textOrNull(root, "encoding");
        String content  = textOrNull(root, "content");

        return new FileContentsResult(filePath, size, encoding, content);
    }

    // ──────────────────────────────────────────────────────────
    // Internal helpers
    // ──────────────────────────────────────────────────────────

    private JsonNode get(String uriTemplate, Object... vars) {
        String body;
        try {
            body = restClient.get()
                    .uri(uriTemplate, vars)
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (req, res) -> {
                        throw new GitHubNotFoundException(
                                "Repository not found or is not publicly accessible.");
                    })
                    .onStatus(status -> status.value() == 429, (req, res) -> {
                        String reset = res.getHeaders().getFirst("X-RateLimit-Reset");
                        throw new GitHubRateLimitException(rateLimitMessage(reset));
                    })
                    .onStatus(status -> status.value() == 403, (req, res) -> {
                        String remaining = res.getHeaders().getFirst("X-RateLimit-Remaining");
                        if ("0".equals(remaining)) {
                            String reset = res.getHeaders().getFirst("X-RateLimit-Reset");
                            throw new GitHubRateLimitException(rateLimitMessage(reset));
                        }
                        String resetTime = res.getHeaders().getFirst("X-RateLimit-Reset");
                        if (resetTime != null) {
                            throw new GitHubRateLimitException(rateLimitMessage(resetTime));
                        }
                        throw new GitHubResponseException("GitHub API request forbidden.");
                    })
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), (req, res) -> {
                        throw new GitHubResponseException(
                                "GitHub API request failed (" + res.getStatusCode().value() + ").");
                    })
                    .body(String.class);
        } catch (ResourceAccessException e) {
            throw new GitHubResponseException(
                    "Could not connect to GitHub API. Please try again shortly.");
        } catch (RestClientResponseException e) {
            throw new GitHubResponseException(
                    "GitHub API request failed (" + e.getStatusCode().value() + ").");
        }

        if (body == null) {
            throw new GitHubResponseException("Empty response from GitHub API.");
        }

        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new GitHubResponseException("Could not parse GitHub API response.");
        }
    }

    private static boolean isRateLimit(int status, String remaining) {
        return status == 429 || (status == 403 && "0".equals(remaining));
    }

    private static String rateLimitMessage(String resetEpoch) {
        if (resetEpoch != null) {
            return "GitHub API rate limit reached. Retry after reset time: " + resetEpoch + ".";
        }
        return "GitHub API rate limit reached. Please try again later.";
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return null;
        return node.get(field).asText();
    }

    // ──────────────────────────────────────────────────────────
    // Value objects returned by this client
    // ──────────────────────────────────────────────────────────

    public record ParsedRepo(String owner, String repo) {}

    public record RepoInfo(String owner, String name, String defaultBranch, String htmlUrl,
                           String description, String language, int stars, int forks,
                           String license, String updatedAt, List<String> topics) {}

    public record GitHubTreeItem(String path, String type, Integer size) {}

    public record FileContentsResult(String path, long size, String encoding, String content) {}
}
