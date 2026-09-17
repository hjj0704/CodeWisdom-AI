package com.codewisdom.evaluation.service;

import com.codewisdom.common.api.ErrorCode;
import com.codewisdom.common.exception.BizException;
import com.codewisdom.evaluation.storage.ExportStorage.Store;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 项目 ZIP 导出并上传 MinIO {@code cw-export}（T-803）。
 */
public class ProjectExportService {

    public static final String EXPORT_BUCKET = "cw-export";

    private final Store store;

    public ProjectExportService(Store store) {
        this.store = Objects.requireNonNull(store);
    }

    public ExportResult exportProject(long projectId, Path sourceRoot) {
        Objects.requireNonNull(sourceRoot, "sourceRoot");
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("源目录不存在: " + sourceRoot);
        }

        byte[] zipBytes = zipDirectory(sourceRoot);
        String objectKey = "project-" + projectId + "/" + UUID.randomUUID() + ".zip";
        store.put(EXPORT_BUCKET, objectKey, zipBytes);

        return new ExportResult(projectId, EXPORT_BUCKET, objectKey, zipBytes.length);
    }

    public ExportResult exportFromWorkspace(long projectId, Path workspaceRoot) {
        Path sourceRoot = resolveProjectRoot(workspaceRoot, projectId);
        if (!Files.isDirectory(sourceRoot)) {
            throw BizException.of(ErrorCode.NOT_FOUND, "项目工作区不存在: " + projectId);
        }
        return exportProject(projectId, sourceRoot);
    }

    public byte[] downloadExport(long projectId, String objectKey) {
        String prefix = "project-" + projectId + "/";
        if (objectKey == null || !objectKey.startsWith(prefix) || objectKey.contains("..")) {
            throw BizException.of(ErrorCode.FORBIDDEN, "无权下载该导出对象");
        }
        return store.get(EXPORT_BUCKET, objectKey).orElseThrow(
                () -> BizException.of(ErrorCode.NOT_FOUND, "导出对象不存在"));
    }

    public static Path resolveProjectRoot(Path workspaceRoot, long projectId) {
        Path workspace = workspaceRoot.resolve(String.valueOf(projectId));
        Path repoDir = workspace.resolve("repo");
        if (Files.isDirectory(repoDir)) {
            return repoDir;
        }
        return workspace;
    }

    /** 解压已存储的 ZIP，用于验收「内容与源一致」。 */
    public byte[] readEntry(String bucket, String objectKey, String entryPath) {
        byte[] zip = store.get(bucket, objectKey).orElseThrow(
                () -> new IllegalArgumentException("导出对象不存在"));
        try (ZipInputStream zis = new ZipInputStream(new java.io.ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals(entryPath)) {
                    return zis.readAllBytes();
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("读取 ZIP 条目失败", ex);
        }
        throw new IllegalArgumentException("ZIP 中无条目: " + entryPath);
    }

    private static byte[] zipDirectory(Path root) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(path -> !Files.isDirectory(path))
                        .forEach(path -> {
                            String entryName = root.relativize(path).toString().replace('\\', '/');
                            try {
                                zos.putNextEntry(new ZipEntry(entryName));
                                zos.write(Files.readAllBytes(path));
                                zos.closeEntry();
                            } catch (IOException ex) {
                                throw new IllegalStateException("打包失败: " + path, ex);
                            }
                        });
            }
            zos.finish();
            return baos.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("创建 ZIP 失败", ex);
        }
    }

    public record ExportResult(long projectId, String bucket, String objectKey, long sizeBytes) {
    }
}
