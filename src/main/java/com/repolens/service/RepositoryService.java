package com.repolens.service;

import com.repolens.client.GitHubClient;
import com.repolens.client.GitHubClient.GitHubTreeItem;
import com.repolens.client.GitHubClient.ParsedRepo;
import com.repolens.client.GitHubClient.RepoInfo;
import com.repolens.dto.ExploreRequest;
import com.repolens.dto.ExploreResponse;
import com.repolens.model.RepositoryInfo;
import com.repolens.model.TraversalEntry;
import com.repolens.model.TreeNode;
import org.springframework.stereotype.Service;

import java.util.List;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;

@Service
public class RepositoryService {

    private final GitHubClient      githubClient;
    private final TreeBuilderService treeBuilder;
    private final TraversalService   traversal;
    private final Cache<String, CachedRepository> cache = Caffeine.newBuilder()
            .maximumSize(100).expireAfterWrite(Duration.ofMinutes(10)).build();

    public RepositoryService(GitHubClient githubClient,
                             TreeBuilderService treeBuilder,
                             TraversalService traversal) {
        this.githubClient = githubClient;
        this.treeBuilder  = treeBuilder;
        this.traversal    = traversal;
    }

    public ExploreResponse explore(ExploreRequest request) {
        ParsedRepo parsed = githubClient.parseUrl(request.repoUrl());

        RepoInfo repoInfo = githubClient.fetchRepoInfo(parsed.owner(), parsed.repo());
        String branch = request.branch() == null || request.branch().isBlank()
                ? repoInfo.defaultBranch() : request.branch().strip();
        String cacheKey = parsed.owner() + "/" + parsed.repo() + "@" + branch;
        CachedRepository cached = cache.get(cacheKey, key -> new CachedRepository(
                githubClient.fetchTree(parsed.owner(), parsed.repo(), branch)));
        List<GitHubTreeItem> items = cached.items();

        TreeNode tree = treeBuilder.build(repoInfo.name(), items);

        String mode = request.traversalMode();
        List<TraversalEntry> entries = traversal.traverse(tree, mode);

        RepositoryInfo info = new RepositoryInfo(
                repoInfo.owner(),
                repoInfo.name(),
                branch,
                repoInfo.htmlUrl(), repoInfo.description(), repoInfo.language(),
                repoInfo.stars(), repoInfo.forks(), repoInfo.license(), repoInfo.updatedAt(), repoInfo.topics()
        );

        return new ExploreResponse(info, tree, mode, entries);
    }

    private record CachedRepository(List<GitHubTreeItem> items) {}
}
