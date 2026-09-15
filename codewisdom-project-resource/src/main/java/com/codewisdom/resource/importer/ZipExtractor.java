package com.codewisdom.resource.importer;

import com.codewisdom.common.api.ErrorCode;
import com.codewisdom.common.exception.BizException;
import com.codewisdom.resource.config.ImportProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 安全解压 ZIP。
 *
 * <p>压缩包是<b>不可信输入</b>，直接 {@code unzip} 是典型漏洞来源。这里挡三类攻击：
 *
 * <ol>
 *   <li><b>Zip Slip（路径穿越）</b>：条目名写成 {@code ../../../etc/cron.d/x} 或绝对路径，
 *       解压时覆盖目标目录之外的任意文件。防线是「规范化后必须仍在目标目录内」，
 *       并且显式拒绝绝对路径与盘符前缀——只做字符串 {@code contains("..")} 是不够的，
 *       {@code a/../../b} 这类写法绕得过朴素检查。</li>
 *   <li><b>Zip Bomb（压缩炸弹）</b>：几十 KB 的包解出几十 GB。三重限制：
 *       条目数、解压总量、压缩比。且<b>边写边统计</b>，不是读完成员目录再判断——
 *       声明的 size 可以撒谎，实际写入的字节不会。</li>
 *   <li><b>畸形条目名</b>：含 NUL、控制字符、Windows 保留字符的名字，先拒掉，
 *       避免后续在文件系统层出各种方言问题。</li>
 * </ol>
 *
 * <p>用流式 {@link ZipInputStream} 而非 {@link java.util.zip.ZipFile}：
 * 前者能边解边熔断，后者必须先把整个包读进内存/落盘。
 *
 * <p>关于符号链接：本类只用 {@link OutputStream} 写文件内容，<b>不会创建链接</b>，
 * 因此压缩包里的符号链接项退化为普通文件，不构成逃逸风险。
 */
@Component
public class ZipExtractor {

    private static final Logger log = LoggerFactory.getLogger(ZipExtractor.class);

    private static final int BUFFER_SIZE = 8192;

    /** Windows 上的保留字符，出现在条目名里一律拒绝。 */
    private static final String ILLEGAL_NAME_CHARS = "<>:\"|?*";

    /** ZIP 本地文件头魔数：普通条目 / 空压缩包 / 分卷。 */
    private static final byte[] MAGIC_LOCAL = {0x50, 0x4B, 0x03, 0x04};
    private static final byte[] MAGIC_EMPTY = {0x50, 0x4B, 0x05, 0x06};
    private static final byte[] MAGIC_SPANNED = {0x50, 0x4B, 0x07, 0x08};

    private final ImportProperties properties;

    public ZipExtractor(ImportProperties properties) {
        this.properties = properties;
    }

    /**
     * 解压结果。
     *
     * @param entries     解出的条目数（含目录）
     * @param totalBytes  解出的总字节数
     * @param targetDir   解压目标目录
     */
    public record ExtractResult(int entries, long totalBytes, Path targetDir) {
    }

    /**
     * 解压到指定目录。目标目录会被清空重建。
     *
     * @param archive   压缩包文件
     * @param targetDir 解压目标目录
     * @throws BizException 路径穿越、超限、或压缩包损坏
     */
    public ExtractResult extract(Path archive, Path targetDir) {
        long archiveSize = sizeOf(archive);
        long maxBytes = maxUncompressedBytes(archiveSize);

        prepareTarget(targetDir);

        Path normalizedTarget = targetDir.toAbsolutePath().normalize();
        int entries = 0;
        long totalBytes = 0;

        try (InputStream fileIn = Files.newInputStream(archive);
             BufferedInputStream buffered = new BufferedInputStream(fileIn)) {

            buffered.mark(MAGIC_LOCAL.length);
            byte[] header = buffered.readNBytes(MAGIC_LOCAL.length);
            buffered.reset();
            requireZipSignature(header);

            ZipInputStream zipIn = new ZipInputStream(buffered);
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                if (++entries > properties.getMaxArchiveEntries()) {
                    throw BizException.of(ErrorCode.ARCHIVE_INVALID,
                            "压缩包条目数超过上限 " + properties.getMaxArchiveEntries());
                }

                String name = entry.getName();
                Path destination = resolveSafely(normalizedTarget, name);

                if (entry.isDirectory()) {
                    Files.createDirectories(destination);
                    continue;
                }

                Files.createDirectories(destination.getParent());
                totalBytes += writeEntry(zipIn, destination, totalBytes, maxBytes, name);
            }
        } catch (BizException e) {
            throw e;
        } catch (IOException e) {
            throw BizException.of(ErrorCode.ARCHIVE_INVALID,
                    "压缩包读取失败或已损坏: " + e.getMessage(), e);
        }

