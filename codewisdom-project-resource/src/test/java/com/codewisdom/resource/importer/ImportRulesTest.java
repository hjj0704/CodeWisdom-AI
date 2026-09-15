package com.codewisdom.resource.importer;

import com.codewisdom.resource.config.ImportProperties;
import com.codewisdom.resource.domain.enums.FileCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 导入规则测试：路径过滤、语言识别、文件分类。
 *
 * <p>三者共同决定「哪些文件进入流水线、按什么类型处理」，规则错了会直接导致
 * 后续阶段漏检或做无用功，因此逐条锁定。
 */
@DisplayName("导入规则")
class ImportRulesTest {

    private ImportProperties properties;
    private PathFilter pathFilter;
    private LanguageDetector languageDetector;
    private FileClassifier fileClassifier;

    @BeforeEach
    void setUp() {
        properties = new ImportProperties();
        pathFilter = new PathFilter(properties);
        languageDetector = new LanguageDetector();
        fileClassifier = new FileClassifier();
    }

    @Nested
    @DisplayName("PathFilter 目录排除")
    class DirectoryFilter {

        @ParameterizedTest(name = "排除目录 {0}")
        @ValueSource(strings = {
                ".git", ".svn", ".hg",
                "target", "build", "out", "bin", "dist", "classes",
                "node_modules", "vendor",
                "__pycache__", ".venv", "venv", ".tox",
                ".gradle", ".mvn",
                ".idea", ".vscode", ".settings",
                ".next", ".nuxt", "coverage"
        })
        void skipsBuildAndCacheDirectories(String dir) {
            assertThat(pathFilter.shouldSkipDirectory(dir)).as("应排除 %s", dir).isTrue();
        }

        @Test
        @DisplayName("目录名大小写不敏感")
        void isCaseInsensitive() {
            assertThat(pathFilter.shouldSkipDirectory("NODE_MODULES")).isTrue();
            assertThat(pathFilter.shouldSkipDirectory("Target")).isTrue();
        }

        @ParameterizedTest(name = "保留目录 {0}")
        @ValueSource(strings = {"src", "main", "java", "resources", "test", "docs", "config", "web", "api"})
        void keepsSourceDirectories(String dir) {
            assertThat(pathFilter.shouldSkipDirectory(dir)).as("不应排除 %s", dir).isFalse();
        }
    }

    @Nested
    @DisplayName("PathFilter 文件排除")
    class FileFilter {

        @ParameterizedTest(name = "排除文件 {0}")
        @ValueSource(strings = {
                "App.class", "lib.jar", "app.war", "core.dll", "main.o",
                "cache.pyc", "app.log", "temp.tmp", "data.zip", "logo.png",
                "font.woff2", "report.pdf", ".DS_Store", "Thumbs.db"
        })
        void skipsBinaryAndArtifacts(String file) {
            assertThat(pathFilter.shouldSkipFile(file, 100)).as("应排除 %s", file).isTrue();
        }

        @ParameterizedTest(name = "保留文件 {0}")
        @ValueSource(strings = {
                "App.java", "main.py", "index.ts", "pom.xml", "README.md",
                "application.yml", "Dockerfile", "Makefile", "App.vue"
        })
        void keepsSourceAndConfigFiles(String file) {
            assertThat(pathFilter.shouldSkipFile(file, 100)).as("不应排除 %s", file).isFalse();
        }

        @Test
        @DisplayName("超过大小上限的文件被跳过")
        void skipsOversizedFiles() {
            properties.setMaxFileSize(1024);
            assertThat(pathFilter.shouldSkipFile("Big.java", 1024)).isFalse();
            assertThat(pathFilter.shouldSkipFile("Big.java", 1025)).isTrue();
        }

        @Test
        @DisplayName("无扩展名文件不被扩展名规则误伤")
        void handlesExtensionlessFiles() {
            assertThat(PathFilter.extensionOf("dockerfile")).isEmpty();
            assertThat(PathFilter.extensionOf("archive.tar.gz")).isEqualTo("gz");
            assertThat(PathFilter.extensionOf("trailing.")).isEmpty();
        }
    }

