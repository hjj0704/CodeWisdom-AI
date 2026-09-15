package com.codewisdom.analysis.parser;

import com.codewisdom.common.api.ErrorCode;
import com.codewisdom.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.treesitter.TSLanguage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("T-301 语言注册表")
class LanguageRegistryTest {

    private final LanguageRegistry registry = new LanguageRegistry();

    @Test
    @DisplayName("支持 java 与 python，与文件树的语言标识对齐")
    void supportsJavaAndPython() {
        assertThat(registry.supportedLanguages()).containsExactlyInAnyOrder("java", "python");
        assertThat(registry.supports("java")).isTrue();
        assertThat(registry.supports("python")).isTrue();
        assertThat(registry.supports("go")).isFalse();
        assertThat(registry.supports(null)).isFalse();
    }

    @Test
    @DisplayName("grammar 被缓存并复用——每次都 new 会白吃内存")
    void cachesGrammarInstance() {
        TSLanguage first = registry.get("java");
        TSLanguage second = registry.get("java");

        assertThat(first).isSameAs(second);
    }

    @Test
    @DisplayName("语言标识大小写不敏感")
    void isCaseInsensitive() {
        assertThat(registry.get("JAVA")).isSameAs(registry.get("java"));
        assertThat(registry.get(" Java ")).isSameAs(registry.get("java"));
    }

    @Test
    @DisplayName("不支持的语言抛出 UNSUPPORTED_LANGUAGE，并提示当前支持范围")
    void rejectsUnsupportedLanguage() {
        assertThatThrownBy(() -> registry.get("go"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UNSUPPORTED_LANGUAGE))
                .hasMessageContaining("暂不支持的语言")
                .hasMessageContaining("java");
    }

    @Test
    @DisplayName("空语言标识被拒绝")
    void rejectsBlankLanguage() {
        assertThatThrownBy(() -> registry.get("  "))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> registry.get(null))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("grammar ABI 落在 Tree-Sitter 运行时支持区间内")
    void grammarAbiIsCompatible() {
        for (String language : registry.supportedLanguages()) {
            assertThat(registry.get(language).abiVersion())
                    .as("%s 的 grammar ABI 应被运行时接受", language)
                    .isBetween(org.treesitter.TSParser.TREE_SITTER_MIN_COMPATIBLE_LANGUAGE_VERSION,
                            org.treesitter.TSParser.TREE_SITTER_LANGUAGE_VERSION);
        }
    }
}