        log.info("解压完成 archive={} entries={} bytes={}", archive.getFileName(), entries, totalBytes);
        return new ExtractResult(entries, totalBytes, targetDir);
    }

    /**
     * 只校验条目名，不解压、不落盘。
     *
     * <p>存在的意义是<b>把「恶意包」挡在业务记录之前</b>：条目名合法性可以从压缩包
     * 本身廉价地判定，没必要先建项目、再解压、再失败、最后留下一条失败记录。
     * 攻击者若能靠畸形包批量制造垃圾项目，本身就是一种资源耗尽手段。
     *
     * <p>注意：压缩炸弹<b>无法</b>在这一步判定——条目声明的 size 可以撒谎，
     * 只有真实写入的字节才可信，因此炸弹防护仍在 {@link #extract} 中边写边熔断，
     * 那类输入会留下一条 FAILED 记录（可审计，是期望行为）。
     *
     * @param zipStream 压缩包输入流
     * @return 条目数
     */
    public int validateEntryNames(InputStream zipStream) {
        int entries = 0;
        try {
            // 先验魔数：非 ZIP 输入会被 ZipInputStream 静默当成「空压缩包」，
            // 结果是用户传了垃圾文件却"导入成功、0 个文件"，必须在入口挡掉。
            BufferedInputStream buffered = zipStream instanceof BufferedInputStream existing
                    ? existing : new BufferedInputStream(zipStream);
            buffered.mark(MAGIC_LOCAL.length);
            byte[] header = buffered.readNBytes(MAGIC_LOCAL.length);
            buffered.reset();
            requireZipSignature(header);

            try (ZipInputStream zipIn = new ZipInputStream(buffered)) {
                ZipEntry entry;
                while ((entry = zipIn.getNextEntry()) != null) {
                    if (++entries > properties.getMaxArchiveEntries()) {
                        throw BizException.of(ErrorCode.ARCHIVE_INVALID,
                                "压缩包条目数超过上限 " + properties.getMaxArchiveEntries());
                    }
                    checkEntryName(entry.getName());
                }
            }
        } catch (BizException e) {
            throw e;
        } catch (IOException e) {
            throw BizException.of(ErrorCode.ARCHIVE_INVALID,
                    "压缩包读取失败或已损坏: " + e.getMessage(), e);
        }
        return entries;
    }

    /** 校验 ZIP 魔数。 */
    private static void requireZipSignature(byte[] header) {
        if (header.length < 4 || header[0] != 0x50 || header[1] != 0x4B) {
            throw BizException.of(ErrorCode.ARCHIVE_INVALID, "不是有效的 ZIP 压缩包");
        }
        if (startsWith(header, MAGIC_LOCAL) || startsWith(header, MAGIC_EMPTY)
                || startsWith(header, MAGIC_SPANNED)) {
            return;
        }
        throw BizException.of(ErrorCode.ARCHIVE_INVALID, "不是有效的 ZIP 压缩包");
    }

    private static boolean startsWith(byte[] value, byte[] prefix) {
        for (int i = 0; i < prefix.length; i++) {
            if (value[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 校验单个条目名是否安全。
     *
     * <p>核心是「规范化之后仍在目标目录内」——这一步同时挡住了
     * {@code ../} 相对穿越与 {@code /etc/...} 绝对路径两类写法。
     * 只做字符串 {@code contains("..")} 不够：{@code a/../../b} 这类写法绕得过朴素检查。
     */
    private void checkEntryName(String rawName) {
        String name = rawName.replace('\\', '/');

        if (name.isBlank()) {
            throw BizException.of(ErrorCode.ARCHIVE_INVALID, "压缩包包含空条目名");
        }
        if (name.indexOf('\0') >= 0) {
            throw BizException.of(ErrorCode.ARCHIVE_INVALID, "压缩包条目名含非法字符");
        }
        // 绝对路径与盘符：Windows 上 "C:/x" 与 "/x" 都会脱离目标目录
        if (name.startsWith("/") || name.matches("^[A-Za-z]:.*")) {
            throw BizException.of(ErrorCode.ARCHIVE_PATH_TRAVERSAL,
                    "压缩包包含绝对路径条目: " + rawName);
        }
        if (name.indexOf(':') >= 0 || containsIllegalChar(name)) {
            throw BizException.of(ErrorCode.ARCHIVE_INVALID,
                    "压缩包条目名含非法字符: " + rawName);
        }
        // 规范化后不得逃出目标目录（目标目录用占位根，只判断相对层数）
        Path probe = Path.of("/__root__").resolve(name).normalize();
        if (!probe.startsWith("/__root__")) {
            throw BizException.of(ErrorCode.ARCHIVE_PATH_TRAVERSAL,
                    "压缩包包含路径穿越条目: " + rawName);
        }
        if (probe.equals(Path.of("/__root__"))) {
            throw BizException.of(ErrorCode.ARCHIVE_INVALID, "压缩包条目名指向根目录: " + rawName);
        }
    }

    /**
     * 把条目名解析为安全的目标路径。
     *
     * <p>先做与预检相同的名字校验，再解析到真实目标目录下——两道都保留，
     * 因为预检可被绕过（直接调 {@code extract} 的场景）时这里是最后一道。
     */
    private Path resolveSafely(Path normalizedTarget, String rawName) {
        checkEntryName(rawName);

        String name = rawName.replace('\\', '/');
        Path resolved = normalizedTarget.resolve(name).normalize();
        if (!resolved.startsWith(normalizedTarget)) {
            throw BizException.of(ErrorCode.ARCHIVE_PATH_TRAVERSAL,
                    "压缩包包含路径穿越条目: " + rawName);
        }
        if (resolved.equals(normalizedTarget)) {
            throw BizException.of(ErrorCode.ARCHIVE_INVALID, "压缩包条目名指向根目录: " + rawName);
        }
        return resolved;
    }

    /**
     * 写单个条目，并<b>按实际写出的字节</b>做熔断。
     *
     * <p>不用 {@link ZipEntry#getSize()} 判断：那是压缩包自己声明的大小，可以撒谎。
     */
    private long writeEntry(ZipInputStream zipIn,
                            Path destination,
                            long writtenSoFar,
                            long maxBytes,
                            String entryName) throws IOException {
        long written = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        try (OutputStream out = Files.newOutputStream(destination,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            int read;
            while ((read = zipIn.read(buffer)) != -1) {
                written += read;
                if (writtenSoFar + written > maxBytes) {
                    throw BizException.of(ErrorCode.ARCHIVE_INVALID,
                            "解压后体积超过上限（" + maxBytes + " 字节），疑似压缩炸弹，"
                                    + "触发条目: " + entryName);
                }
                out.write(buffer, 0, read);
            }
        }
        return written;
    }

    /** 压缩比与总量上限取两者中更严的一个。 */
    private long maxUncompressedBytes(long archiveSize) {
        long configured = properties.getMaxUncompressedSize();
        if (archiveSize <= 0) {
            return configured;
        }
        long ratioLimit = archiveSize * properties.getMaxCompressionRatio();
        return Math.min(configured, ratioLimit);
    }

    private static void prepareTarget(Path targetDir) {
        try {
            if (Files.exists(targetDir)) {
                Files.walk(targetDir)
                        .sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                p.toFile().setWritable(true);
                                Files.deleteIfExists(p);
                            } catch (IOException e) {
                                throw new java.io.UncheckedIOException(e);
                            }
                        });
            }
            Files.createDirectories(targetDir);
        } catch (IOException e) {
            throw BizException.of(ErrorCode.ARCHIVE_INVALID, "解压目标目录准备失败: " + e.getMessage(), e);
        }
    }

    private static boolean containsIllegalChar(String name) {
        for (char c : ILLEGAL_NAME_CHARS.toCharArray()) {
            if (name.indexOf(c) >= 0) {
                return true;
            }
        }
        // 控制字符
        for (char c : name.toCharArray()) {
            if (c < 0x20) {
                return true;
            }
        }
        return false;
    }

    private static long sizeOf(Path archive) {
        try {
            return Files.size(archive);
        } catch (IOException e) {
            throw BizException.of(ErrorCode.ARCHIVE_INVALID, "压缩包不可读: " + e.getMessage(), e);
        }
    }
}
