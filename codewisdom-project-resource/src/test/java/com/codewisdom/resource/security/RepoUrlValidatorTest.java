package com.codewisdom.resource.security;

import com.codewisdom.common.api.ErrorCode;
import com.codewisdom.common.exception.BizException;
import com.codewisdom.resource.config.ImportProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 仓库地址校验（SSRF 防护）测试。
 *
 * <p>DNS 被替换为固定映射表，因此本测试<b>完全离线且确定</b>，
 * 但覆盖的正是攻击者会尝试的路径：协议走私、凭据夹带、端口绕行、
 * 白名单后缀欺骗、DNS 指向内网。
 */
@DisplayName("仓库地址校验 RepoUrlValidator")
class RepoUrlValidatorTest {

    private static final Map<String, String[]> DNS = Map.of(
            "github.com", new String[]{"140.82.121.4"},
            "gitee.com", new String[]{"212.64.62.174"},
            "gist.github.com", new String[]{"140.82.121.4"},
            // 白名单域名被解析到内网——DNS 劫持 / 内网 DNS 污染场景
            "evil-dns.github.com", new String[]{"10.0.0.5"},
            // 直接以 IP 充当主机名
            "127.0.0.1", new String[]{"127.0.0.1"},
            "169.254.169.254", new String[]{"169.254.169.254"},
            "192.168.1.10", new String[]{"192.168.1.10"}
    );

    private ImportProperties properties;
    private RepoUrlValidator validator;

    @BeforeEach
    void setUp() {
        properties = new ImportProperties();
        properties.setAllowedHosts(List.of("github.com", "gitee.com"));
        properties.setAllowPrivateNetwork(false);
        validator = new StubDnsValidator(properties, DNS);
    }

    @Nested
    @DisplayName("放行")
    class Allowed {

        @Test
        @DisplayName("白名单域名 + https 通过，并返回规范化 URI")
        void acceptsAllowedHttpsUrl() {
            URI uri = validator.validate("  https://gitee.com/y_project/RuoYi.git  ");

            assertThat(uri.getHost()).isEqualTo("gitee.com");
            assertThat(uri.getPath()).isEqualTo("/y_project/RuoYi.git");
        }

