package com.codewisdom.resource.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codewisdom.resource.domain.enums.ImportTaskStatus;
import com.codewisdom.resource.domain.enums.ProjectStatus;
import com.codewisdom.resource.dto.GitImportRequest;
import com.codewisdom.resource.dto.ImportResult;
import com.codewisdom.resource.entity.ImportTask;
import com.codewisdom.resource.entity.Project;
import com.codewisdom.resource.mapper.ImportTaskMapper;
import com.codewisdom.resource.mapper.ProjectMapper;
import com.codewisdom.resource.config.ImportProperties;
import com.codewisdom.resource.security.RepoUrlValidator;
import com.codewisdom.resource.service.ImportService;
import com.codewisdom.resource.testsupport.TestWorkspaces;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(ImportTaskMqTest.LocalRepoConfig.class)
@DisplayName("T-206 RabbitMQ 异步导入")
class ImportTaskMqTest {

    @Autowired
    private ImportService importService;

    @Autowired
    private ImportTaskMapper importTaskMapper;

    @Autowired
    private ProjectMapper projectMapper;

    @TempDir
    Path originRoot;

    private Path localRepo;

    @AfterAll
    static void cleanWorkspaces() {
        TestWorkspaces.forceDelete(Path.of(System.getProperty("java.io.tmpdir"), "codewisdom-test-repos"));
    }

    @BeforeEach
    void setUpLocalRepo() throws Exception {
        localRepo = originRoot.resolve("demo-repo");
        Files.createDirectories(localRepo.resolve("src"));
        Files.writeString(localRepo.resolve("README.md"), "# mq test");
        try (Git git = Git.init().setDirectory(localRepo.toFile()).call()) {
            PersonIdent who = new PersonIdent("CodeWisdom", "dev@codewisdom.local");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("init").setAuthor(who).setCommitter(who).setSign(false).call();
        }
    }

    @Test
    @DisplayName("异步导入：PENDING → RUNNING → SUCCESS")
    void asyncImportTransitionsTaskStatus() {
        String url = localRepo.toUri().toString();
        GitImportRequest request = new GitImportRequest(url, null, "MQ 异步项目", true, true);

        ImportResult enqueued = importService.enqueueGitImport(request, 1L);
        assertThat(enqueued.status()).isEqualTo(ImportTaskStatus.PENDING);

        ImportTask pending = importTaskMapper.selectById(enqueued.taskId());
        assertThat(pending.getStatus()).isEqualTo(ImportTaskStatus.PENDING);

        ParseTaskMessage message = ParseTaskMessage.gitImport(
                enqueued.projectId(), enqueued.taskId(), url, null, true);
        importService.processAsyncGitImport(message);

        ImportTask finished = importTaskMapper.selectById(enqueued.taskId());
        assertThat(finished.getStatus()).isEqualTo(ImportTaskStatus.SUCCESS);
        assertThat(finished.getStartedAt()).isNotNull();
        assertThat(finished.getFinishedAt()).isNotNull();

        Project project = projectMapper.selectById(enqueued.projectId());
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.READY);
        assertThat(project.getFileCount()).isPositive();
    }

    @TestConfiguration
    static class LocalRepoConfig {

        @Bean
        @Primary
        RepoUrlValidator permissiveRepoUrlValidator(ImportProperties properties) {
            return new RepoUrlValidator(properties) {
                @Override
                public java.net.URI validate(String rawUrl) {
                    if (rawUrl == null || rawUrl.isBlank() || !rawUrl.contains("://")) {
                        return super.validate(rawUrl);
                    }
                    return java.net.URI.create(rawUrl.trim());
                }
            };
        }
    }
}
