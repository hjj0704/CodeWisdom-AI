package com.codewisdom.resource.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codewisdom.common.api.ErrorCode;
import com.codewisdom.common.exception.BizException;
import com.codewisdom.resource.config.ImportProperties;
import com.codewisdom.resource.domain.enums.FileNodeType;
import com.codewisdom.resource.domain.enums.ProjectStatus;
import com.codewisdom.resource.dto.FileContentResponse;
import com.codewisdom.resource.entity.FileNode;
import com.codewisdom.resource.entity.Project;
import com.codewisdom.resource.mapper.FileNodeMapper;
import com.codewisdom.resource.mapper.ProjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 项目源码读盘：在线预览与 ZIP 导出。
 */
@Service
public class ProjectFileService {

    private static final int BINARY_PROBE_BYTES = 8192;

    private final ProjectMapper projectMapper;
    private final FileNodeMapper fileNodeMapper;
    private final ImportProperties properties;
    private final ProjectQueryService projectQueryService;

    public ProjectFileService(ProjectMapper projectMapper,
                              FileNodeMapper fileNodeMapper,
                              ImportProperties properties,
                              ProjectQueryService projectQueryService) {
        this.projectMapper = projectMapper;
        this.fileNodeMapper = fileNodeMapper;
        this.properties = properties;
        this.projectQueryService = projectQueryService;
    }

    public FileContentResponse readFile(Long projectId, String path, long userId) {
        projectQueryService.requireReadyProject(projectId, userId);
        requireProject(projectId);
        String normalized = normalizePath(path);
        if (normalized.isEmpty()) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "path 不能为空");
        }

        FileNode node = fileNodeMapper.selectOne(new LambdaQueryWrapper<FileNode>()
                .eq(FileNode::getProjectId, projectId)
                .eq(FileNode::getPath, normalized));
        if (node == null || node.getNodeType() != FileNodeType.FILE) {
            throw BizException.of(ErrorCode.NOT_FOUND, "文件不存在: " + normalized);
        }

        Path scanRoot = scanRoot(projectId);
        Path file = resolveSafe(scanRoot, normalized);
        if (!Files.isRegularFile(file)) {
            throw BizException.of(ErrorCode.NOT_FOUND, "磁盘上找不到文件: " + normalized);
        }

        long size = node.getSize() == null ? 0L : node.getSize();
        if (size > properties.getMaxFileSize()) {
            throw BizException.of(ErrorCode.PAYLOAD_TOO_LARGE, "文件过大，请下载 ZIP 查看");
        }

        try {
            byte[] bytes = Files.readAllBytes(file);
            if (isBinary(bytes)) {
                return new FileContentResponse(normalized, node.getLanguage(), size, true, null);
            }
            String text = new String(bytes, detectCharset(bytes));
            return new FileContentResponse(normalized, node.getLanguage(), size, false, text);
        } catch (IOException e) {
            throw BizException.of(ErrorCode.IMPORT_ERROR, "读取文件失败: " + e.getMessage(), e);
        }
    }

    public FileContentResponse saveFile(Long projectId, String path, String content, long userId) {
        projectQueryService.requireReadyProject(projectId, userId);
        requireProject(projectId);
        String normalized = normalizePath(path);
        if (normalized.isEmpty()) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "path 不能为空");
        }
        if (content == null) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "content 不能为空");
        }
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > properties.getMaxFileSize()) {
            throw BizException.of(ErrorCode.PAYLOAD_TOO_LARGE, "文件过大");
        }
        if (isBinary(bytes)) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "不支持保存二进制内容");
        }

        FileNode node = fileNodeMapper.selectOne(new LambdaQueryWrapper<FileNode>()
                .eq(FileNode::getProjectId, projectId)
                .eq(FileNode::getPath, normalized));
        if (node == null || node.getNodeType() != FileNodeType.FILE) {
            throw BizException.of(ErrorCode.NOT_FOUND, "文件不存在: " + normalized);
        }

        Path scanRoot = scanRoot(projectId);
        Path file = resolveSafe(scanRoot, normalized);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
            node.setSize((long) bytes.length);
            fileNodeMapper.updateById(node);
            return new FileContentResponse(normalized, node.getLanguage(), bytes.length, false, content);
        } catch (IOException e) {
            throw BizException.of(ErrorCode.IMPORT_ERROR, "保存文件失败: " + e.getMessage(), e);
        }
    }

    public void writeProjectZip(Long projectId, OutputStream out, long userId) {
        projectQueryService.requireExportable(projectId, userId);
        requireProject(projectId);
        Path scanRoot = scanRoot(projectId);
        if (!Files.isDirectory(scanRoot)) {
            throw BizException.of(ErrorCode.NOT_FOUND, "项目工作区不存在");
        }

        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            Files.walkFileTree(scanRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String entryName = scanRoot.relativize(file).toString().replace('\\', '/');
                    zip.putNextEntry(new ZipEntry(entryName));
                    Files.copy(file, zip);
                    zip.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw BizException.of(ErrorCode.IMPORT_ERROR, "打包失败: " + e.getMessage(), e);
        }
    }

    Path scanRoot(Long projectId) {
        Path workspace = Paths.get(properties.getWorkspaceRoot()).resolve(String.valueOf(projectId));
        Path repoDir = workspace.resolve("repo");
        if (Files.isDirectory(repoDir)) {
            return repoDir;
        }
        return workspace;
    }

    private Project requireProject(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null || project.getStatus() != ProjectStatus.READY) {
            throw BizException.of(ErrorCode.NOT_FOUND, "项目不存在或未就绪: " + projectId);
        }
        return project;
    }

    private static Path resolveSafe(Path root, String relativePath) {
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root.normalize())) {
            throw BizException.of(ErrorCode.FORBIDDEN, "非法路径");
        }
        return resolved;
    }

    private static String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        String normalized = path.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static boolean isBinary(byte[] bytes) {
        int limit = Math.min(bytes.length, BINARY_PROBE_BYTES);
        for (int i = 0; i < limit; i++) {
            if (bytes[i] == 0) {
                return true;
            }
        }
        return false;
    }

    private static Charset detectCharset(byte[] bytes) {
        String probe = new String(bytes, 0, Math.min(bytes.length, 512), StandardCharsets.UTF_8);
        if (probe.contains("\uFFFD")) {
            return Charset.forName("GBK");
        }
        return StandardCharsets.UTF_8;
    }
}
