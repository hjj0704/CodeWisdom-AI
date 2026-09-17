package com.codewisdom.resource.storage;

import com.codewisdom.resource.entity.Project;
import com.codewisdom.resource.service.SourceArchiveService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("T-205 MinIO 源码归档")
class MinioArchiveTest {

    @Autowired
    private SourceArchiveService sourceArchiveService;

    @Autowired
    private SourceArchiveStorage storage;

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("归档后对象大小与本地 ZIP 一致")
    void archivePreservesSize() throws Exception {
        Path zip = tempDir.resolve("upload.zip");
        byte[] payload = new byte[] {1, 2, 3, 4, 5};
        Files.write(zip, payload);

        Project project = new Project();
        project.setId(99L);
        project.setStorageBucket("cw-source");
        project.setStoragePrefix("projects/test-archive/");

        sourceArchiveService.archiveProjectSource(project, tempDir, zip);

        String key = "projects/test-archive/source.zip";
        assertThat(storage.exists("cw-source", key)).isTrue();
        assertThat(storage.objectSize("cw-source", key)).isEqualTo(payload.length);
    }
}
