package com.codewisdom.resource.importer;

import com.codewisdom.resource.config.ImportProperties;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 仓库拉取测试（本地 {@code file://}，离线确定性）。
 *
 * <p>重点覆盖「重复导入同一工作区」——这是唯一能暴露 Windows 只读 pack 文件
 * 删除失败的场景，第一次拉取永远是好的，坑在第二次。
 */
@DisplayName("仓库拉取 GitRepoFetcher")
class GitRepoFetcherTest {

    @TempDir
    Path tempDir;

    private GitRepoFetcher fetcher;
    private Path origin;

    @BeforeEach
    void setUp() throws Exception {
        ImportProperties properties = new ImportProperties();
        properties.setCloneTimeoutSeconds(30);
        properties.setCloneDepth(1);
        fetcher = new GitRepoFetcher(properties);

        origin = tempDir.resolve("origin");
        Files.createDirectories(origin);
        Files.writeString(origin.resolve("README.md"), "# origin\n");
        Files.createDirectories(origin.resolve("src"));
        Files.writeString(origin.resolve("src/App.java"), "class App {}\n");

        try (Git git = Git.init().setDirectory(origin.toFile()).call()) {
            PersonIdent who = new PersonIdent("CodeWisdom", "dev@codewisdom.local");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("init").setAuthor(who).setCommitter(who).setSign(false).call();
        }
    }

    @Test
    @DisplayName("克隆本地仓库，返回分支与 HEAD 提交，且工作区文件就位")
    void clonesLocalRepository() {
        Path workspace = tempDir.resolve("ws-1");

        GitRepoFetcher.FetchResult result = fetcher.fetch(origin.toUri(), null, workspace);

        assertThat(result.workspace()).isEqualTo(workspace);
        assertThat(result.branch()).isNotBlank();
        assertThat(result.headCommit()).hasSize(40);
        assertThat(workspace.resolve("README.md")).exists();
        assertThat(workspace.resolve("src/App.java")).exists();
        assertThat(workspace.resolve(".git")).as(".git 保留，文件树阶段再过滤").exists();
    }

    @Test
    @DisplayName("重复拉取同一工作区：清空重建成功（Windows 只读 pack 文件回归测试）")
    void reclonesIntoExistingWorkspace() {
        Path workspace = tempDir.resolve("ws-reuse");

        fetcher.fetch(origin.toUri(), null, workspace);
        assertThat(workspace.resolve("README.md")).exists();

        // 第二次：工作区已存在且含只读的 git pack 文件，必须先清空再拉
        assertThatCode(() -> fetcher.fetch(origin.toUri(), null, workspace))
                .as("重复导入不得因只读文件删除失败")
                .doesNotThrowAnyException();

        GitRepoFetcher.FetchResult second = fetcher.fetch(origin.toUri(), null, workspace);
        assertThat(second.headCommit()).hasSize(40);
        assertThat(workspace.resolve("README.md")).exists();

        // 不留旧内容：手工塞进去的文件应已被清掉
        assertThat(workspace.resolve("stale.txt")).doesNotExist();
    }

    @Test
    @DisplayName("工作区残留的旧文件在重新拉取后被清除，保证快照干净")
    void oldWorkspaceContentIsPurged() throws Exception {
        Path workspace = tempDir.resolve("ws-stale");
        fetcher.fetch(origin.toUri(), null, workspace);

        Files.writeString(workspace.resolve("stale.txt"), "leftover");

        fetcher.fetch(origin.toUri(), null, workspace);

        assertThat(workspace.resolve("stale.txt")).doesNotExist();
    }

    @Test
    @DisplayName("指定分支时检出该分支")
    void checksOutRequestedBranch() throws Exception {
        try (Git git = Git.open(origin.toFile())) {
            git.checkout().setCreateBranch(true).setName("feature/x").call();
            Files.writeString(origin.resolve("feature.txt"), "on branch\n");
            PersonIdent who = new PersonIdent("CodeWisdom", "dev@codewisdom.local");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("feature").setAuthor(who).setCommitter(who).setSign(false).call();
        }
        Path workspace = tempDir.resolve("ws-branch");

        GitRepoFetcher.FetchResult result = fetcher.fetch(origin.toUri(), "feature/x", workspace);

        assertThat(result.branch()).isEqualTo("feature/x");
        assertThat(workspace.resolve("feature.txt")).exists();
    }
}
