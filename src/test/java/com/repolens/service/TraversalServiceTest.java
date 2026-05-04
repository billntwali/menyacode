package com.repolens.service;

import com.repolens.model.NodeType;
import com.repolens.model.TraversalEntry;
import com.repolens.model.TreeNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class TraversalServiceTest {

    private TraversalService service;

    @BeforeEach
    void setUp() {
        service = new TraversalService();
    }

    // ── Helpers ──────────────────────────────────────────────────

    private static TreeNode file(String name, String path) {
        return new TreeNode(name, path, NodeType.FILE, null, List.of());
    }

    private static TreeNode dir(String name, String path, TreeNode... children) {
        return new TreeNode(name, path, NodeType.DIRECTORY, null, List.of(children));
    }

    // ── DFS tests ────────────────────────────────────────────────

    @Test
    void dfs_singleNode_returnsOneEntry() {
        TreeNode root = file("file.txt", "file.txt");

        List<TraversalEntry> result = service.traverse(root, "dfs");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).path()).isEqualTo("file.txt");
        assertThat(result.get(0).order()).isEqualTo(1);
        assertThat(result.get(0).depth()).isEqualTo(0);
    }

    @Test
    void dfs_twoLevelTree_visitedInPreOrder() {
        //  root
        //  ├── a
        //  │   └── c
        //  └── b
        TreeNode c    = file("c", "a/c");
        TreeNode a    = dir("a", "a", c);
        TreeNode b    = file("b", "b");
        TreeNode root = dir("root", "", a, b);

        List<TraversalEntry> result = service.traverse(root, "dfs");
        List<String> paths = result.stream().map(TraversalEntry::path).toList();

        assertThat(paths).containsExactly("", "a", "a/c", "b");
    }

    @Test
    void dfs_ordersAreSequential() {
        TreeNode root = dir("root", "", file("x", "x"), file("y", "y"));

        List<TraversalEntry> result = service.traverse(root, "dfs");

        for (int i = 0; i < result.size(); i++) {
            assertThat(result.get(i).order()).isEqualTo(i + 1);
        }
    }

    @Test
    void dfs_depthIsCorrect() {
        TreeNode leaf = file("leaf", "a/b/leaf");
        TreeNode b    = dir("b", "a/b", leaf);
        TreeNode a    = dir("a", "a", b);
        TreeNode root = dir("root", "", a);

        List<TraversalEntry> result = service.traverse(root, "dfs");

        assertThat(result.get(0).depth()).isEqualTo(0); // root
        assertThat(result.get(1).depth()).isEqualTo(1); // a
        assertThat(result.get(2).depth()).isEqualTo(2); // a/b
        assertThat(result.get(3).depth()).isEqualTo(3); // a/b/leaf
    }

    // ── BFS tests ────────────────────────────────────────────────

    @Test
    void bfs_twoLevelTree_visitedLevelByLevel() {
        //  root
        //  ├── a
        //  │   └── c
        //  └── b
        TreeNode c    = file("c", "a/c");
        TreeNode a    = dir("a", "a", c);
        TreeNode b    = file("b", "b");
        TreeNode root = dir("root", "", a, b);

        List<TraversalEntry> result = service.traverse(root, "bfs");
        List<String> paths = result.stream().map(TraversalEntry::path).toList();

        assertThat(paths).containsExactly("", "a", "b", "a/c");
    }

    @Test
    void bfs_ordersAreSequential() {
        TreeNode root = dir("root", "", file("x", "x"), file("y", "y"), file("z", "z"));

        List<TraversalEntry> result = service.traverse(root, "bfs");

        for (int i = 0; i < result.size(); i++) {
            assertThat(result.get(i).order()).isEqualTo(i + 1);
        }
    }

    @Test
    void bfs_depthIsCorrect() {
        TreeNode leaf = file("leaf", "a/leaf");
        TreeNode a    = dir("a", "a", leaf);
        TreeNode b    = file("b", "b");
        TreeNode root = dir("root", "", a, b);

        List<TraversalEntry> result = service.traverse(root, "bfs");

        // root=0, a=1, b=1, leaf=2
        assertThat(result.stream().filter(e -> e.path().equals("")).findFirst().orElseThrow().depth()).isEqualTo(0);
        assertThat(result.stream().filter(e -> e.path().equals("a")).findFirst().orElseThrow().depth()).isEqualTo(1);
        assertThat(result.stream().filter(e -> e.path().equals("b")).findFirst().orElseThrow().depth()).isEqualTo(1);
        assertThat(result.stream().filter(e -> e.path().equals("a/leaf")).findFirst().orElseThrow().depth()).isEqualTo(2);
    }

    // ── Mode validation ──────────────────────────────────────────

    @Test
    void unknownMode_throwsIllegalArgument() {
        TreeNode root = file("f", "f");
        assertThatThrownBy(() -> service.traverse(root, "xyz"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported traversal mode");
    }

    @Test
    void modeIsCaseInsensitive() {
        TreeNode root = file("f", "f");
        assertThatNoException().isThrownBy(() -> service.traverse(root, "DFS"));
        assertThatNoException().isThrownBy(() -> service.traverse(root, "BFS"));
    }
}
