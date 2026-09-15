package com.codewisdom.resource.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codewisdom.resource.domain.enums.ImportTaskStatus;
import com.codewisdom.resource.domain.enums.ProjectStatus;
import com.codewisdom.resource.domain.enums.SourceType;
import com.codewisdom.resource.entity.FileNode;
import com.codewisdom.resource.entity.ImportTask;
import com.codewisdom.resource.entity.Project;
import com.codewisdom.resource.mapper.FileNodeMapper;
import com.codewisdom.resource.mapper.ImportTaskMapper;
import com.codewisdom.resource.mapper.ProjectMapper;
import com.codewisdom.resource.testsupport.TestWorkspaces;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T-203 端到端验收：ZIP 上传 → 安全解压 → 剪枝建树 → 落库。
 *
 * <p>同时覆盖「恶意包被拒绝且不留下任何痕迹」——安全类功能只在成功路径上测试是不够的。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("T-203 ZIP 导入端到端")
class ZipImportIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private ImportTaskMapper importTaskMapper;

    @Autowired
    private FileNodeMapper fileNodeMapper;

    @TempDir
    Path tempDir;

    @AfterAll
    static void cleanWorkspaces() {
        TestWorkspaces.forceDelete(Path.of(System.getProperty("java.io.tmpdir"), "codewisdom-test-repos"));
    }

    @Test
    @DisplayName("上传 ZIP 导入成功：文件树落库，构建产物与依赖目录被剪掉")
    void importsZipEndToEnd() throws Exception {
        byte[] zip = zipOf(
                new String[]{"pom.xml", "<project/>"},
                new String[]{"README.md", "# 压缩包项目"},
                new String[]{"src/main/java/demo/App.java", "package demo; class App {}"},
                new String[]{"src/main/resources/application.yml", "server:\n  port: 8080\n"},
                // 以下都应被剪掉
                new String[]{"target/classes/demo/App.class", "bytecode"},
                new String[]{"node_modules/lodash/index.js", "module.exports={};"},
                new String[]{".git/config", "[core]"},
                new String[]{"build.log", "noise"});

        mockMvc.perform(multipart("/import/zip")
                        .file(new MockMultipartFile("file", "demo.zip", "application/zip", zip))
                        .param("name", "压缩包导入项目"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.fileCount").value(4));

        Project project = findProject("压缩包导入项目");
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.READY);
        assertThat(project.getSourceType()).isEqualTo(SourceType.ZIP);
        assertThat(project.getSourceUrl()).as("ZIP 导入没有仓库地址").isNull();
        assertThat(project.getFileCount()).isEqualTo(4);

        ImportTask task = importTaskMapper.selectList(new LambdaQueryWrapper<ImportTask>()
                        .eq(ImportTask::getProjectId, project.getId()))
                .stream().findFirst().orElseThrow();
        assertThat(task.getStatus()).isEqualTo(ImportTaskStatus.SUCCESS);

        List<String> paths = fileNodeMapper.selectList(new LambdaQueryWrapper<FileNode>()
                        .eq(FileNode::getProjectId, project.getId()))
                .stream().map(FileNode::getPath).toList();

        assertThat(paths).contains("pom.xml", "README.md", "src/main/java/demo/App.java");
        assertThat(paths).as("构建产物必须被剪掉").noneMatch(p -> p.startsWith("target/"));
        assertThat(paths).as("依赖目录必须被剪掉").noneMatch(p -> p.startsWith("node_modules/"));
        assertThat(paths).as(".git 目录必须被剪掉")
                .noneMatch(p -> p.equals(".git") || p.startsWith(".git/"));
        assertThat(paths).as("日志必须被剪掉").noneMatch(p -> p.endsWith(".log"));
    }

    @Test
    @DisplayName("未指定项目名时用压缩包文件名")
    void derivesProjectNameFromArchiveName() throws Exception {
        byte[] zip = zipOf(new String[]{"a.txt", "hello"});

        mockMvc.perform(multipart("/import/zip")
                        .file(new MockMultipartFile("file", "my-service.zip", "application/zip", zip)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        assertThat(projectMapper.selectList(new LambdaQueryWrapper<Project>()
                .eq(Project::getName, "my-service"))).hasSize(1);
    }

    @Test
    @DisplayName("Zip Slip 恶意包被拒绝，且不创建项目、不产生逃逸文件")
    void rejectsZipSlipArchiveWithoutSideEffects() throws Exception {
        long projectsBefore = projectMapper.selectCount(null);
        byte[] evil = zipOf(new String[]{"../escaped-from-zip.txt", "pwned"});

        mockMvc.perform(multipart("/import/zip")
                        .file(new MockMultipartFile("file", "evil.zip", "application/zip", evil))
                        .param("name", "恶意压缩包"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(51003));

        // 校验发生在建项目之前，因此不应留下 FAILED 项目——恶意输入不该污染业务数据
        assertThat(projectMapper.selectCount(null))
                .as("路径穿越包不应创建项目记录")
                .isEqualTo(projectsBefore);

        Path escaped = Path.of(System.getProperty("java.io.tmpdir")).resolve("escaped-from-zip.txt");
        assertThat(escaped).doesNotExist();
    }

    @Test
    @DisplayName("非 ZIP 文件被拒绝")
    void rejectsNonZipUpload() throws Exception {
        mockMvc.perform(multipart("/import/zip")
                        .file(new MockMultipartFile("file", "notes.txt", "text/plain",
                                "not a zip".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(51002));
    }

    @Test
    @DisplayName("空文件被拒绝")
    void rejectsEmptyUpload() throws Exception {
        mockMvc.perform(multipart("/import/zip")
                        .file(new MockMultipartFile("file", "empty.zip", "application/zip", new byte[0])))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(51002));
    }

    // ---- helpers ----

    private Project findProject(String name) {
        return projectMapper.selectList(new LambdaQueryWrapper<Project>()
                        .eq(Project::getName, name))
                .stream().findFirst().orElseThrow();
    }

    private byte[] zipOf(String[]... entries) throws Exception {
        Path zip = tempDir.resolve("upload-" + System.nanoTime() + ".zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (String[] entry : entries) {
                out.putNextEntry(new ZipEntry(entry[0]));
                out.write(entry[1].getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return Files.readAllBytes(zip);
    }
}
