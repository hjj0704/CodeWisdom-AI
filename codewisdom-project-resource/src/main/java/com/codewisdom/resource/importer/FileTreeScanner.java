package com.codewisdom.resource.importer;

import com.codewisdom.common.api.ErrorCode;
import com.codewisdom.common.exception.BizException;
import com.codewisdom.resource.config.ImportProperties;
import com.codewisdom.resource.domain.enums.FileCategory;
import com.codewisdom.resource.domain.enums.FileNodeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/**
 * 扫描工作区，构建内存中的文件树。
 *
 * <p>只做扫描与建模，不碰数据库——因为自增主键要等插入后才知道，
 * 落库的分层写入由 {@code ImportService} 负责。这样扫描逻辑可以脱离 Spring 与数据库单测。
 *
 * <p>两道熔断：单文件超过 {@code maxFileSize} 跳过；节点总数超过 {@code maxFiles} 中止导入。
 * 后者是防御性的——一个异常仓库（比如把依赖目录提交进版本库的项目）足以打爆内存。
 */
@Component
public class FileTreeScanner {

    private static final Logger log = LoggerFactory.getLogger(FileTreeScanner.class);

    private final PathFilter pathFilter;
    private final LanguageDetector languageDetector;
    private final FileClassifier fileClassifier;
    private final ImportProperties properties;

    public FileTreeScanner(PathFilter pathFilter,
                           LanguageDetector languageDetector,
                           FileClassifier fileClassifier,
                           ImportProperties properties) {
        this.pathFilter = pathFilter;
        this.languageDetector = languageDetector;
        this.fileClassifier = fileClassifier;
        this.properties = properties;
    }

    /**
     * 扫描结果。
     *
     * @param roots     根节点列表（相对工作区根目录）
     * @param fileCount 纳入的文件总数（不含目录）
     * @param totalSize 纳入的文件总字节数
     */
    public record ScanResult(List<ScannedNode> roots, int fileCount, long totalSize) {
    }

    /**
     * 扫描树节点。
     *
     * @param path     相对工作区根的路径，使用 {@code /} 分隔
     * @param name     节点名
     * @param type     目录或文件
     * @param language 语言标识，仅源码文件有值
     * @param category 分类
     * @param size     文件字节数；目录为 0
     * @param checksum 内容 SHA-256；目录为 null
     * @param children 子节点
     */
    public record ScannedNode(String path,
                              String name,
                              FileNodeType type,
                              String language,
                              FileCategory category,
                              long size,
                              String checksum,
                              List<ScannedNode> children) {
    }

    /**
     * 扫描目录。
     *
     * @param workspaceRoot 已拉取到本地的工作区根目录
     */
    public ScanResult scan(Path workspaceRoot) throws IOException {
        if (!Files.isDirectory(workspaceRoot)) {
            throw BizException.of(ErrorCode.IMPORT_ERROR, "工作区目录不存在: " + workspaceRoot);
        }

        Counter counter = new Counter(properties.getMaxFiles());
        List<ScannedNode> roots = scanChildren(workspaceRoot, "", counter);
        log.info("文件树扫描完成 dir={} files={} size={}B", workspaceRoot, counter.files, counter.size);
        return new ScanResult(roots, counter.files, counter.size);
    }

    private List<ScannedNode> scanChildren(Path dir, String parentPath, Counter counter) throws IOException {
        List<ScannedNode> nodes = new ArrayList<>();
        List<Path> entries;
        try (Stream<Path> stream = Files.list(dir)) {
            entries = stream.sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
        }

        for (Path entry : entries) {
            String name = entry.getFileName().toString();
            String path = parentPath.isEmpty() ? name : parentPath + "/" + name;

            if (Files.isDirectory(entry)) {
                if (pathFilter.shouldSkipDirectory(name)) {
                    continue;
                }
                counter.tick(path);
                nodes.add(new ScannedNode(path, name, FileNodeType.DIR, null,
                        FileCategory.OTHER, 0L, null, scanChildren(entry, path, counter)));
                continue;
            }

            if (!Files.isRegularFile(entry)) {
                continue;
            }

            long size = Files.size(entry);
            if (pathFilter.shouldSkipFile(name, size)) {
                continue;
            }

            counter.tick(path);
            counter.addSize(size);
            String language = languageDetector.detect(name);
            nodes.add(new ScannedNode(path, name, FileNodeType.FILE, language,
                    fileClassifier.classify(name, language), size, sha256(entry), List.of()));
        }
        return nodes;
    }

    /** 节点计数、体积累加与上限熔断。 */
    private static final class Counter {
        private final int max;
        private int total;
        private int files;
        private long size;

        private Counter(int max) {
            this.max = max;
        }

        private void tick(String path) {
            if (++total > max) {
                throw BizException.of(ErrorCode.IMPORT_ERROR,
                        "文件数超过上限 " + max + "，已中止导入（触发路径: " + path + "）");
            }
        }

        private void addSize(long bytes) {
            files++;
            size += bytes;
        }
    }

    /** 内容 SHA-256，用于阶段 3 判断文件是否变化而跳过重复解析。 */
    static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            try (var in = Files.newInputStream(file)) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 必须支持 SHA-256", e);
        }
    }
}
