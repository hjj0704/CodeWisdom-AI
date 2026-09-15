package com.codewisdom.resource.security;

import com.codewisdom.common.api.ErrorCode;
import com.codewisdom.common.exception.BizException;
import com.codewisdom.resource.config.ImportProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;

/**
 * 仓库地址校验器 —— 防 SSRF 的第一道闸门。
 *
 * <p>依次拒绝：
 * <ol>
 *   <li>空地址 / 非法 URI；</li>
 *   <li>非 http(s) 协议（{@code file:} / {@code ssh:} / {@code git:} 等一律拒——{@code file:} 可直接读服务器本地文件）；</li>
 *   <li>携带用户名密码的地址（避免凭据被写进日志与数据库）；</li>
 *   <li>非标准端口（80/443 之外）—— 内网探测常借道高位端口；</li>
 *   <li>localhost / {@code *.local} / 云元数据地址等敏感主机名；</li>
 *   <li>不在白名单内的域名；</li>
 *   <li><b>解析到内网、回环、链路本地、CGNAT 等非公网地址</b>（覆盖「白名单域名被解析到内网」这类 DNS 劫持）。</li>
 * </ol>
 *
 * <p>第 7 条是关键：仅做域名白名单不足以防 SSRF，必须校验解析后的真实 IP。
 */
@Component
public class RepoUrlValidator {

    private static final Logger log = LoggerFactory.getLogger(RepoUrlValidator.class);

    /** 云厂商元数据服务地址，攻击者常用它窃取实例凭据。 */
    private static final List<String> SENSITIVE_HOSTS = List.of(
            "localhost", "localhost.localdomain", "metadata.google.internal",
            "metadata.goog", "instance-data");

    private static final List<Integer> ALLOWED_PORTS = List.of(-1, 80, 443);

    private final ImportProperties properties;

    public RepoUrlValidator(ImportProperties properties) {
        this.properties = properties;
    }

    /**
     * 校验仓库地址，不通过则抛 {@link BizException}。
     *
     * @param rawUrl 用户输入的仓库地址
     * @return 规范化后的 URI（已 trim）
     */
    public URI validate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw BizException.of(ErrorCode.IMPORT_URL_REJECTED, "仓库地址不能为空");
        }

        URI uri = parse(rawUrl.trim());

        String scheme = lower(uri.getScheme());
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw BizException.of(ErrorCode.IMPORT_URL_REJECTED,
                    "仅支持 http/https 协议的仓库地址，当前为: " + uri.getScheme());
        }

        if (uri.getUserInfo() != null) {
            throw BizException.of(ErrorCode.IMPORT_URL_REJECTED,
                    "仓库地址不得携带用户名密码，请使用平台凭据管理");
        }

        if (!ALLOWED_PORTS.contains(uri.getPort())) {
            throw BizException.of(ErrorCode.IMPORT_URL_REJECTED,
                    "仓库地址端口不被允许: " + uri.getPort());
        }

        String host = lower(uri.getHost());
        if (host == null || host.isBlank()) {
            throw BizException.of(ErrorCode.IMPORT_URL_REJECTED, "仓库地址缺少主机名");
        }

        rejectSensitiveHost(host);
        rejectHostNotAllowed(host);

        if (!properties.isAllowPrivateNetwork()) {
            rejectPrivateAddress(host);
        } else {
            log.warn("已开启 allowPrivateNetwork，跳过内网地址校验，切勿在生产环境开启。host={}", host);
        }

        return uri;
    }

    private URI parse(String url) {
        try {
            return new URI(url);
        } catch (URISyntaxException e) {
            throw BizException.of(ErrorCode.IMPORT_URL_REJECTED, "仓库地址格式不正确");
        }
    }

    private void rejectSensitiveHost(String host) {
        boolean sensitive = SENSITIVE_HOSTS.contains(host)
                || host.endsWith(".local")
                || host.endsWith(".internal")
                || host.endsWith(".localhost");
        if (sensitive) {
            throw BizException.of(ErrorCode.IMPORT_URL_REJECTED, "不允许访问该主机: " + host);
        }
    }

    private void rejectHostNotAllowed(String host) {
        List<String> allowed = properties.getAllowedHosts();
        if (allowed == null || allowed.isEmpty()) {
            return;
        }
        boolean ok = allowed.stream()
                .map(RepoUrlValidator::lower)
                .anyMatch(rule -> host.equals(rule) || host.endsWith("." + rule));
        if (!ok) {
            throw BizException.of(ErrorCode.IMPORT_URL_REJECTED,
                    "仓库托管域名不在白名单内: " + host);
        }
    }

    private void rejectPrivateAddress(String host) {
        InetAddress[] addresses;
        try {
            addresses = resolve(host);
        } catch (UnknownHostException e) {
            throw BizException.of(ErrorCode.IMPORT_URL_REJECTED, "域名无法解析: " + host);
        }
        for (InetAddress address : addresses) {
            if (!isPublic(address)) {
                throw BizException.of(ErrorCode.IMPORT_URL_REJECTED,
                        "仓库地址解析到非公网地址，已拒绝: " + host + " -> " + address.getHostAddress());
            }
        }
    }

    /**
     * 解析主机名。
     *
     * <p>抽成可覆写方法，是为了让单元测试能注入确定的解析结果——
     * 否则每条用例都要真实 DNS，离线环境下会变成假红。
     */
    protected InetAddress[] resolve(String host) throws UnknownHostException {
        return InetAddress.getAllByName(host);
    }

    /** 判断是否为可公网访问的地址；除标准私有段外，额外覆盖 CGNAT 与 IPv6 唯一本地地址。 */
    static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            // 0.0.0.0/8
            if (first == 0) {
                return false;
            }
            // 100.64.0.0/10 运营商级 NAT
            if (first == 100 && second >= 64 && second <= 127) {
                return false;
            }
            // 192.0.0.0/24 IETF 保留
            if (first == 192 && second == 0 && (bytes[2] & 0xFF) == 0) {
                return false;
            }
            // 198.18.0.0/15 基准测试
            if (first == 198 && (second == 18 || second == 19)) {
                return false;
            }
            return true;
        }
        if (bytes.length == 16) {
            // fc00::/7 唯一本地地址
            int first = bytes[0] & 0xFF;
            return (first & 0xFE) != 0xFC;
        }
        return false;
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
