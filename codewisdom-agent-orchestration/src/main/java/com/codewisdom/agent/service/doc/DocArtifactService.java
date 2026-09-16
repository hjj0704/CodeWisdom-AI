package com.codewisdom.agent.service.doc;

import com.codewisdom.agent.domain.doc.DocArtifactType;
import com.codewisdom.agent.entity.DocRecordEntity;
import com.codewisdom.agent.mapper.DocRecordMapper;
import com.codewisdom.agent.storage.ArtifactStorage;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * 文档产物归档（T-604）：Markdown/文本 → MinIO {@code cw-artifact} + {@code t_doc_record}。
 */
@Service
public class DocArtifactService {

    public static final String ARTIFACT_BUCKET = "cw-artifact";

    private final ArtifactStorage artifactStorage;
    private final DocRecordMapper docRecordMapper;

    public DocArtifactService(ArtifactStorage artifactStorage, DocRecordMapper docRecordMapper) {
        this.artifactStorage = Objects.requireNonNull(artifactStorage);
        this.docRecordMapper = Objects.requireNonNull(docRecordMapper);
    }

    public DocArtifactRecord archive(DocArtifactCommand command) {
        Objects.requireNonNull(command, "command");
        byte[] bytes = command.content().getBytes(StandardCharsets.UTF_8);
        String objectKey = buildObjectKey(command);

        artifactStorage.put(ARTIFACT_BUCKET, objectKey, bytes, command.contentType());

        DocRecordEntity entity = new DocRecordEntity();
        entity.setProjectId(command.projectId());
        entity.setDocType(command.docType().name());
        entity.setBucket(ARTIFACT_BUCKET);
        entity.setObjectKey(objectKey);
        entity.setFileName(command.fileName());
        entity.setContentSize((long) bytes.length);
        docRecordMapper.insert(entity);

        return new DocArtifactRecord(
                entity.getId(),
                entity.getProjectId(),
                command.docType(),
                ARTIFACT_BUCKET,
                objectKey,
                command.fileName(),
                bytes.length);
    }

    private static String buildObjectKey(DocArtifactCommand command) {
        String prefix = command.projectId() == null ? "global" : "project-" + command.projectId();
        return prefix + "/" + command.docType().name().toLowerCase() + "/"
                + UUID.randomUUID() + "-" + sanitizeFileName(command.fileName());
    }

    private static String sanitizeFileName(String fileName) {
        return fileName.replace('\\', '/').replaceAll("^/+", "");
    }

    public record DocArtifactCommand(
            Long projectId,
            DocArtifactType docType,
            String fileName,
            String content,
            String contentType
    ) {
        public DocArtifactCommand {
            Objects.requireNonNull(docType, "docType");
            Objects.requireNonNull(fileName, "fileName");
            Objects.requireNonNull(content, "content");
            if (contentType == null || contentType.isBlank()) {
                contentType = "text/markdown; charset=utf-8";
            }
        }
    }

    public record DocArtifactRecord(
            Long id,
            Long projectId,
            DocArtifactType docType,
            String bucket,
            String objectKey,
            String fileName,
            long contentSize
    ) {
    }
}
