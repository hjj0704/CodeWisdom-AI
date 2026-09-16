package com.codewisdom.evaluation.service;

import com.codewisdom.evaluation.storage.ExportStorage.InMemoryStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("T-803 项目 ZIP 导出")
class ExportTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("导出 ZIP 可下载且内容与源一致")
    void exportPreservesContent() throws Exception {
        Path src = tempDir.resolve("project");
        Files.createDirectories(src.resolve("src"));
        Path file = src.resolve("src/App.java");
        String content = "public class App {}";
        Files.writeString(file, content);

        ProjectExportService exporter = new ProjectExportService(new InMemoryStore());
        ProjectExportService.ExportResult result = exporter.exportProject(42L, src);

        assertThat(result.bucket()).isEqualTo(ProjectExportService.EXPORT_BUCKET);
        assertThat(result.sizeBytes()).isPositive();

        byte[] entry = exporter.readEntry(result.bucket(), result.objectKey(), "src/App.java");
        assertThat(new String(entry, StandardCharsets.UTF_8)).isEqualTo(content);
    }
}
