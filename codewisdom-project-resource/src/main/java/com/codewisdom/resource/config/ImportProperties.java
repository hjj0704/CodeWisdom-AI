package com.codewisdom.resource.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 项目导入相关配置。
 *
 * <p>刻意做成可配置而非硬编码：本机实测 {@code github.com} 不可达而 {@code gitee.com} 正常，
 * 不同网络环境下可导入的托管源不同，硬编码会让部署环境失去适配能力。
 */
@Data
@Component
@ConfigurationProperties(prefix = "codewisdom.import")
public class ImportProperties {

    /**
     * 允许的仓库托管域名白名单。匹配规则：完全相等，或以 {@code .域名} 结尾。
     *
     * <p>留空表示不做域名限制，此时仅依赖「禁止解析到内网地址」这一层防护。
     * 不建议留空——白名单是防 SSRF 的第一道闸门。
     */
    private List<String> allowedHosts = List.of(
            "github.com", "gitee.com", "gitlab.com", "bitbucket.org", "codeberg.org");

    /**
     * 是否允许仓库地址解析到内网/回环地址。
     *
     * <p><b>生产环境必须保持 false</b>，否则可被用于探测内网服务（SSRF）。
     * 仅在本地集成测试（file:// 克隆、内网 Git 服务）时按需开启。
     */
    private boolean allowPrivateNetwork = false;

    /** 单文件最大字节数，超过则不纳入文件树（默认 2 MiB）。 */
    private long maxFileSize = 2L * 1024 * 1024;

    /** 单项目最大文件节点数，超过则中止导入，防止异常仓库拖垮服务（默认 20000）。 */
    private int maxFiles = 20_000;

    /** 克隆超时秒数（默认 180）。 */
    private int cloneTimeoutSeconds = 180;

    /** 克隆深度；0 或负数表示完整克隆（默认 1，只取最新快照）。 */
    private int cloneDepth = 1;

    /** 仓库工作区存放根目录。 */
    private String workspaceRoot = "./data/repos";

    // ---- 压缩包解压限制（防 Zip Slip 之外的 Zip Bomb）----

    /** 单个压缩包允许的最大条目数（默认 50000）。 */
    private int maxArchiveEntries = 50_000;

    /** 解压后允许的最大总字节数（默认 512 MiB）。 */
    private long maxUncompressedSize = 512L * 1024 * 1024;

    /**
     * 允许的最大压缩比（解压后总字节 / 压缩包字节，默认 200）。
     *
     * <p>防的是「几十 KB 的包解出几十 GB」这类炸弹——只限总量不够，
     * 因为炸弹可以在触发总量上限前就把磁盘写满。
     */
    private int maxCompressionRatio = 200;
}
