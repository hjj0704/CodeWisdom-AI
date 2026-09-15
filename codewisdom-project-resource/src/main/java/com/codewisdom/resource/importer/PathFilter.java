package com.codewisdom.resource.importer;

import com.codewisdom.resource.config.ImportProperties;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * 文件树过滤规则。
 *
 * <p>导入的仓库里真正有价值的只是源码与配置，构建产物、依赖目录、IDE 元数据
 * 会把文件树撑大一到两个数量级（实测 RuoYi 的 {@code .git} 内部文件就有 11 个，
 * 而 Node 项目的 {@code node_modules} 动辄上万）。在遍历阶段就剪掉，比入库后再过滤便宜得多。
 *
 * <p>规则基于<b>名称</b>而非完整路径，因此 {@code src/main/webapp/dist} 这类嵌套产物目录
 * 同样会被排除。这是刻意的：构建产物出现在哪里都该被排除。
 */
@Component
public class PathFilter {

    /** 整目录排除：命中即不再递归进入。 */
    private static final Set<String> EXCLUDED_DIRS = Set.of(
            // 版本控制
            ".git", ".svn", ".hg",
            // 构建产物
            "target", "build", "out", "bin", "dist", "classes",
            // 依赖目录
            "node_modules", "bower_components", "vendor",
            // 语言运行时缓存
            "__pycache__", ".venv", "venv", ".tox", ".pytest_cache", ".mypy_cache",
            ".gradle", ".mvn",
            // IDE / 编辑器
            ".idea", ".vscode", ".settings", ".eclipse", ".fleet",
            // 其他缓存
            ".next", ".nuxt", ".cache", ".sass-cache", "coverage", ".nyc_output",
            // 本平台工作区（避免递归导入自己）
            ".spike-tmp"
    );

    /** 按文件名精确排除。 */
    private static final Set<String> EXCLUDED_FILE_NAMES = Set.of(
            ".ds_store", "thumbs.db", "desktop.ini", ".gitkeep", ".gitattributes"
    );

    /** 按扩展名排除：二进制/编译产物/日志，解析阶段也用不到。 */
    private static final Set<String> EXCLUDED_EXTENSIONS = Set.of(
            "class", "jar", "war", "ear", "dex", "apk", "aab",
            "o", "obj", "so", "dll", "dylib", "exe", "bin", "pdb",
            "pyc", "pyo", "pyd",
            "log", "tmp", "temp", "bak", "swp", "swo",
            "lock", "pid",
            "zip", "tar", "gz", "bz2", "7z", "rar",
            "png", "jpg", "jpeg", "gif", "bmp", "ico", "svg", "webp",
            "mp3", "mp4", "avi", "mov", "wav",
            "ttf", "otf", "woff", "woff2", "eot",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "db", "sqlite", "mdb", "dat"
    );

    private final ImportProperties properties;

    public PathFilter(ImportProperties properties) {
        this.properties = properties;
    }

    /** 该目录是否应整体跳过（不递归、不入库）。 */
    public boolean shouldSkipDirectory(String directoryName) {
        return EXCLUDED_DIRS.contains(lower(directoryName));
    }

    /** 该文件是否应跳过（按文件名或扩展名）。 */
    public boolean shouldSkipFile(String fileName, long size) {
        String lower = lower(fileName);
        if (EXCLUDED_FILE_NAMES.contains(lower)) {
            return true;
        }
        if (EXCLUDED_EXTENSIONS.contains(extensionOf(lower))) {
            return true;
        }
        return size > properties.getMaxFileSize();
    }

    /** 取小写扩展名；无扩展名返回空串。 */
    static String extensionOf(String lowerFileName) {
        int dot = lowerFileName.lastIndexOf('.');
        if (dot < 0 || dot == lowerFileName.length() - 1) {
            return "";
        }
        return lowerFileName.substring(dot + 1);
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