        @Test
        @DisplayName("白名单域名的子域也放行（gist.github.com）")
        void acceptsSubdomainOfAllowedHost() {
            assertThatCode(() -> validator.validate("https://gist.github.com/user/abc.git"))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("拒绝 —— 协议与格式")
    class SchemeRejection {

        @ParameterizedTest(name = "拒绝非 http(s) 协议: {0}")
        @ValueSource(strings = {
                "file:///etc/passwd",
                "ssh://git@github.com/user/repo.git",
                "git://github.com/user/repo.git",
                "ftp://github.com/user/repo.git",
                "jar:file:///tmp/a.jar!/"
        })
        void rejectsNonHttpSchemes(String url) {
            assertThatThrownBy(() -> validator.validate(url))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("http/https");
        }

        @ParameterizedTest(name = "拒绝空地址: [{0}]")
        @ValueSource(strings = {"", "   "})
        void rejectsBlank(String url) {
            assertThatThrownBy(() -> validator.validate(url))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("拒绝格式非法的地址")
        void rejectsMalformedUrl() {
            assertThatThrownBy(() -> validator.validate("ht tp://github.com/a b"))
                    .isInstanceOf(BizException.class);
        }
    }

    @Nested
    @DisplayName("拒绝 —— 凭据与端口")
    class CredentialAndPortRejection {

        @Test
        @DisplayName("拒绝携带用户名密码的地址，避免凭据进日志与数据库")
        void rejectsUserInfo() {
            assertThatThrownBy(() -> validator.validate("https://user:pass@github.com/a/b.git"))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("用户名密码");
        }

        @Test
        @DisplayName("拒绝非标准端口，内网探测常借道高位端口")
        void rejectsNonStandardPort() {
            assertThatThrownBy(() -> validator.validate("https://github.com:8443/a/b.git"))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("端口");
        }

        @Test
        @DisplayName("显式 443 端口放行")
        void acceptsExplicitHttpsPort() {
            assertThatCode(() -> validator.validate("https://github.com:443/a/b.git"))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("拒绝 —— 主机名与白名单")
    class HostRejection {

        @Test
        @DisplayName("拒绝 localhost")
        void rejectsLocalhost() {
            assertThatThrownBy(() -> validator.validate("http://localhost/a/b.git"))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("拒绝 .internal 主机（云元数据常见域名）")
        void rejectsInternalHost() {
            assertThatThrownBy(() -> validator.validate("http://metadata.google.internal/x"))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("拒绝不在白名单的域名")
        void rejectsHostNotAllowed() {
            assertThatThrownBy(() -> validator.validate("https://example.com/a/b.git"))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("白名单");
        }

        @Test
        @DisplayName("拒绝白名单后缀欺骗：evilgithub.com 不得被当作 github.com")
        void rejectsSuffixSpoofingWithoutDot() {
            assertThatThrownBy(() -> validator.validate("https://evilgithub.com/a/b.git"))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("白名单");
        }

        @Test
        @DisplayName("拒绝白名单后缀欺骗：github.com.evil.com 不得被当作 github.com")
        void rejectsSuffixSpoofingWithTrailingDomain() {
            assertThatThrownBy(() -> validator.validate("https://github.com.evil.com/a/b.git"))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("白名单");
        }
    }

    @Nested
    @DisplayName("拒绝 —— 解析到内网（关键防线）")
    class PrivateAddressRejection {

        @Test
        @DisplayName("白名单域名被解析到内网地址时同样拒绝（防 DNS 劫持）")
        void rejectsAllowedHostResolvingToPrivateIp() {
            assertThatThrownBy(() -> validator.validate("https://evil-dns.github.com/a/b.git"))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("非公网地址");
        }

        @ParameterizedTest(name = "拒绝解析到 {0} 的地址")
        @ValueSource(strings = {"127.0.0.1", "169.254.169.254", "192.168.1.10"})
        void rejectsPrivateIpLiterals(String host) {
            // 清空白名单，逼出「IP 层校验」这条独立防线
            properties.setAllowedHosts(List.of());
            assertThatThrownBy(() -> validator.validate("https://" + host + "/a/b.git"))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("非公网地址");
        }

        @Test
        @DisplayName("域名无法解析时给出明确错误，而不是放行")
        void rejectsUnresolvableHost() {
            properties.setAllowedHosts(List.of());
            assertThatThrownBy(() -> validator.validate("https://nowhere.example/a/b.git"))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("无法解析");
        }
    }

    @Nested
    @DisplayName("内网地址判定 isPublic")
    class AddressClassification {

        @ParameterizedTest(name = "非公网: {0}")
        @ValueSource(strings = {
                "0.0.0.0", "10.1.2.3", "172.16.0.1", "192.168.0.1", "127.0.0.1",
                "169.254.169.254", "100.64.0.1", "100.127.255.254",
                "192.0.0.1", "198.18.0.1", "198.19.255.255", "224.0.0.1"
        })
        void classifiesNonPublic(String ip) throws Exception {
            assertThat(RepoUrlValidator.isPublic(InetAddress.getByName(ip)))
                    .as("%s 应被判为非公网", ip)
                    .isFalse();
        }

        @ParameterizedTest(name = "公网: {0}")
        @ValueSource(strings = {"8.8.8.8", "140.82.121.4", "1.1.1.1", "100.63.255.255", "100.128.0.1"})
        void classifiesPublic(String ip) throws Exception {
            assertThat(RepoUrlValidator.isPublic(InetAddress.getByName(ip)))
                    .as("%s 应被判为公网", ip)
                    .isTrue();
        }

        @Test
        @DisplayName("IPv6 唯一本地地址 fc00::/7 判为非公网")
        void classifiesUniqueLocalIpv6() throws Exception {
            assertThat(RepoUrlValidator.isPublic(InetAddress.getByName("fd00::1"))).isFalse();
            assertThat(RepoUrlValidator.isPublic(InetAddress.getByName("2001:4860:4860::8888"))).isTrue();
        }
    }

    @Nested
    @DisplayName("配置开关")
    class Configuration {

        @Test
        @DisplayName("开启 allowPrivateNetwork 后放行内网地址（仅供本地集成测试）")
        void allowPrivateNetworkBypassesAddressCheck() {
            properties.setAllowPrivateNetwork(true);
            // 该开关只关掉「地址层」校验，域名白名单仍然独立生效，需一并放开
            properties.setAllowedHosts(List.of());
            assertThatCode(() -> validator.validate("https://127.0.0.1:80/a/b.git"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("allowPrivateNetwork 不会绕过域名白名单——两层防护彼此独立")
        void allowPrivateNetworkDoesNotBypassAllowList() {
            properties.setAllowPrivateNetwork(true);
            assertThatThrownBy(() -> validator.validate("https://example.com/a/b.git"))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("白名单");
        }

        @Test
        @DisplayName("白名单留空时只靠 IP 层防护，公网域名仍放行")
        void emptyAllowListFallsBackToIpCheckOnly() {
            properties.setAllowedHosts(List.of());
            assertThatCode(() -> validator.validate("https://github.com/a/b.git"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("错误码固定为 IMPORT_URL_REJECTED")
        void usesDedicatedErrorCode() {
            assertThatThrownBy(() -> validator.validate("file:///etc/passwd"))
                    .isInstanceOfSatisfying(BizException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.IMPORT_URL_REJECTED));
        }
    }

    /** 用固定映射表替换 DNS，保证离线可测。 */
    private static final class StubDnsValidator extends RepoUrlValidator {

        private final Map<String, String[]> dns;

        private StubDnsValidator(ImportProperties properties, Map<String, String[]> dns) {
            super(properties);
            this.dns = new HashMap<>(dns);
        }

        @Override
        protected InetAddress[] resolve(String host) throws UnknownHostException {
            String[] ips = dns.get(host);
            if (ips == null) {
                throw new UnknownHostException(host);
            }
            InetAddress[] addresses = new InetAddress[ips.length];
            for (int i = 0; i < ips.length; i++) {
                addresses[i] = InetAddress.getByName(ips[i]);
            }
            return addresses;
        }
    }
}
