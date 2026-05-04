package com.repolens.model;

public record TraversalEntry(
        String path,
        NodeType type,
        int depth,
        int order
) {}
