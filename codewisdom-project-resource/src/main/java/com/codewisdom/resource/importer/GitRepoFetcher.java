package com.codewisdom.resource.importer;

import com.codewisdom.common.api.ErrorCode;
import com.codewisdom.common.exception.BizException;
import com.codewisdom.resource.config.ImportProperties;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * 用 JGit 把远端仓库拉到本地工作区。
 *
 * <p>默认浅克隆（{@code depth=1}）：本平台只做「当前快照」的静态分析，不需要历史。
 * 实测 18MB / 700 文件的仓库浅克隆约 10 秒，完整克隆会显著更慢且更占磁盘。
 *
 * <p>工作区里的 {@code .git} 目录<b>保留</b>：文件树阶段会把它过滤掉，
 * 但后续的 Diff / 导出需要版本信息。
 */
@Component
public class GitRepoFetcher {

    private static final Logger log = LoggerFactory.getLogger(GitRepoFetcher.class);

    private final ImportProperties properties;

    public GitRepoFetcher(ImportProperties properties) {
        this.properties = properties;
    }

    /**
     * 拉取结果。
     *
     * @param branch     实际检出的分支名
     * @param headCommit HEAD 提交 id；空仓库时为 null
     * @param workspace  本地工作区目录
     */
    public record FetchResult(String branch, String headCommit, Path workspace) {
    }

    /**
     * 克隆仓库到指定目录。目录已存在时先清空，保证重新导入拿到的是干净快照。
     *
     * @param repoUri   已通过校验的仓库地址
     * @param branch    指定分支；为空则用远端默认分支
     * @param workspace 目标目录
     */
    public FetchResult fetch(URI repoUri, String branch, Path workspace) {
        try {
            if (Files.exists(workspace)) {
                deleteRecursively(workspace);
            }
            Files.createDirectories(workspace.getParent());

            var command = Git.cloneRepository()
                    .setURI(repoUri.toString())
                    .setDirectory(workspace.toFile())
                    .setTimeout(properties.getCloneTimeoutSeconds());

            int depth = properties.getCloneDepth();
            if (depth > 0) {
                command.setDepth(depth).setCloneAllBranches(false);
            }
            if (branch != null && !branch.isBlank()) {
                command.setBranch(branch.trim());
            }

            try (Git git = command.call()) {
                Repository repository = git.getRepository();
                String actualBranch = repository.getBranch();
                String headCommit = resolveHeadCommit(git);
                log.info("仓库拉取完成 uri={} branch={} head={} workspace={}",
                        sanitize(repoUri), actualBranch, headCommit, workspace);
                return new FetchResult(actualBranch, headCommit, workspace);
            }
        } catch (GitAPIException e) {
            throw BizException.of(ErrorCode.IMPORT_ERROR,
                    "仓库拉取失败: " + rootMessage(e), e);
        } catch (IOException e) {
            throw BizException.of(ErrorCode.IMPORT_ERROR,
                    "工作区准备失败: " + rootMessage(e), e);
        }
    }

    /** 空仓库没有 HEAD 提交，此处返回 null 而不是抛异常。 */
    private static String resolveHeadCommit(Git git) throws GitAPIException {
        var commits = git.log().setMaxCount(1).call();
        for (RevCommit commit : commits) {
            return commit.getName();
        }
        return null;
    }

    /**
     * 递归删除目录。
     *
     * <p><b>Windows 特有坑</b>：Git 写出的 pack 文件带只读属性，
     * {@link Files#deleteIfExists} 遇到只读文件会抛 {@code AccessDeniedException}。
     * 因此删除前必须逐个清掉只读位——否则「重新导入同一项目」在第二次必然失败，
     * 而第一次永远是好的，属于只在重复操作时才现形的问题。
     */
    private static void deleteRecursively(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                if (!Files.isSymbolicLink(path)) {
                    path.toFile().setWritable(true);
                }
                Files.deleteIfExists(path);
            }
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable current = e;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    /** 输出到日志前去掉 userinfo，避免凭据泄漏。 */
    private static String sanitize(URI uri) {
        return uri.getScheme() + "://" + uri.getHost() + uri.getPath();
    }
}
