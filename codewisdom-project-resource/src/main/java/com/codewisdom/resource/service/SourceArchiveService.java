package com.codewisdom.resource.service;

import com.codewisdom.resource.entity.Project;
import com.codewisdom.resource.importer.WorkspaceZipper;
import com.codewisdom.resource.storage.SourceArchiveStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * T-205：导入成功后把原包/源码归档到 {@code cw-source} 桶。
 */
@Service
public class SourceArchiveService {

    private static final Logger log = LoggerFactory.getLogger(SourceArchiveService.class);
    private static final String ARCHIVE_OBJECT = "source.zip";

    private final SourceArchiveStorage storage;

    public SourceArchiveService(SourceArchiveStorage storage) {
        this.storage = storage;
    }

    /**
     * @param originalZip 若非空且存在，直接归档原 ZIP；否则将工作区目录打包后归档。
     */
    public void archiveProjectSource(Project project, Path workspace, Path originalZip) {
        String bucket = project.getStorageBucket();
        String objectKey = project.getStoragePrefix() + ARCHIVE_OBJECT;
        Path toUpload = null;
        boolean deleteTemp = false;
        try {
            if (originalZip != null && Files.isRegularFile(originalZip)) {
                toUpload = originalZip;
            } else {
                toUpload = WorkspaceZipper.zipDirectory(workspace);
                deleteTemp = true;
            }
            long localSize = Files.size(toUpload);
            storage.putFile(bucket, objectKey, toUpload, "application/zip");
            long remoteSize = storage.objectSize(bucket, objectKey);
            if (remoteSize != localSize) {
                throw new IllegalStateException(
                        "归档大小不一致 local=" + localSize + " remote=" + remoteSize);
            }
            log.info("源码归档完成 projectId={} key={} size={}", project.getId(), objectKey, localSize);
        } catch (IOException ex) {
            throw new IllegalStateException("源码归档失败 projectId=" + project.getId(), ex);
        } finally {
            if (deleteTemp && toUpload != null) {
                try {
                    Files.deleteIfExists(toUpload);
                } catch (IOException ex) {
                    log.warn("临时归档文件删除失败: {}", toUpload, ex);
                }
            }
        }
    }
}
