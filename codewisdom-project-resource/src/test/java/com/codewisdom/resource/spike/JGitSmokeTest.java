package com.codewisdom.resource.spike;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * S4 冒烟：验证 JGit 能拉取公开仓库并遍历文件树。
 *
 * <p>分两层：
 * <ul>
 *   <li><b>离线确定性层</b>（无 {@code network} 标签）：本地建仓 → file:// 克隆，
 *       验证克隆链路与文件树遍历逻辑，保证 {@code mvn clean verify} 在离线环境也是绿的；</li>
 *   <li><b>真实网络层</b>（{@code network} 标签）：拉取 Gitee 公开仓库，
 *       验证真实场景。网络不可达时自动跳过而非失败。</li>
 * </ul>
 *
 * <p><b>环境说明</b>：本机实测 {@code github.com} 不可达（连接超时），
 * 而 {@code gitee.com} 正常，故网络冒烟默认指向 Gitee。
 */
@DisplayName("S4 冒烟：JGit 仓库拉取")
class JGitSmokeTest {

    /** 真实网络冒烟目标：公开、体量适中（浅克隆约 18MB / 700 文件）。 */
    private static final String GITEE_REPO = "https://gitee.com/y_project/RuoYi.git";

    @TempDir
    Path tempDir;

    // ---------- 离线确定性层 ----------

    @Test
    @DisplayName("离线：本地建仓后经 file:// 克隆，文件树与提交可读")
    void clonesLocalRepositoryAndWalksTree() throws Exception {
        Path origin = tempDir.resolve("origin");
        Path clone = tempDir.resolve("clone");

        try (Git git = Git.init().setDirectory(origin.toFile()).call()) {
            Files.writeString(origin.resolve("README.md"), "# demo\n");
            Files.createDirectories(origin.resolve("src/main/java/demo"));
            Files.writeString(origin.resolve("src/main/java/demo/App.java"), "package demo;\nclass App {}\n");
            Files.createDirectories(origin.resolve("target"));
            Files.writeString(origin.resolve("target/build.log"), "should be filtered\n");

            PersonIdent who = new PersonIdent("CodeWisdom", "dev@codewisdom.local");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("init").setAuthor(who).setCommitter(who)
                    .setSign(false).call();
        }

        try (Git cloned = Git.cloneRepository()
                .setURI(origin.toUri().toString())
                .setDirectory(clone.toFile())
                .setDepth(1)
                .call()) {

            assertThat(cloned.getRepository().getBranch()).isNotBlank();

            RevCommit head = cloned.log().setMaxCount(1).call().iterator().next();
            assertThat(head.getShortMessage()).isEqualTo("init");
            assertThat(head.getAuthorIdent().getName()).isEqualTo("CodeWisdom");

            List<String> files = listFiles(clone);
            assertThat(files).contains("README.md", "src/main/java/demo/App.java");

            // 阶段 2 的文件树过滤输入确认：.git 与 target 都在物理工作区里，必须被规则排除
            assertThat(files).anyMatch(f -> f.startsWith(".git/"));
            assertThat(files).contains("target/build.log");
        }
    }

    // ---------- 真实网络层 ----------

    @BeforeAll
    static void requireNetwork() {
        assumeTrue(reachable("gitee.com", 443),
                "gitee.com 不可达，跳过网络冒烟（离线环境属预期）");
    }

    @Test
    @Tag("network")
    @DisplayName("网络：浅克隆 Gitee 公开仓库并读取 HEAD")
    void clonesPublicGiteeRepository() throws Exception {
        Path workDir = tempDir.resolve("gitee-repo");

        try (Git git = Git.cloneRepository()
                .setURI(GITEE_REPO)
                .setDirectory(workDir.toFile())
                .setDepth(1)
                .setCloneAllBranches(false)
                .call()) {

            Repository repository = git.getRepository();
            RevCommit head = git.log().setMaxCount(1).call().iterator().next();

            assertThat(repository.getBranch()).isNotBlank();
            assertThat(head.getName()).hasSize(40);

            List<String> files = listFiles(workDir);
            assertThat(files).isNotEmpty();
            assertThat(files).anyMatch(f -> f.endsWith("pom.xml"));
            assertThat(files).anyMatch(f -> f.endsWith(".java"));

            System.out.printf("[S4] 克隆成功 uri=%s branch=%s head=%s files=%d%n",
                    GITEE_REPO, repository.getBranch(), head.abbreviate(8).name(), files.size());
        }
    }

    @Test
    @Tag("network")
    @DisplayName("网络：工作区含 .git 目录——导入时须将其排除在文件树外")
    void workspaceExposesGitDirectory() throws Exception {
        Path workDir = tempDir.resolve("gitee-probe");

        try (Git git = Git.cloneRepository()
                .setURI(GITEE_REPO)
                .setDirectory(workDir.toFile())
                .setDepth(1)
                .call()) {

            Set<String> topLevel = listFiles(workDir).stream()
                    .map(name -> name.split("/")[0])
                    .collect(Collectors.toSet());

            assertThat(topLevel).as("阶段 2 的文件树必须过滤掉 .git").contains(".git");
            assertThat(git.getRepository().getDirectory()).exists();
        }
    }

    // ---------- helpers ----------

    private static boolean reachable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 3_000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** 列出工作区相对路径（含 .git 内部文件），作为过滤逻辑的输入。 */
    private static List<String> listFiles(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .map(p -> root.relativize(p).toString().replace('\\', '/'))
                    .toList();
        }
    }
}
