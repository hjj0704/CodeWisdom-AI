package com.codewisdom.agent.service.doc;

import com.codewisdom.agent.domain.doc.DocArtifactType;
import com.codewisdom.agent.entity.DocRecordEntity;
import com.codewisdom.agent.mapper.DocRecordMapper;
import com.codewisdom.agent.storage.ArtifactStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-604 验收：文档产物落 MinIO（测试档用内存实现）+ DB 可追溯记录。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("T-604 文档产物归档")
class DocArtifactTest {

    @Autowired
    private DocArtifactService docArtifactService;

    @Autowired
    private DocRecordMapper docRecordMapper;

    @Autowired
    private ArtifactStorage artifactStorage;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Flyway 建表 t_doc_record")
    void schemaApplied() {
        Integer tables = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 't_doc_record'",
                Integer.class);
        assertThat(tables).isEqualTo(1);
    }

    @Test
    @DisplayName("归档后对象存储与 DB 记录一致")
    void archiveWritesStorageAndDb() {
        String markdown = """
                # API 文档
                ## GET /projects
                列出项目
                """;
        DocArtifactService.DocArtifactRecord record = docArtifactService.archive(
                new DocArtifactService.DocArtifactCommand(
                        100L,
                        DocArtifactType.API,
                        "api.md",
                        markdown,
                        "text/markdown"));

        assertThat(record.contentSize()).isEqualTo(markdown.getBytes(StandardCharsets.UTF_8).length);
        assertThat(artifactStorage.exists(record.bucket(), record.objectKey())).isTrue();

        byte[] stored = artifactStorage.get(record.bucket(), record.objectKey()).orElseThrow();
        assertThat(new String(stored, StandardCharsets.UTF_8)).isEqualTo(markdown);

        DocRecordEntity entity = docRecordMapper.selectById(record.id());
        assertThat(entity.getProjectId()).isEqualTo(100L);
        assertThat(entity.getDocType()).isEqualTo("API");
        assertThat(entity.getBucket()).isEqualTo(DocArtifactService.ARTIFACT_BUCKET);
        assertThat(entity.getObjectKey()).isEqualTo(record.objectKey());
        assertThat(entity.getContentSize()).isEqualTo(record.contentSize());
        assertThat(entity.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("支持 MODULE / DEPLOY 类型")
    void moduleAndDeployTypes() {
        docArtifactService.archive(new DocArtifactService.DocArtifactCommand(
                1L, DocArtifactType.MODULE, "module.md", "## 模块说明", null));
        docArtifactService.archive(new DocArtifactService.DocArtifactCommand(
                1L, DocArtifactType.DEPLOY, "deploy.md", "## 部署", null));

        assertThat(docRecordMapper.selectList(null)).hasSize(2);
    }
}
