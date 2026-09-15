package com.codewisdom.resource.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codewisdom.common.api.ErrorCode;
import com.codewisdom.common.exception.BizException;
import com.codewisdom.resource.config.ImportProperties;
import com.codewisdom.resource.domain.enums.FileNodeType;
import com.codewisdom.resource.domain.enums.ImportTaskStatus;
import com.codewisdom.resource.domain.enums.ProjectStatus;
import com.codewisdom.resource.domain.enums.SourceType;
import com.codewisdom.resource.dto.GitImportRequest;
import com.codewisdom.resource.dto.ImportResult;
import com.codewisdom.resource.entity.FileNode;
import com.codewisdom.resource.entity.ImportTask;
import com.codewisdom.resource.entity.Project;
import com.codewisdom.resource.importer.FileTreeScanner;
import com.codewisdom.resource.importer.GitRepoFetcher;
import com.codewisdom.resource.mapper.FileNodeMapper;
import com.codewisdom.resource.mapper.ImportTaskMapper;
import com.codewisdom.resource.mapper.ProjectMapper;
import com.codewisdom.resource.security.RepoUrlValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 项目导入编排。
 *
 * <p><b>事务边界是刻意拆开的</b>：克隆可能耗时数十秒，不能把它圈进数据库事务里——
 * 那会长时间占用连接，且失败时回滚无意义（磁盘上的文件不会自己消失）。
 * 因此切成三段：
 * <ol>
 *   <li>短事务：落 Project(IMPORTING) + ImportTask(RUNNING)，让前端能立刻看到任务；</li>
 *   <li>无事务：克隆 + 扫描；</li>
 *   <li>短事务：清旧节点、写文件树、更新终态。</li>
 * </ol>
 * 任一步失败都会把 Project/ImportTask 置为 FAILED，并记录错误码，不留「永远 RUNNING」的僵尸任务。
 */
