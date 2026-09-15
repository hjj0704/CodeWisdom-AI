package com.codewisdom.resource.importer;

import com.codewisdom.resource.domain.enums.FileCategory;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * 文件分类。
 *
 * <p>分类结果决定后续阶段要不要处理这个文件：阶段 3 只解析 {@code SOURCE}，
 * 阶段 5 的依赖冲突检测只读 {@code CONFIG}，阶段 6 的文档生成只补 {@code SOURCE}。
 * 分错会让流水线做无用功或漏掉目标，所以判定顺序是刻意设计的：
 * <b>先看名字（最可靠），再看语言，最后才看扩展名兜底</b>。
 */
@Component
public class FileClassifier {

    /** 这些"语言"本质是文档或配置，不属于源码。 */
    private static final Set<String> NON_SOURCE_LANGUAGES = Set.of(
            "markdown", "rst", "asciidoc", "xml", "json", "yaml", "toml");

    private static final Set<String> DOC_EXTENSIONS = Set.of("md", "markdown", "rst", "adoc", "txt");

    /** 无扩展名的文档文件（README / LICENSE 等）。 */
    private static final Set<String> DOC_NAME_PREFIXES = Set.of(
            "readme", "license", "licence", "changelog", "notice", "authors", "contributing", "todo");

    /** 精确匹配的配置文件全名（小写）。 */
    private static final Set<String> CONFIG_FILE_NAMES = Set.of(
            "pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts",
            "package.json", "package-lock.json", "tsconfig.json", "vite.config.js", "vite.config.ts",
            "requirements.txt", "pyproject.toml", "setup.py", "setup.cfg", "pipfile", "poetry.lock",
            "go.mod", "go.sum", "cargo.toml", "cargo.lock", "gemfile", "composer.json",
            "dockerfile", "makefile", "cmakelists.txt",
            ".gitignore", ".dockerignore", ".editorconfig", ".npmrc", ".nvmrc", ".env", ".env.example");

    private static final Set<String> CONFIG_EXTENSIONS = Set.of(
            "yml", "yaml", "properties", "toml", "ini", "cfg", "conf", "env", "xml", "json");

    /**
     * 判定文件分类。
     *
     * @param fileName 文件名
     * @param language {@link LanguageDetector} 的结果，可为 null
     */
    public FileCategory classify(String fileName, String language) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        String extension = PathFilter.extensionOf(lower);

        // 1. 配置文件全名精确匹配，优先级最高。
        //    必须排在扩展名判断之前：requirements.txt 的扩展名是 txt，
        //    若先按扩展名判会误归为文档，后续依赖冲突检测就会漏掉它。
        if (CONFIG_FILE_NAMES.contains(lower)) {
            return FileCategory.CONFIG;
        }

        // 2. 文档：扩展名命中，或"无扩展名/文档扩展名 + 文档名"（README / LICENSE / NOTICE）
        if (DOC_EXTENSIONS.contains(extension) || isDocName(lower, extension)) {
            return FileCategory.DOC;
        }

        // 3. 有语言标识且不是文档/配置类语言 → 源码
        if (language != null && !NON_SOURCE_LANGUAGES.contains(language)) {
            return FileCategory.SOURCE;
        }

        // 4. 扩展名兜底为配置
        if (CONFIG_EXTENSIONS.contains(extension)) {
            return FileCategory.CONFIG;
        }

        return FileCategory.OTHER;
    }

    /**
     * 是否为「按名字识别」的文档文件。
     *
     * <p>限定「无扩展名或文档扩展名」，避免把 {@code LICENSE.bin} 这类同名前缀的
     * 非文档文件误判成文档。
     */
    private static boolean isDocName(String lowerFileName, String extension) {
        if (!extension.isEmpty() && !DOC_EXTENSIONS.contains(extension)) {
            return false;
        }
        String base = lowerFileName;
        int dot = base.indexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        return DOC_NAME_PREFIXES.contains(base);
    }
}
