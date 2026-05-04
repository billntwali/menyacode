package com.repolens.service;

import com.repolens.model.TraversalEntry;
import com.repolens.model.TreeNode;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

@Service
public class TraversalService {

    public List<TraversalEntry> traverse(TreeNode root, String mode) {
        return switch (mode.toLowerCase()) {
            case "dfs" -> dfs(root);
            case "bfs" -> bfs(root);
            default    -> throw new IllegalArgumentException("Unsupported traversal mode: " + mode +
                                 ". Use 'dfs' or 'bfs'.");
        };
    }

    private List<TraversalEntry> dfs(TreeNode root) {
        List<TraversalEntry> result = new ArrayList<>();
        // stack stores (node, depth); push children in reverse order to preserve left-to-right
        Deque<NodeDepth> stack = new ArrayDeque<>();
        stack.push(new NodeDepth(root, 0));

        while (!stack.isEmpty()) {
            NodeDepth current = stack.pop();
            result.add(entry(current.node, current.depth, result.size() + 1));

            List<TreeNode> children = current.node.children();
            for (int i = children.size() - 1; i >= 0; i--) {
                stack.push(new NodeDepth(children.get(i), current.depth + 1));
            }
        }
        return result;
    }

    private List<TraversalEntry> bfs(TreeNode root) {
        List<TraversalEntry> result = new ArrayList<>();
        Deque<NodeDepth> queue = new ArrayDeque<>();
        queue.add(new NodeDepth(root, 0));

        while (!queue.isEmpty()) {
            NodeDepth current = queue.poll();
            result.add(entry(current.node, current.depth, result.size() + 1));

            for (TreeNode child : current.node.children()) {
                queue.add(new NodeDepth(child, current.depth + 1));
            }
        }
        return result;
    }

    private static TraversalEntry entry(TreeNode node, int depth, int order) {
        return new TraversalEntry(node.path(), node.type(), depth, order);
    }

    private record NodeDepth(TreeNode node, int depth) {}
}
