package com.repolens.model;

import java.util.List;

public record TreeNode(
        String name,
        String path,
        NodeType type,
        Integer size,
        List<TreeNode> children
) {}
