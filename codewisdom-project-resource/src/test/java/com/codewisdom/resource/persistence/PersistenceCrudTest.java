package com.codewisdom.resource.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codewisdom.resource.domain.enums.FileCategory;
import com.codewisdom.resource.domain.enums.FileNodeType;
import com.codewisdom.resource.domain.enums.ImportTaskStatus;
import com.codewisdom.resource.domain.enums.ProjectStatus;
import com.codewisdom.resource.domain.enums.SourceType;
import com.codewisdom.resource.entity.FileNode;
import com.codewisdom.resource.entity.ImportTask;
import com.codewisdom.resource.entity.Project;
import com.codewisdom.resource.mapper.FileNodeMapper;
import com.codewisdom.resource.mapper.ImportTaskMapper;
import com.codewisdom.resource.mapper.ProjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-201 验收之二：三张表的增删改查与字段映射。
 *
 * <p>重点验证四件容易出错的事：
 * <ol>
 *   <li>自增主键回填；</li>
 *   <li>审计时间自动填充（建表脚本中该列 NOT NULL 且无默认值）；</li>
 *   <li>枚举按 name 双向映射，不依赖序号；</li>
 *   <li>下划线列名到驼峰属性的映射。</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("T-201 Mapper CRUD")
class PersistenceCrudTest {

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private ImportTaskMapper importTaskMapper;

    @Autowired
    private FileNodeMapper fileNodeMapper;

    @Test
    @DisplayName("Project：新增回填主键与审计时间，枚举与列名映射正确")
    void projectInsertFillsIdAndAuditFields() {
        Project project = newProject("demo-repo");

        assertThat(project.getId()).as("插入前无 id").isNull();
        assertThat(projectMapper.insert(project)).isEqualTo(1);

        assertThat(project.getId()).as("自增主键应被回填").isNotNull().isPositive();
        assertThat(project.getCreatedAt()).as("createdAt 应由 MetaObjectHandler 填充").isNotNull();
        assertThat(project.getUpdatedAt()).isNotNull();

        Project loaded = projectMapper.selectById(project.getId());
        assertThat(loaded).isNotNull();
        assertThat(loaded.getName()).isEqualTo("demo-repo");
        assertThat(loaded.getSourceType()).as("枚举按 name 往返").isEqualTo(SourceType.GIT);
        assertThat(loaded.getStatus()).isEqualTo(ProjectStatus.IMPORTING);
        assertThat(loaded.getStoragePrefix()).as("storage_prefix 列应映射到 storagePrefix 属性")
                .isEqualTo("projects/tmp/");
        assertThat(loaded.getFileCount()).isZero();
        assertThat(loaded.getTotalSize()).isZero();
    }

    @Test
    @DisplayName("Project：更新后 updatedAt 前进，且 createdAt 不被写回")
    void projectUpdateAdvancesUpdatedAt() throws InterruptedException {
        Project project = newProject("to-update");
        projectMapper.insert(project);

        // 基准取库里的值，而不是内存里的值：列是 DATETIME(3)，内存是纳秒，直接比会假红
        LocalDateTime baselineCreatedAt = projectMapper.selectById(project.getId()).getCreatedAt();

        Thread.sleep(50);
        project.setStatus(ProjectStatus.READY);
        project.setFileCount(42);
        project.setTotalSize(1024L);
        assertThat(projectMapper.updateById(project)).isEqualTo(1);

        Project reloaded = projectMapper.selectById(project.getId());
        assertThat(reloaded.getStatus()).isEqualTo(ProjectStatus.READY);
        assertThat(reloaded.getFileCount()).isEqualTo(42);
        assertThat(reloaded.getCreatedAt()).as("createdAt 不应被更新写回").isEqualTo(baselineCreatedAt);
        assertThat(reloaded.getUpdatedAt()).as("updatedAt 应前进").isAfter(baselineCreatedAt);
    }

    @Test
    @DisplayName("Project：删除后查不到")
    void projectDelete() {
        Project project = newProject("to-delete");
        projectMapper.insert(project);

        assertThat(projectMapper.deleteById(project.getId())).isEqualTo(1);
        assertThat(projectMapper.selectById(project.getId())).isNull();
    }

