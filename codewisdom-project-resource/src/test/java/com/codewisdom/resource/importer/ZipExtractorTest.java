package com.codewisdom.resource.importer;

import com.codewisdom.common.api.ErrorCode;
import com.codewisdom.common.exception.BizException;
import com.codewisdom.resource.config.ImportProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 安全解压测试。
 *
 * <p>压缩包是<b>不可信输入</b>，本测试逐条覆盖真实攻击手法：
 * 相对穿越、深层穿越、绝对路径、Windows 盘符、畸形条目名、压缩炸弹。
 *
 * <p>关键断言不只是「抛异常」，还要断言<b>目标目录之外确实没有生成文件</b>——
 * 抛异常但已经写出去，等于没防住。
 */
@DisplayName("ZIP 安全解压 ZipExtractor")
class ZipExtractorTest {

    @TempDir
    Path tempDir;

    private ImportProperties properties;
    private ZipExtractor extractor;
    private Path extractDir;

    @BeforeEach
    void setUp() {
        properties = new ImportProperties();
        properties.setMaxArchiveEntries(1000);
        properties.setMaxUncompressedSize(10L * 1024 * 1024);
        properties.setMaxCompressionRatio(200);
        extractor = new ZipExtractor(properties);
        extractDir = tempDir.resolve("extract");
    }

    @Nested
    @DisplayName("正常解压")
    class HappyPath {

        @Test
        @DisplayName("解出文件与目录，内容与压缩前一致")
        void extractsNormalArchive() throws Exception {
            Path zip = zipWith(
                    new String[]{"pom.xml", "<project/>"},
                    new String[]{"src/main/java/demo/App.java", "package demo; class App {}"},
                    new String[]{"README.md", "# 中文标题"});

            ZipExtractor.ExtractResult result = extractor.extract(zip, extractDir);

            assertThat(result.entries()).isEqualTo(3);
            assertThat(Files.readString(extractDir.resolve("pom.xml"))).isEqualTo("<project/>");
            assertThat(Files.readString(extractDir.resolve("src/main/java/demo/App.java")))
                    .contains("class App");
            assertThat(Files.readString(extractDir.resolve("README.md")))
                    .as("UTF-8 中文内容不得被破坏")
                    .isEqualTo("# 中文标题");
        }

        @Test
        @DisplayName("重复解压到同一目录时先清空，不留上次的残留文件")
        void clearsTargetDirectoryBeforeExtract() throws Exception {
            Path zip = zipWith(new String[]{"a.txt", "hello"});
            extractor.extract(zip, extractDir);
            Files.writeString(extractDir.resolve("stale.txt"), "from previous run");

            extractor.extract(zip, extractDir);

            assertThat(extractDir.resolve("stale.txt")).doesNotExist();
            assertThat(extractDir.resolve("a.txt")).exists();
        }

        @Test
        @DisplayName("空压缩包解出 0 个条目，不抛异常")
        void handlesEmptyArchive() throws Exception {
            Path zip = tempDir.resolve("empty.zip");
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
                // 不写任何条目
            }

            ZipExtractor.ExtractResult result = extractor.extract(zip, extractDir);

