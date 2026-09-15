package com.codewisdom.resource.controller;

import com.codewisdom.resource.domain.enums.FileCategory;
import com.codewisdom.resource.entity.Project;
import com.codewisdom.resource.mapper.ProjectMapper;
import com.codewisdom.resource.testsupport.TestWorkspaces;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T-204 验收：文件树查询接口与分类统计。
 *
 * <p>核心断言是「<b>节点数与磁盘一致</b>」——树里的节点必须能一一对应到压缩包里的真实条目，
 * 不多不少。用真实导入链路造数据，而不是手工插库，这样连剪枝逻辑一起验了。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("T-204 文件树与统计")
class FileTreeEndpointTest {

    /** 压缩包内容：6 个文件 + 6 个目录 = 12 个节点。 */
    private static final String[][] ARCHIVE = {
            {"pom.xml", "<project/>"},
            {"README.md", "# 示例项目"},
            {"src/main/java/demo/App.java", "package demo; class App {}"},
            {"src/main/java/demo/Service.java", "package demo; class Service {}"},
            {"src/main/resources/application.yml", "server:\n  port: 8080\n"},
            {"docs/guide.md", "# 指南"},
            // 以下应被剪掉，不计入节点数
            {"target/classes/demo/App.class", "bytecode"},
            {"node_modules/lodash/index.js", "module.exports={};"},
            {"app.log", "noise"}
    };

    private static final int EXPECTED_DIRECTORIES = 6;
    private static final int EXPECTED_FILES = 6;
    private static final int EXPECTED_TOTAL_NODES = EXPECTED_DIRECTORIES + EXPECTED_FILES;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @TempDir
    Path tempDir;

    private Long projectId;

    @AfterAll
    static void cleanWorkspaces() {
        TestWorkspaces.forceDelete(Path.of(System.getProperty("java.io.tmpdir"), "codewisdom-test-repos"));
    }

