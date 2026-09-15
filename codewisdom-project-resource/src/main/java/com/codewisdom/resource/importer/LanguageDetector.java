package com.codewisdom.resource.importer;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

/**
 * 扩展名 → 语言标识。
 *
 * <p>语言标识统一使用小写、无空格的形式，供阶段 3 选择对应的 Tree-Sitter grammar，
 * 以及阶段 5 按语言分派审计规则。无法识别时返回 {@code null}（表示「非源码」）。
 */
@Component
public class LanguageDetector {

    private static final Map<String, String> EXTENSION_TO_LANGUAGE = Map.ofEntries(
            // JVM
            Map.entry("java", "java"),
            Map.entry("kt", "kotlin"),
            Map.entry("kts", "kotlin"),
            Map.entry("scala", "scala"),
            Map.entry("groovy", "groovy"),
            // 前端
            Map.entry("js", "javascript"),
            Map.entry("mjs", "javascript"),
            Map.entry("cjs", "javascript"),
            Map.entry("jsx", "javascript"),
            Map.entry("ts", "typescript"),
            Map.entry("tsx", "typescript"),
            Map.entry("vue", "vue"),
            Map.entry("svelte", "svelte"),
            Map.entry("css", "css"),
            Map.entry("scss", "scss"),
            Map.entry("less", "less"),
            Map.entry("html", "html"),
            // 脚本
            Map.entry("py", "python"),
            Map.entry("pyi", "python"),
            Map.entry("rb", "ruby"),
            Map.entry("php", "php"),
            Map.entry("sh", "shell"),
            Map.entry("bash", "shell"),
            Map.entry("zsh", "shell"),
            Map.entry("ps1", "powershell"),
            Map.entry("lua", "lua"),
            Map.entry("pl", "perl"),
            // 系统级
            Map.entry("go", "go"),
            Map.entry("rs", "rust"),
            Map.entry("c", "c"),
            Map.entry("h", "c"),
            Map.entry("cpp", "cpp"),
            Map.entry("cc", "cpp"),
            Map.entry("cxx", "cpp"),
            Map.entry("hpp", "cpp"),
            Map.entry("cs", "csharp"),
            Map.entry("swift", "swift"),
            Map.entry("m", "objective-c"),
            Map.entry("dart", "dart"),
            Map.entry("ex", "elixir"),
            Map.entry("exs", "elixir"),
            Map.entry("erl", "erlang"),
            // 数据 / 查询
            Map.entry("sql", "sql"),
            Map.entry("xml", "xml"),
            Map.entry("json", "json"),
            Map.entry("yml", "yaml"),
            Map.entry("yaml", "yaml"),
            Map.entry("toml", "toml"),
            Map.entry("proto", "protobuf"),
            Map.entry("graphql", "graphql"),
            Map.entry("gql", "graphql"),
            // 文档
            Map.entry("md", "markdown"),
            Map.entry("rst", "rst"),
            Map.entry("adoc", "asciidoc")
    );

    /**
     * 识别语言。
     *
     * @param fileName 文件名（大小写不敏感）
     * @return 语言标识；非源码或无扩展名时返回 {@code null}
     */
    public String detect(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        String extension = PathFilter.extensionOf(fileName.toLowerCase(Locale.ROOT));
        return EXTENSION_TO_LANGUAGE.get(extension);
    }
}