@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    private final RepoUrlValidator urlValidator;
    private final GitRepoFetcher fetcher;
    private final FileTreeScanner scanner;
    private final ProjectMapper projectMapper;
    private final ImportTaskMapper importTaskMapper;
    private final FileNodeMapper fileNodeMapper;
    private final TransactionTemplate transactionTemplate;
    private final ImportProperties properties;

    public ImportService(RepoUrlValidator urlValidator,
                         GitRepoFetcher fetcher,
                         FileTreeScanner scanner,
                         ProjectMapper projectMapper,
                         ImportTaskMapper importTaskMapper,
                         FileNodeMapper fileNodeMapper,
                         TransactionTemplate transactionTemplate,
                         ImportProperties properties) {
        this.urlValidator = urlValidator;
        this.fetcher = fetcher;
        this.scanner = scanner;
        this.projectMapper = projectMapper;
        this.importTaskMapper = importTaskMapper;
        this.fileNodeMapper = fileNodeMapper;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
    }

    /**
     * 从 Git 公开仓库导入项目。
     *
     * @throws BizException 地址非法、拉取失败或文件数超限
     */
    public ImportResult importFromGit(GitImportRequest request) {
        URI repoUri = urlValidator.validate(request.url());

        Project project = createProject(repoUri, request);
        ImportTask task = createTask(project.getId());
        log.info("开始导入 projectId={} taskId={} url={}", project.getId(), task.getId(), repoUri.getHost());

        Path workspace = Paths.get(properties.getWorkspaceRoot()).resolve(String.valueOf(project.getId()));

        try {
            markTaskRunning(task);
            GitRepoFetcher.FetchResult fetched = fetcher.fetch(repoUri, request.branch(), workspace);
            FileTreeScanner.ScanResult scanned = scanner.scan(fetched.workspace());

            transactionTemplate.executeWithoutResult(status ->
                    persistSuccess(project, task, fetched, scanned));

            return new ImportResult(project.getId(), task.getId(), ImportTaskStatus.SUCCESS,
                    fetched.branch(), fetched.headCommit(),
                    scanned.fileCount(), scanned.totalSize(), "导入成功");
        } catch (BizException e) {
            return fail(project, task, e.getErrorCode(), e.getMessage());
        } catch (IOException e) {
            return fail(project, task, ErrorCode.IMPORT_ERROR, "读取工作区失败: " + e.getMessage());
        } catch (RuntimeException e) {
            log.error("导入发生未预期异常 projectId={}", project.getId(), e);
            return fail(project, task, ErrorCode.SYSTEM_ERROR, "导入失败: " + e.getMessage());
        }
    }

    // ---- 阶段 1：建项目与任务 ----

    private Project createProject(URI repoUri, GitImportRequest request) {
        return transactionTemplate.execute(status -> {
            Project project = new Project();
            project.setName(resolveName(request.name(), repoUri));
            project.setSourceType(SourceType.GIT);
            project.setSourceUrl(repoUri.toString());
            project.setDefaultBranch(blankToNull(request.branch()));
            project.setStatus(ProjectStatus.IMPORTING);
            project.setStorageBucket("cw-source");
            // 用 UUID 而非自增 id 拼前缀：id 要等插入后才知道，而 storage_prefix 是 NOT NULL，
            // 用 id 就必须「先插后改」两次写。UUID 还能避免对象存储键暴露项目数量。
            project.setStoragePrefix("projects/" + UUID.randomUUID() + "/");
            project.setFileCount(0);
            project.setTotalSize(0L);
            projectMapper.insert(project);
            return project;
        });
    }

    private ImportTask createTask(Long projectId) {
        return transactionTemplate.execute(status -> {
            ImportTask task = new ImportTask();
            task.setProjectId(projectId);
            task.setStatus(ImportTaskStatus.PENDING);
            task.setProgress(0);
            task.setMessage("已入队");
            importTaskMapper.insert(task);
            return task;
        });
    }

    private void markTaskRunning(ImportTask task) {
        transactionTemplate.executeWithoutResult(status -> {
            task.setStatus(ImportTaskStatus.RUNNING);
            task.setProgress(10);
            task.setMessage("正在拉取仓库");
            task.setStartedAt(now());
            importTaskMapper.updateById(task);
        });
    }

    // ---- 阶段 3：落库并置终态 ----

    private void persistSuccess(Project project,
                                ImportTask task,
                                GitRepoFetcher.FetchResult fetched,
                                FileTreeScanner.ScanResult scanned) {
        // 重新导入时先清空旧文件树，避免新旧节点混在一起
        fileNodeMapper.delete(new LambdaQueryWrapper<FileNode>()
                .eq(FileNode::getProjectId, project.getId()));

        persistTree(project.getId(), scanned.roots());

        project.setStatus(ProjectStatus.READY);
        project.setDefaultBranch(fetched.branch());
        project.setFileCount(scanned.fileCount());
        project.setTotalSize(scanned.totalSize());
        projectMapper.updateById(project);

        task.setStatus(ImportTaskStatus.SUCCESS);
        task.setProgress(100);
        task.setMessage("导入成功，共 " + scanned.fileCount() + " 个文件");
        task.setFinishedAt(now());
        importTaskMapper.updateById(task);
    }

    /**
     * 广度优先写入文件树。
     *
     * <p>必须 BFS：自增主键要等父节点插入后才拿得到，只有逐层写入才能正确填 {@code parent_id}。
     * 用「路径 → 已生成 id」的映射把父子关系串起来。
     */
    private void persistTree(Long projectId, List<FileTreeScanner.ScannedNode> roots) {
        Map<String, Long> pathToId = new HashMap<>();
        Deque<FileTreeScanner.ScannedNode> queue = new ArrayDeque<>(roots);

        while (!queue.isEmpty()) {
            FileTreeScanner.ScannedNode node = queue.poll();

            FileNode entity = new FileNode();
            entity.setProjectId(projectId);
            entity.setParentId(pathToId.get(parentPathOf(node.path())));
            entity.setPath(node.path());
            entity.setName(node.name());
            entity.setNodeType(node.type());
            entity.setLanguage(node.language());
            entity.setCategory(node.category());
            entity.setSize(node.size());
            entity.setChecksum(node.checksum());

            fileNodeMapper.insert(entity);
            pathToId.put(node.path(), entity.getId());

            queue.addAll(node.children());
        }
        log.debug("文件树落库完成 projectId={} nodes={}", projectId, pathToId.size());
    }

    private ImportResult fail(Project project, ImportTask task, ErrorCode errorCode, String message) {
        log.warn("导入失败 projectId={} code={} message={}", project.getId(), errorCode.code(), message);
        transactionTemplate.executeWithoutResult(status -> {
            project.setStatus(ProjectStatus.FAILED);
            projectMapper.updateById(project);

            task.setStatus(ImportTaskStatus.FAILED);
            task.setErrorCode(errorCode.name());
            task.setMessage(message);
            task.setFinishedAt(now());
            importTaskMapper.updateById(task);
        });
        return new ImportResult(project.getId(), task.getId(), ImportTaskStatus.FAILED,
                null, null, 0, 0L, message);
    }

    // ---- 工具 ----

    /** {@code a/b/c.java} → {@code a/b}；根节点返回 null。 */
    static String parentPathOf(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? null : path.substring(0, slash);
    }

    /** 从仓库地址推导项目名：{@code https://gitee.com/u/RuoYi.git} → {@code RuoYi}。 */
    static String resolveName(String requestedName, URI repoUri) {
        if (requestedName != null && !requestedName.isBlank()) {
            return requestedName.trim();
        }
        String path = repoUri.getPath() == null ? "" : repoUri.getPath();
        String last = path.substring(path.lastIndexOf('/') + 1);
        if (last.endsWith(".git")) {
            last = last.substring(0, last.length() - 4);
        }
        return last.isBlank() ? repoUri.getHost() : last;
    }

    private static LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
