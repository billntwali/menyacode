package com.repolens.service;

import com.repolens.client.GitHubClient.GitHubTreeItem;
import com.repolens.model.NodeType;
import com.repolens.model.TreeNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class TreeBuilderServiceTest {

    private TreeBuilderService service;

    @BeforeEach
    void setUp() {
        service = new TreeBuilderService();
    }

    // ── Helpers ──────────────────────────────────────────────────

    private static GitHubTreeItem blob(String path, Integer size) {
        return new GitHubTreeItem(path, "blob", size);
    }

    private static GitHubTreeItem tree(String path) {
        return new GitHubTreeItem(path, "tree", null);
    }

    private static TreeNode find(TreeNode root, String name) {
        if (root.name().equals(name)) return root;
        for (TreeNode child : root.children()) {
            TreeNode found = find(child, name);
            if (found != null) return found;
        }
        return null;
    }

    // ── Basic structure ──────────────────────────────────────────

    @Test
    void emptyItemList_returnsRootOnly() {
        TreeNode root = service.build("my-repo", List.of());

        assertThat(root.name()).isEqualTo("my-repo");
        assertThat(root.type()).isEqualTo(NodeType.DIRECTORY);
        assertThat(root.children()).isEmpty();
        assertThat(root.path()).isEqualTo("");
    }

    @Test
    void singleFile_appearsUnderRoot() {
        TreeNode root = service.build("repo", List.of(blob("README.md", 512)));

        assertThat(root.children()).hasSize(1);
        TreeNode readme = root.children().get(0);
        assertThat(readme.name()).isEqualTo("README.md");
        assertThat(readme.type()).isEqualTo(NodeType.FILE);
        assertThat(readme.size()).isEqualTo(512);
        assertThat(readme.path()).isEqualTo("README.md");
    }

    @Test
    void explicitDirectory_createdCorrectly() {
        TreeNode root = service.build("repo", List.of(
                tree("src"),
                blob("src/Main.java", 1024)
        ));

        TreeNode src = root.children().stream()
                .filter(n -> n.name().equals("src"))
                .findFirst()
                .orElseThrow();

        assertThat(src.type()).isEqualTo(NodeType.DIRECTORY);
        assertThat(src.children()).hasSize(1);
        assertThat(src.children().get(0).name()).isEqualTo("Main.java");
    }

    @Test
    void impliedDirectory_createdWhenNotExplicitlyListed() {
        // No explicit "src" tree item; it should be inferred from file paths
        TreeNode root = service.build("repo", List.of(
                blob("src/Main.java", 200),
                blob("src/Util.java", 100)
        ));

        TreeNode src = find(root, "src");
        assertThat(src).isNotNull();
        assertThat(src.type()).isEqualTo(NodeType.DIRECTORY);
        assertThat(src.children()).hasSize(2);
    }

    @Test
    void deeplyNestedPath_builtCorrectly() {
        TreeNode root = service.build("repo", List.of(
                blob("a/b/c/deep.txt", 10)
        ));

        TreeNode a    = find(root, "a");
        TreeNode b    = find(root, "b");
        TreeNode c    = find(root, "c");
        TreeNode deep = find(root, "deep.txt");

        assertThat(a).isNotNull();
        assertThat(b).isNotNull();
        assertThat(c).isNotNull();
        assertThat(deep).isNotNull();
        assertThat(deep.type()).isEqualTo(NodeType.FILE);
        assertThat(deep.path()).isEqualTo("a/b/c/deep.txt");
    }

    // ── Sorting ──────────────────────────────────────────────────

    @Test
    void children_directoriesBeforeFiles() {
        TreeNode root = service.build("repo", List.of(
                blob("readme.md", 100),
                tree("src"),
                blob("build.gradle", 50),
                tree("test")
        ));

        List<TreeNode> children = root.children();
        int lastDirIdx  = -1;
        int firstFileIdx = Integer.MAX_VALUE;

        for (int i = 0; i < children.size(); i++) {
            if (children.get(i).type() == NodeType.DIRECTORY) lastDirIdx  = i;
            if (children.get(i).type() == NodeType.FILE)      firstFileIdx = Math.min(firstFileIdx, i);
        }

        assertThat(lastDirIdx).isLessThan(firstFileIdx);
    }

    @Test
    void children_sortedAlphabeticallyWithinGroup() {
        TreeNode root = service.build("repo", List.of(
                blob("zeta.txt", 1),
                blob("alpha.txt", 1),
                blob("mid.txt", 1)
        ));

        List<String> names = root.children().stream().map(TreeNode::name).toList();
        assertThat(names).containsExactly("alpha.txt", "mid.txt", "zeta.txt");
    }

    // ── Size handling ─────────────────────────────────────────────

    @Test
    void fileSize_preserved() {
        TreeNode root = service.build("repo", List.of(blob("big.bin", 999_999)));
        assertThat(root.children().get(0).size()).isEqualTo(999_999);
    }

    @Test
    void directorySize_isNull() {
        TreeNode root = service.build("repo", List.of(
                tree("src"),
                blob("src/X.java", 100)
        ));
        TreeNode src = find(root, "src");
        assertThat(src.size()).isNull();
    }
}
