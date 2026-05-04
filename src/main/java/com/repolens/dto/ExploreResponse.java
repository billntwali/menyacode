package com.repolens.dto;

import com.repolens.model.RepositoryInfo;
import com.repolens.model.TraversalEntry;
import com.repolens.model.TreeNode;

import java.util.List;

public record ExploreResponse(
        RepositoryInfo repository,
        TreeNode tree,
        String traversalMode,
        List<TraversalEntry> traversal
) {}
