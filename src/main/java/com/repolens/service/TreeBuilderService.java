package com.repolens.service;

import com.repolens.client.GitHubClient.GitHubTreeItem;
import com.repolens.model.NodeType;
import com.repolens.model.TreeNode;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class TreeBuilderService {

    public TreeNode build(String repoName, List<GitHubTreeItem> items) {
        MutableNode root = new MutableNode(repoName, "", "directory", null);

        List<GitHubTreeItem> sorted = items.stream()
                .filter(i -> i.path() != null && !i.path().isBlank())
                .sorted(Comparator.comparing(GitHubTreeItem::path))
                .toList();

        for (GitHubTreeItem item : sorted) {
            insertItem(root, item);
        }

        return toTreeNode(root);
    }

    private void insertItem(MutableNode root, GitHubTreeItem item) {
        String[] parts = item.path().split("/");
        MutableNode current = root;
        StringBuilder accumulatedPath = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) continue;

            if (!accumulatedPath.isEmpty()) accumulatedPath.append('/');
            accumulatedPath.append(part);

            String nodePath = accumulatedPath.toString();
            boolean isLeaf  = (i == parts.length - 1);

            if (isLeaf) {
                String nodeType = "tree".equals(item.type()) ? "directory" : "file";
                Integer size    = "file".equals(nodeType) ? item.size() : null;

                MutableNode existing = current.children.get(part);
                if (existing == null) {
                    current.children.put(part, new MutableNode(part, nodePath, nodeType, size));
                } else {
                    existing.type = nodeType;
                    existing.size = size;
                }
            } else {
                MutableNode next = current.children.get(part);
                if (next == null) {
                    next = new MutableNode(part, nodePath, "directory", null);
                    current.children.put(part, next);
                }
                current = next;
            }
        }
    }

    private TreeNode toTreeNode(MutableNode node) {
        List<TreeNode> children = node.children.values().stream()
                .sorted(Comparator
                        .comparing((MutableNode n) -> "file".equals(n.type) ? 1 : 0)
                        .thenComparing(n -> n.name.toLowerCase()))
                .map(this::toTreeNode)
                .toList();

        NodeType type = "directory".equals(node.type) ? NodeType.DIRECTORY : NodeType.FILE;
        return new TreeNode(node.name, node.path, type, node.size, children);
    }

    private static class MutableNode {
        final String name;
        final String path;
        String  type;
        Integer size;
        final Map<String, MutableNode> children = new LinkedHashMap<>();

        MutableNode(String name, String path, String type, Integer size) {
            this.name = name;
            this.path = path;
            this.type = type;
            this.size = size;
        }
    }
}
