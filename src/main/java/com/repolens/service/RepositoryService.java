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

@Service
public class RepositoryService {

    private final GitHubClient      githubClient;
    private final TreeBuilderService treeBuilder;
    private final TraversalService   traversal;

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

        List<GitHubTreeItem> items = githubClient.fetchTree(
                parsed.owner(), parsed.repo(), repoInfo.defaultBranch());

        TreeNode tree = treeBuilder.build(repoInfo.name(), items);

        String mode = request.traversalMode();
        List<TraversalEntry> entries = traversal.traverse(tree, mode);

        RepositoryInfo info = new RepositoryInfo(
                repoInfo.owner(),
                repoInfo.name(),
                repoInfo.defaultBranch(),
                repoInfo.htmlUrl()
        );

        return new ExploreResponse(info, tree, mode, entries);
    }
}