            assertThat(result.entries()).isZero();
            assertThat(result.totalBytes()).isZero();
        }
    }

    @Nested
    @DisplayName("Zip Slip 路径穿越防护")
    class ZipSlip {

        @Test
        @DisplayName("拒绝 ../ 相对穿越，且目标目录外不生成文件")
        void rejectsRelativeTraversal() throws Exception {
            Path zip = zipWith(new String[]{"../escaped.txt", "pwned"});

            assertThatThrownBy(() -> extractor.extract(zip, extractDir))
                    .isInstanceOfSatisfying(BizException.class,
                            e -> assertThat(e.getErrorCode())
                                    .isEqualTo(ErrorCode.ARCHIVE_PATH_TRAVERSAL));

            assertThat(tempDir.resolve("escaped.txt"))
                    .as("逃逸文件绝不能落到目标目录之外")
                    .doesNotExist();
        }

        @Test
        @DisplayName("拒绝深层穿越 a/b/../../../escaped.txt——朴素的 contains(\"..\") 之外还要靠规范化")
        void rejectsDeepTraversal() throws Exception {
            Path zip = zipWith(new String[]{"a/b/../../../escaped.txt", "pwned"});

            assertThatThrownBy(() -> extractor.extract(zip, extractDir))
                    .isInstanceOf(BizException.class);

            assertThat(tempDir.resolve("escaped.txt")).doesNotExist();
            assertThat(tempDir.getParent().resolve("escaped.txt")).doesNotExist();
        }

        @Test
        @DisplayName("拒绝绝对路径条目 /tmp/escaped.txt")
        void rejectsAbsolutePath() throws Exception {
            Path zip = zipWith(new String[]{"tmp/escaped.txt", "pwned"});
            // 先验证正常相对路径可用，再验证前缀为 / 的被拒
            extractor.extract(zip, extractDir);
            assertThat(extractDir.resolve("tmp/escaped.txt")).exists();

            Path malicious = zipWith(new String[]{"/escaped-abs.txt", "pwned"});
            assertThatThrownBy(() -> extractor.extract(malicious, extractDir))
                    .isInstanceOfSatisfying(BizException.class,
                            e -> assertThat(e.getErrorCode())
                                    .isEqualTo(ErrorCode.ARCHIVE_PATH_TRAVERSAL));
            assertThat(tempDir.resolve("escaped-abs.txt")).doesNotExist();
        }

        @Test
        @DisplayName("拒绝 Windows 盘符条目 C:/escaped.txt")
        void rejectsWindowsDrivePath() throws Exception {
            Path zip = zipWith(new String[]{"C:/escaped.txt", "pwned"});

            assertThatThrownBy(() -> extractor.extract(zip, extractDir))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("拒绝反斜杠写法的穿越 ..\\escaped.txt")
        void rejectsBackslashTraversal() throws Exception {
            Path zip = zipWith(new String[]{"..\\escaped.txt", "pwned"});

            assertThatThrownBy(() -> extractor.extract(zip, extractDir))
                    .isInstanceOfSatisfying(BizException.class,
                            e -> assertThat(e.getErrorCode())
                                    .isEqualTo(ErrorCode.ARCHIVE_PATH_TRAVERSAL));
            assertThat(tempDir.resolve("escaped.txt")).doesNotExist();
        }

        @Test
        @DisplayName("拒绝指向根目录的空条目名")
        void rejectsEntryPointingAtRoot() throws Exception {
            Path zip = zipWith(new String[]{"sub/..", "x"});

            assertThatThrownBy(() -> extractor.extract(zip, extractDir))
                    .isInstanceOf(BizException.class);
        }
    }

    @Nested
    @DisplayName("畸形条目名防护")
    class MalformedNames {

        @Test
        @DisplayName("拒绝含 NUL 的条目名")
        void rejectsNullByteInName() throws Exception {
            Path zip = zipWith(new String[]{"evil\u0000.txt", "pwned"});

            assertThatThrownBy(() -> extractor.extract(zip, extractDir))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("拒绝含 Windows 保留字符的条目名")
        void rejectsIllegalCharacters() throws Exception {
            Path zip = zipWith(new String[]{"a<b>c.txt", "x"});

            assertThatThrownBy(() -> extractor.extract(zip, extractDir))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("非法字符");
        }
    }

    @Nested
    @DisplayName("Zip Bomb 压缩炸弹防护")
    class ZipBomb {

        @Test
        @DisplayName("解压总量超过上限时中止，不写满磁盘")
        void rejectsOversizedPayload() throws Exception {
            properties.setMaxUncompressedSize(100 * 1024);
            Path zip = zipWithZeros("bomb.bin", 200 * 1024);

            assertThatThrownBy(() -> extractor.extract(zip, extractDir))
                    .isInstanceOfSatisfying(BizException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ARCHIVE_INVALID))
                    .hasMessageContaining("压缩炸弹");
        }

        @Test
        @DisplayName("压缩比超过上限时中止——只限总量挡不住小包大解")
        void rejectsAbnormalCompressionRatio() throws Exception {
            properties.setMaxUncompressedSize(Long.MAX_VALUE / 2);
            properties.setMaxCompressionRatio(5);
            // 1MB 的全零内容压缩后只有几 KB，压缩比远超 5
            Path zip = zipWithZeros("bomb.bin", 1024 * 1024);

            assertThatThrownBy(() -> extractor.extract(zip, extractDir))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("压缩炸弹");
        }

        @Test
        @DisplayName("条目数超过上限时中止")
        void rejectsTooManyEntries() throws Exception {
            properties.setMaxArchiveEntries(3);
            Path zip = zipWith(
                    new String[]{"a.txt", "1"},
                    new String[]{"b.txt", "2"},
                    new String[]{"c.txt", "3"},
                    new String[]{"d.txt", "4"});

            assertThatThrownBy(() -> extractor.extract(zip, extractDir))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("条目数超过上限");
        }

        @Test
        @DisplayName("正常压缩比的内容不受影响（阈值不能误伤）")
        void allowsNormalCompression() throws Exception {
            Path zip = zipWith(new String[]{"normal.java", "class A { /* 普通内容 */ }"});

            assertThat(extractor.extract(zip, extractDir).entries()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("损坏输入")
    class CorruptedInput {

        @Test
        @DisplayName("非 ZIP 文件被拒绝，错误码为 ARCHIVE_INVALID")
        void rejectsNonZipFile() throws Exception {
            Path notZip = tempDir.resolve("fake.zip");
            Files.writeString(notZip, "this is definitely not a zip archive");

            assertThatThrownBy(() -> extractor.extract(notZip, extractDir))
                    .isInstanceOfSatisfying(BizException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ARCHIVE_INVALID));
        }

        @Test
        @DisplayName("文件不存在时给出明确错误")
        void rejectsMissingArchive() {
            Path missing = tempDir.resolve("nope.zip");

            assertThatThrownBy(() -> extractor.extract(missing, extractDir))
                    .isInstanceOfSatisfying(BizException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ARCHIVE_INVALID))
                    .hasMessageContaining("不可读");
        }
    }

    // ---- helpers ----

    /** 按「条目名, 内容」成对构造 ZIP。允许写入任意条目名，用于模拟恶意包。 */
    private Path zipWith(String[]... entries) throws IOException {
        Path zip = tempDir.resolve("test-" + System.nanoTime() + ".zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (String[] entry : entries) {
                out.putNextEntry(new ZipEntry(entry[0]));
                out.write(entry[1].getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return zip;
    }

    /** 构造高压缩比条目（全零内容），用于压缩炸弹测试。 */
    private Path zipWithZeros(String name, int size) throws IOException {
        Path zip = tempDir.resolve("bomb-" + System.nanoTime() + ".zip");
        try (OutputStream fileOut = Files.newOutputStream(zip);
             ZipOutputStream out = new ZipOutputStream(fileOut)) {
            out.putNextEntry(new ZipEntry(name));
            byte[] zeros = new byte[8192];
            int remaining = size;
            while (remaining > 0) {
                int chunk = Math.min(zeros.length, remaining);
                out.write(zeros, 0, chunk);
                remaining -= chunk;
            }
            out.closeEntry();
        }
        return zip;
    }
}