    @Nested
    @DisplayName("LanguageDetector 语言识别")
    class LanguageDetection {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
                "App.java,java", "Main.kt,kotlin", "build.gradle.kts,kotlin",
                "index.js,javascript", "view.tsx,typescript", "App.vue,vue",
                "main.py,python", "server.go,go", "lib.rs,rust",
                "schema.sql,sql", "app.yml,yaml", "config.toml,toml",
                "README.md,markdown", "script.sh,shell"
        })
        void detectsByExtension(String fileName, String expected) {
            assertThat(languageDetector.detect(fileName)).isEqualTo(expected);
        }

        @Test
        @DisplayName("大小写不敏感")
        void isCaseInsensitive() {
            assertThat(languageDetector.detect("APP.JAVA")).isEqualTo("java");
        }

        @ParameterizedTest(name = "无法识别 {0}")
        @ValueSource(strings = {"data.unknownext", "dockerfile", "Makefile", "noext"})
        void returnsNullForUnknown(String fileName) {
            assertThat(languageDetector.detect(fileName)).isNull();
        }

        @Test
        @DisplayName("入参为 null 或空串时安全返回 null")
        void handlesNullInput() {
            assertThat(languageDetector.detect(null)).isNull();
            assertThat(languageDetector.detect("  ")).isNull();
        }
    }

    @Nested
    @DisplayName("FileClassifier 文件分类")
    class Classification {

        @ParameterizedTest(name = "源码 {0}")
        @ValueSource(strings = {"App.java", "main.py", "index.ts", "App.vue", "server.go", "lib.rs"})
        void classifiesSourceCode(String file) {
            assertThat(fileClassifier.classify(file, languageDetector.detect(file)))
                    .isEqualTo(FileCategory.SOURCE);
        }

        @ParameterizedTest(name = "配置 {0}")
        @ValueSource(strings = {
                "pom.xml", "build.gradle", "package.json", "requirements.txt",
                "Dockerfile", "Makefile", ".gitignore", "application.yml",
                "settings.xml", "pyproject.toml"
        })
        void classifiesConfig(String file) {
            assertThat(fileClassifier.classify(file, languageDetector.detect(file)))
                    .as("%s 应归为配置", file)
                    .isEqualTo(FileCategory.CONFIG);
        }

        @ParameterizedTest(name = "文档 {0}")
        @ValueSource(strings = {"README.md", "CHANGELOG.md", "LICENSE", "NOTICE", "docs/guide.rst", "NOTES.txt"})
        void classifiesDocumentation(String file) {
            String name = file.contains("/") ? file.substring(file.lastIndexOf('/') + 1) : file;
            assertThat(fileClassifier.classify(name, languageDetector.detect(name)))
                    .as("%s 应归为文档", file)
                    .isEqualTo(FileCategory.DOC);
        }

        @Test
        @DisplayName("pom.xml 归为配置而非源码——即便它被识别为 xml 语言")
        void xmlConfigIsNotSource() {
            assertThat(languageDetector.detect("pom.xml")).isEqualTo("xml");
            assertThat(fileClassifier.classify("pom.xml", "xml")).isEqualTo(FileCategory.CONFIG);
        }

        @Test
        @DisplayName("Markdown 归为文档而非源码")
        void markdownIsNotSource() {
            assertThat(fileClassifier.classify("README.md", "markdown")).isEqualTo(FileCategory.DOC);
        }

        @Test
        @DisplayName("无法识别的一律归为 OTHER，不猜测")
        void unknownFallsBackToOther() {
            assertThat(fileClassifier.classify("data.unknownext", null)).isEqualTo(FileCategory.OTHER);
            assertThat(fileClassifier.classify("LICENSE.bin", null)).isEqualTo(FileCategory.OTHER);
        }
    }
}