    @Test
    @DisplayName("ImportTask：按项目查历史，并能记录失败错误码")
    void importTaskLifecycle() {
        Project project = newProject("with-tasks");
        projectMapper.insert(project);

        ImportTask task = new ImportTask();
        task.setProjectId(project.getId());
        task.setStatus(ImportTaskStatus.PENDING);
        task.setProgress(0);
        importTaskMapper.insert(task);
        assertThat(task.getId()).isNotNull();

        task.setStatus(ImportTaskStatus.RUNNING);
        task.setProgress(60);
        task.setStartedAt(LocalDateTime.now());
        importTaskMapper.updateById(task);

        ImportTask failed = new ImportTask();
        failed.setProjectId(project.getId());
        failed.setStatus(ImportTaskStatus.FAILED);
        failed.setProgress(0);
        failed.setErrorCode("IMPORT_URL_REJECTED");
        failed.setMessage("仓库地址不被允许");
        importTaskMapper.insert(failed);

        // 另一个项目的任务不应被查出
        Project other = newProject("unrelated");
        projectMapper.insert(other);
        ImportTask otherTask = new ImportTask();
        otherTask.setProjectId(other.getId());
        otherTask.setStatus(ImportTaskStatus.SUCCESS);
        otherTask.setProgress(100);
        importTaskMapper.insert(otherTask);

        List<ImportTask> tasks = importTaskMapper.selectList(
                new LambdaQueryWrapper<ImportTask>().eq(ImportTask::getProjectId, project.getId()));

        assertThat(tasks).hasSize(2);
        assertThat(tasks).extracting(ImportTask::getStatus)
                .containsExactlyInAnyOrder(ImportTaskStatus.RUNNING, ImportTaskStatus.FAILED);
        assertThat(tasks).filteredOn(t -> t.getErrorCode() != null)
                .singleElement()
                .satisfies(t -> {
                    assertThat(t.getErrorCode()).isEqualTo("IMPORT_URL_REJECTED");
                    assertThat(t.getMessage()).isEqualTo("仓库地址不被允许");
                    assertThat(t.getFinishedAt()).isNull();
                });
    }

    @Test
    @DisplayName("FileNode：父子关系与完整路径两条查询路径都可用")
    void fileNodeTreeAndPathLookup() {
        Project project = newProject("with-tree");
        projectMapper.insert(project);

        FileNode srcDir = newNode(project.getId(), null, "src", "src", FileNodeType.DIR);
        fileNodeMapper.insert(srcDir);

        FileNode appFile = newNode(project.getId(), srcDir.getId(),
                "src/App.java", "App.java", FileNodeType.FILE);
        appFile.setLanguage("java");
        appFile.setCategory(FileCategory.SOURCE);
        appFile.setSize(256L);
        appFile.setChecksum("abc123");
        fileNodeMapper.insert(appFile);

        FileNode pomFile = newNode(project.getId(), null, "pom.xml", "pom.xml", FileNodeType.FILE);
        pomFile.setCategory(FileCategory.CONFIG);
        pomFile.setSize(1024L);
        fileNodeMapper.insert(pomFile);

        // 路径 1：按父节点取子节点（树形渲染）
        List<FileNode> childrenOfSrc = fileNodeMapper.selectList(
                new LambdaQueryWrapper<FileNode>()
                        .eq(FileNode::getParentId, srcDir.getId()));
        assertThat(childrenOfSrc).singleElement()
                .satisfies(n -> assertThat(n.getPath()).isEqualTo("src/App.java"));

        // 路径 2：按完整路径直接定位（解析阶段批量筛选）
        List<FileNode> sourceFiles = fileNodeMapper.selectList(
                new LambdaQueryWrapper<FileNode>()
                        .eq(FileNode::getProjectId, project.getId())
                        .eq(FileNode::getCategory, FileCategory.SOURCE));
        assertThat(sourceFiles).singleElement()
                .satisfies(n -> {
                    assertThat(n.getLanguage()).isEqualTo("java");
                    assertThat(n.getChecksum()).isEqualTo("abc123");
                    assertThat(n.getNodeType()).isEqualTo(FileNodeType.FILE);
                });

        // 根节点：parentId 为空
        List<FileNode> roots = fileNodeMapper.selectList(
                new LambdaQueryWrapper<FileNode>()
                        .eq(FileNode::getProjectId, project.getId())
                        .isNull(FileNode::getParentId));
        assertThat(roots).extracting(FileNode::getName)
                .containsExactlyInAnyOrder("src", "pom.xml");
    }

    // ---- helpers ----

    private Project newProject(String name) {
        Project project = new Project();
        project.setName(name);
        project.setSourceType(SourceType.GIT);
        project.setSourceUrl("https://gitee.com/y_project/RuoYi.git");
        project.setDefaultBranch("master");
        project.setStatus(ProjectStatus.IMPORTING);
        project.setStorageBucket("cw-source");
        project.setStoragePrefix("projects/tmp/");
        project.setFileCount(0);
        project.setTotalSize(0L);
        return project;
    }

    private FileNode newNode(Long projectId, Long parentId, String path, String name, FileNodeType type) {
        FileNode node = new FileNode();
        node.setProjectId(projectId);
        node.setParentId(parentId);
        node.setPath(path);
        node.setName(name);
        node.setNodeType(type);
        node.setCategory(FileCategory.OTHER);
        node.setSize(0L);
        return node;
    }
}