    @BeforeEach
    void importSampleProject() throws Exception {
        String body = mockMvc.perform(multipart("/import/zip")
                        .file(new MockMultipartFile("file", "sample.zip", "application/zip", zipOf()))
                        .param("name", "文件树测试项目"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        projectId = objectMapper.readTree(body).path("data").path("projectId").asLong();
        assertThat(projectId).as("导入应返回项目 id").isPositive();
    }

    @Test
    @DisplayName("整树查询：节点数与磁盘一致，剪掉的条目不出现在树里")
    void returnsFullTreeMatchingDisk() throws Exception {
        JsonNode root = getJson("/projects/" + projectId() + "/tree");

        assertThat(root.path("code").asInt()).isZero();
        JsonNode tree = root.path("data");

        assertThat(tree.path("id").isNull()).as("虚拟根没有数据库 id").isTrue();
        assertThat(tree.path("name").asText()).isEqualTo("文件树测试项目");
        assertThat(tree.path("path").asText()).isEmpty();
        assertThat(tree.path("type").asText()).isEqualTo("DIR");

        List<String> allPaths = flattenPaths(tree);
        assertThat(allPaths).as("节点总数必须与磁盘一致")
                .hasSize(EXPECTED_TOTAL_NODES);

        assertThat(allPaths).contains(
                "pom.xml", "README.md", "docs", "docs/guide.md",
                "src", "src/main", "src/main/java", "src/main/java/demo",
                "src/main/java/demo/App.java", "src/main/java/demo/Service.java",
                "src/main/resources", "src/main/resources/application.yml");

        assertThat(allPaths).as("产物与依赖目录不得出现在树里")
                .noneMatch(p -> p.startsWith("target/") || p.startsWith("node_modules/"));
        assertThat(allPaths).noneMatch(p -> p.endsWith(".class") || p.endsWith(".log"));
    }

    @Test
    @DisplayName("排序稳定：目录在前，同类按名称升序")
    void sortsDirectoriesFirstThenByName() throws Exception {
        JsonNode children = getJson("/projects/" + projectId() + "/tree").path("data").path("children");

        List<String> topLevel = toList(children).stream()
                .map(n -> n.path("type").asText() + ":" + n.path("name").asText())
                .toList();

        assertThat(topLevel).containsExactly(
                "DIR:docs", "DIR:src", "FILE:README.md", "FILE:pom.xml");
    }

    @Test
    @DisplayName("depth 限制展开层级：1 表示只返回一级条目")
    void limitsDepth() throws Exception {
        JsonNode tree = getJson("/projects/" + projectId() + "/tree?depth=1").path("data");

        assertThat(tree.path("children")).hasSize(4);
        assertThat(toList(tree.path("children")))
                .allSatisfy(child -> assertThat(child.path("children"))
                        .as("depth=1 时子节点的 children 应为空")
                        .isEmpty());
    }

    @Test
    @DisplayName("depth 非法时返回参数错误")
    void rejectsInvalidDepth() throws Exception {
        mockMvc.perform(get("/projects/" + projectId() + "/tree").param("depth", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    @DisplayName("子树查询：指定路径只返回该节点及其后代")
    void returnsSubtree() throws Exception {
        String body = mockMvc.perform(get("/projects/{id}/tree", projectId())
                        .param("path", "src/main/java"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode sub = objectMapper.readTree(body).path("data");

        assertThat(sub.path("path").asText()).isEqualTo("src/main/java");
        assertThat(sub.path("type").asText()).isEqualTo("DIR");

        List<String> paths = flattenPaths(sub);
        assertThat(paths).containsExactly(
                "src/main/java",
                "src/main/java/demo",
                "src/main/java/demo/App.java",
                "src/main/java/demo/Service.java");
    }

    @Test
    @DisplayName("路径不存在时返回 40400")
    void returnsNotFoundForMissingPath() throws Exception {
        mockMvc.perform(get("/projects/" + projectId() + "/tree").param("path", "no/such/dir"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    @DisplayName("项目不存在时返回 40400")
    void returnsNotFoundForMissingProject() throws Exception {
        mockMvc.perform(get("/projects/99999999/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40400));

        mockMvc.perform(get("/projects/99999999/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    @DisplayName("统计：节点数、体积、分类、语言、层级全部与磁盘一致")
    void computesStats() throws Exception {
        JsonNode stats = getJson("/projects/" + projectId() + "/stats").path("data");

        assertThat(stats.path("totalNodes").asInt()).isEqualTo(EXPECTED_TOTAL_NODES);
        assertThat(stats.path("directoryCount").asInt()).isEqualTo(EXPECTED_DIRECTORIES);
        assertThat(stats.path("fileCount").asInt()).isEqualTo(EXPECTED_FILES);
        assertThat(stats.path("totalSize").asLong()).isPositive();

        assertThat(stats.path("byCategory").path(FileCategory.SOURCE.name()).asInt())
                .as("App.java + Service.java").isEqualTo(2);
        assertThat(stats.path("byCategory").path(FileCategory.CONFIG.name()).asInt())
                .as("pom.xml + application.yml").isEqualTo(2);
        assertThat(stats.path("byCategory").path(FileCategory.DOC.name()).asInt())
                .as("README.md + docs/guide.md").isEqualTo(2);

        assertThat(stats.path("byLanguage").path("java").asInt()).isEqualTo(2);
        assertThat(stats.path("byLanguage").path("markdown").asInt()).isEqualTo(2);
        assertThat(stats.path("byLanguage").path("xml").asInt()).isEqualTo(1);
        assertThat(stats.path("byLanguage").path("yaml").asInt()).isEqualTo(1);

        // 最深的目录是 src/main/java/demo，共 4 层
        assertThat(stats.path("maxDepth").asInt()).isEqualTo(4);
    }

    @Test
    @DisplayName("统计的文件数与 t_project 上的冗余计数一致（对账）")
    void statsAgreeWithProjectCounters() throws Exception {
        Project project = projectMapper.selectById(projectId());
        JsonNode stats = getJson("/projects/" + projectId() + "/stats").path("data");

        assertThat(stats.path("fileCount").asInt()).isEqualTo(project.getFileCount());
        assertThat(stats.path("totalSize").asLong()).isEqualTo(project.getTotalSize());
    }

    // ---- helpers ----

    private Long projectId() {
        return projectId;
    }

    private JsonNode getJson(String uri) throws Exception {
        String body = mockMvc.perform(get(uri))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    private static List<JsonNode> toList(JsonNode arrayNode) {
        List<JsonNode> list = new ArrayList<>();
        arrayNode.forEach(list::add);
        return list;
    }

    private static List<String> flattenPaths(JsonNode node) {
        List<String> paths = new ArrayList<>();
        String self = node.path("path").asText();
        if (!self.isEmpty()) {
            paths.add(self);
        }
        for (JsonNode child : node.path("children")) {
            paths.addAll(flattenPaths(child));
        }
        return paths;
    }

    private byte[] zipOf() throws Exception {
        Path zip = tempDir.resolve("sample-" + System.nanoTime() + ".zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (String[] entry : ARCHIVE) {
                out.putNextEntry(new ZipEntry(entry[0]));
                out.write(entry[1].getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return Files.readAllBytes(zip);
    }
}
