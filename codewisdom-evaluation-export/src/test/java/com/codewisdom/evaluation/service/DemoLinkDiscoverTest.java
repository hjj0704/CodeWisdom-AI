package com.codewisdom.evaluation.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("在线演示链接探测")
class DemoLinkDiscoverTest {

    @Test
    @DisplayName("README 中的在线演示链接可被识别")
    void findsMarkdownDemoLink() {
        String readme = """
                # Demo Project
                访问 [在线演示](https://demo-app.vercel.app/) 体验功能。
                源码：https://github.com/acme/demo
                """;

        List<DemoLinkDiscoverService.Candidate> links =
                DemoLinkDiscoverService.extractFromContent(readme, "README.md");

        assertThat(links).isNotEmpty();
        assertThat(links.get(0).url()).isEqualTo("https://demo-app.vercel.app/");
        assertThat(links.get(0).label()).contains("在线演示");
    }

    @Test
    @DisplayName("忽略与仓库地址相同的链接")
    void ignoresSourceRepoUrl() {
        String readme = """
                [在线演示](https://gitee.com/han/demo)
                """;
        List<DemoLinkDiscoverService.Candidate> links = DemoLinkDiscoverService.extractFromContent(
                readme, "README.md", "https://gitee.com/han/demo.git");

        assertThat(links).isEmpty();
    }

    @Test
    @DisplayName("package.json homepage 可作为演示入口")
    void readsHomepageFromPackageJson() {
        String pkg = """
                {
                  "name": "demo-ui",
                  "homepage": "https://demo-ui.netlify.app"
                }
                """;

        List<DemoLinkDiscoverService.Candidate> links =
                DemoLinkDiscoverService.extractFromContent(pkg, "package.json");

        assertThat(links).extracting(DemoLinkDiscoverService.Candidate::url)
                .contains("https://demo-ui.netlify.app");
    }
}
