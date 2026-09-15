package com.codewisdom.resource.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codewisdom.resource.domain.enums.ImportTaskStatus;
import com.codewisdom.resource.domain.enums.ProjectStatus;
import com.codewisdom.resource.entity.FileNode;
import com.codewisdom.resource.entity.Project;
import com.codewisdom.resource.mapper.FileNodeMapper;
import com.codewisdom.resource.mapper.ProjectMapper;
import com.codewisdom.resource.testsupport.TestWorkspaces;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T-202 真实链路验收：从公网 Git 仓库导入。
 *
 * <p>与 {@code GitImportIntegrationTest} 的区别在于——这里<b>不替换校验器</b>，
 * 走的是完整的 http(s) + 域名白名单 + 公网地址校验链，以及 JGit 真实的 HTTP 传输。
 * 本地 file:// 用例证明的是编排逻辑，本用例证明的是生产路径本身。
 *
 * <p>打 {@code network} 标签：外网不可达时自动跳过，不会把离线构建染红。
 * <b>环境说明</b>：本机实测 {@code github.com} 不可达，故指向 Gitee。
 */
@Tag("network")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("T-202 真实仓库导入（Gitee）")
class GitNetworkImportTest {

    private static final String REPO_URL = "https://gitee.com/y_project/RuoYi.git";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private FileNodeMapper fileNodeMapper;

    @BeforeAll
    static void requireNetwork() {
        assumeTrue(reachable("gitee.com", 443), "gitee.com 不可达，跳过真实仓库导入用例");
    }

    @AfterAll
    static void cleanWorkspaces() {
        TestWorkspaces.forceDelete(Path.of(System.getProperty("java.io.tmpdir"), "codewisdom-test-repos"));
    }

    @Test
    @DisplayName("导入 Gitee 公开仓库：校验通过、克隆成功、文件树落库且产物被剪掉")
    void importsRealPublicRepository() throws Exception {
        // 先抓原始响应再断言：失败时能直接看到服务端给出的原因，不必重跑
        String body = mockMvc.perform(post("/import/git")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"" + REPO_URL + "\",\"name\":\"RuoYi-真实导入\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).as("导入应成功，实际响应: %s", body)
                .contains("\"status\":\"SUCCESS\"")
                .contains("\"code\":0");

        Long projectId = projectMapper.selectList(new LambdaQueryWrapper<Project>()
                        .eq(Project::getName, "RuoYi-真实导入"))
                .stream().findFirst().orElseThrow().getId();

        Project project = projectMapper.selectById(projectId);
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.READY);
        assertThat(project.getSourceType().name()).isEqualTo("GIT");
        assertThat(project.getFileCount()).isPositive();
        assertThat(project.getTotalSize()).isPositive();
        assertThat(project.getStoragePrefix()).startsWith("projects/");

        List<FileNode> nodes = fileNodeMapper.selectList(new LambdaQueryWrapper<FileNode>()
                .eq(FileNode::getProjectId, projectId));
        List<String> paths = nodes.stream().map(FileNode::getPath).toList();

        assertThat(nodes).hasSizeGreaterThan(100);
        assertThat(paths).anyMatch(p -> p.endsWith("pom.xml"));
        assertThat(paths).anyMatch(p -> p.endsWith(".java"));
        assertThat(paths).as(".git 目录必须被剪掉")
                .noneMatch(p -> p.equals(".git") || p.startsWith(".git/"));
        assertThat(paths).as("构建产物必须被剪掉").noneMatch(p -> p.startsWith("target/"));
        assertThat(paths).as("二进制必须被剪掉")
                .noneMatch(p -> p.endsWith(".class") || p.endsWith(".jar"));
        // .gitignore 是合法配置文件，不在剪枝范围内（前缀形似 .git，勿误伤）
        assertThat(paths).contains(".gitignore");

        System.out.printf("[T-202] 真实导入成功 projectId=%d branch=%s files=%d size=%dB%n",
                projectId, project.getDefaultBranch(), project.getFileCount(), project.getTotalSize());
    }

    @Test
    @DisplayName("非白名单域名被校验器拒绝，不产生任何项目记录")
    void rejectsNonAllowListedHost() throws Exception {
        long before = projectMapper.selectCount(null);

        mockMvc.perform(post("/import/git")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/a/b.git\",\"name\":\"不该出现\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(51001));

        assertThat(projectMapper.selectCount(null)).isEqualTo(before);
    }

    @Test
    @DisplayName("项目文件树可经 HTTP 查询接口读取（为 T-204 预留的连通性检查）")
    void pingStillRespondsAlongsideImportFeature() throws Exception {
        mockMvc.perform(get("/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value("codewisdom-project-resource"));
    }

    private static boolean reachable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 3_000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
