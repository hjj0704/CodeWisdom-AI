package com.codewisdom.resource.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codewisdom.resource.config.ImportProperties;
import com.codewisdom.resource.domain.enums.ImportTaskStatus;
import com.codewisdom.resource.domain.enums.ProjectStatus;
import com.codewisdom.resource.domain.enums.SourceType;
import com.codewisdom.resource.entity.FileNode;
import com.codewisdom.resource.entity.ImportTask;
import com.codewisdom.resource.entity.Project;
import com.codewisdom.resource.mapper.FileNodeMapper;
import com.codewisdom.resource.mapper.ImportTaskMapper;
import com.codewisdom.resource.mapper.ProjectMapper;
import com.codewisdom.resource.security.RepoUrlValidator;
import com.codewisdom.resource.testsupport.TestWorkspaces;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T-202 端到端验收：从 HTTP 请求到文件树落库的完整链路。
 *
 * <p>用<b>本地 Git 仓库</b>（{@code file://}）作为导入源，因此完全离线、可重复。
 * 校验器被替换为放行版——生产环境的 http(s) 与白名单校验由
 * {@code RepoUrlValidatorTest} 单独覆盖，不在这里重复，也不在这里放水。
 *
 * <p>网络可达时的真实远端导入由 {@code @Tag("network")} 的用例补充。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(GitImportIntegrationTest.LocalRepoConfig.class)
@DisplayName("T-202 Git 导入端到端")
class GitImportIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private ImportTaskMapper importTaskMapper;

    @Autowired
    private FileNodeMapper fileNodeMapper;

    @TempDir
    Path originRoot;

    private Path localRepo;

    @AfterAll
    static void cleanWorkspaces() {
        TestWorkspaces.forceDelete(Path.of(System.getProperty("java.io.tmpdir"), "codewisdom-test-repos"));
    }

    @BeforeEach
    void setUpLocalRepository() throws Exception {
        localRepo = originRoot.resolve("demo-repo");
        Files.createDirectories(localRepo);

        write("pom.xml", "<project><artifactId>demo</artifactId></project>");
        write("README.md", "# Demo 项目");
        write("src/main/java/demo/App.java", "package demo;\npublic class App {}\n");
        write("src/main/java/demo/Service.java", "package demo;\npublic class Service {}\n");
        write("src/main/resources/application.yml", "server:\n  port: 9090\n");
        // 以下都必须在导入时被剪掉
        write("target/classes/demo/App.class", "bytecode");
        write("node_modules/lodash/index.js", "module.exports = {};");
        write("app.log", "noise");

        try (Git git = Git.init().setDirectory(localRepo.toFile()).call()) {
            PersonIdent who = new PersonIdent("CodeWisdom", "dev@codewisdom.local");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("init: demo 项目").setAuthor(who).setCommitter(who).setSign(false).call();
        }
    }

    @Test
    @DisplayName("导入成功：项目 READY、任务 SUCCESS、文件树落库且产物被剪掉")
    void importsLocalRepositoryEndToEnd() throws Exception {
        String url = localRepo.toUri().toString();

        mockMvc.perform(post("/import/git")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"" + url + "\",\"name\":\"演示项目\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.projectId").isNumber())
                // 5 个保留文件：pom.xml / README.md / App.java / Service.java / application.yml
                .andExpect(jsonPath("$.data.fileCount").value(5));

        Long projectId = projectMapper.selectList(new LambdaQueryWrapper<Project>()
                        .eq(Project::getName, "演示项目"))
                .stream().findFirst().orElseThrow().getId();

        Project project = projectMapper.selectById(projectId);
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.READY);
        assertThat(project.getSourceType()).isEqualTo(SourceType.GIT);
        assertThat(project.getStoragePrefix())
                .as("对象存储前缀用 UUID，避免依赖自增 id 造成两次写入")
                .startsWith("projects/")
                .endsWith("/")
                .hasSize("projects/".length() + 36 + 1);
        assertThat(project.getFileCount()).isEqualTo(5);
        assertThat(project.getTotalSize()).isPositive();
        assertThat(project.getDefaultBranch()).isNotBlank();

        ImportTask task = importTaskMapper.selectList(new LambdaQueryWrapper<ImportTask>()
                        .eq(ImportTask::getProjectId, projectId))
                .stream().findFirst().orElseThrow();
        assertThat(task.getStatus()).isEqualTo(ImportTaskStatus.SUCCESS);
        assertThat(task.getProgress()).isEqualTo(100);
        assertThat(task.getStartedAt()).isNotNull();
        assertThat(task.getFinishedAt()).isNotNull();
        assertThat(task.getErrorCode()).isNull();

        List<String> paths = fileNodeMapper.selectList(new LambdaQueryWrapper<FileNode>()
                        .eq(FileNode::getProjectId, projectId))
                .stream().map(FileNode::getPath).toList();

        assertThat(paths).contains(
                "pom.xml", "README.md",
                "src/main/java/demo/App.java",
                "src/main/java/demo/Service.java",
                "src/main/resources/application.yml");

        assertThat(paths).as(".git 目录必须被剪掉")
                .noneMatch(p -> p.equals(".git") || p.startsWith(".git/"));
        assertThat(paths).as("构建产物必须被剪掉").noneMatch(p -> p.startsWith("target/"));
        assertThat(paths).as("依赖目录必须被剪掉").noneMatch(p -> p.startsWith("node_modules/"));
        assertThat(paths).as("二进制与日志必须被剪掉")
                .noneMatch(p -> p.endsWith(".class") || p.endsWith(".log"));
    }

    @Test
    @DisplayName("父子关系正确：子节点的 parent_id 指向父目录")
    void buildsCorrectParentChildLinks() throws Exception {
        String url = localRepo.toUri().toString();

        mockMvc.perform(post("/import/git")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"" + url + "\",\"name\":\"父子关系校验\"}"))
                .andExpect(status().isOk());

        Long projectId = projectMapper.selectList(new LambdaQueryWrapper<Project>()
                        .eq(Project::getName, "父子关系校验"))
                .stream().findFirst().orElseThrow().getId();

        FileNode appJava = nodeByPath(projectId, "src/main/java/demo/App.java");
        FileNode demoDir = nodeByPath(projectId, "src/main/java/demo");

        assertThat(appJava.getParentId()).isEqualTo(demoDir.getId());
        assertThat(demoDir.getParentId())
                .isEqualTo(nodeByPath(projectId, "src/main/java").getId());

        // 根节点没有父
        assertThat(nodeByPath(projectId, "src").getParentId()).isNull();
        assertThat(nodeByPath(projectId, "pom.xml").getParentId()).isNull();

        // 语言与分类落到库
        assertThat(appJava.getLanguage()).isEqualTo("java");
        assertThat(nodeByPath(projectId, "pom.xml").getCategory().name()).isEqualTo("CONFIG");
    }

    @Test
    @DisplayName("重新导入同一项目时旧文件树被清空，不产生重复节点")
    void reimportReplacesPreviousTree() throws Exception {
        String url = localRepo.toUri().toString();
        String body = "{\"url\":\"" + url + "\",\"name\":\"重复导入\"}";

        mockMvc.perform(post("/import/git").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/import/git").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        // 两次导入产生两个项目，各自持有自己的文件树
        List<Project> projects = projectMapper.selectList(new LambdaQueryWrapper<Project>()
                .eq(Project::getName, "重复导入"));
        assertThat(projects).hasSize(2);

        for (Project p : projects) {
            List<FileNode> nodes = fileNodeMapper.selectList(new LambdaQueryWrapper<FileNode>()
                    .eq(FileNode::getProjectId, p.getId()));
            assertThat(nodes).as("项目 %s 不应有重复路径", p.getId())
                    .extracting(FileNode::getPath)
                    .doesNotHaveDuplicates();
            assertThat(nodes).hasSize(p.getFileCount() + 5); // 5 个文件 + src/main/java/demo 等 5 个目录
        }
    }

    @Test
    @DisplayName("非法地址被拒绝，且不留下任何项目记录")
    void rejectsInvalidUrlWithoutSideEffects() throws Exception {
        long before = projectMapper.selectCount(null);

        mockMvc.perform(post("/import/git")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"not-a-url\",\"name\":\"应被拒绝\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(51001));

        assertThat(projectMapper.selectCount(null))
                .as("地址校验失败时不应创建项目记录")
                .isEqualTo(before);
    }

    // ---- helpers ----

    private FileNode nodeByPath(Long projectId, String path) {
        List<FileNode> nodes = fileNodeMapper.selectList(new LambdaQueryWrapper<FileNode>()
                .eq(FileNode::getProjectId, projectId)
                .eq(FileNode::getPath, path));
        assertThat(nodes).as("应存在唯一节点 %s", path).hasSize(1);
        return nodes.get(0);
    }

    private void write(String relativePath, String content) throws Exception {
        Path target = localRepo.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }

    /** 用放行版校验器替换生产校验器：测试源是本地 {@code file://} 仓库，不走 http 校验链。 */
    @TestConfiguration
    static class LocalRepoConfig {

        @Bean
        @Primary
        RepoUrlValidator permissiveRepoUrlValidator(ImportProperties properties) {
            return new RepoUrlValidator(properties) {
                @Override
                public URI validate(String rawUrl) {
                    if (rawUrl == null || rawUrl.isBlank() || !rawUrl.contains("://")) {
                        return super.validate(rawUrl);
                    }
                    return URI.create(rawUrl.trim());
                }
            };
        }
    }
}
