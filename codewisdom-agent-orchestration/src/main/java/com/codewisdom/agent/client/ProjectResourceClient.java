package com.codewisdom.agent.client;

import com.codewisdom.agent.config.DownstreamProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class ProjectResourceClient {

    private static final List<String> DOC_CANDIDATES = List.of(
            "README.md", "README", "readme.md", "Readme.md",
            "docs/README.md", "docs/spec.md", "docs/architecture.md",
            "CHANGELOG.md", "pom.xml", "package.json", "build.gradle",
            "pyproject.toml", "go.mod");

    private static final int MAX_DOC_CHARS = 9000;
    private static final int MAX_FILE_SNIPPET = 2800;
    private static final int MAX_JAVA_SAMPLES = 2;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public ProjectResourceClient(DownstreamProperties properties, ObjectMapper objectMapper) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.getProjectResourceBaseUrl())
                .build();
        this.objectMapper = objectMapper;
    }

    public Optional<String> fetchProjectName(long projectId, String authorizationHeader) {
        JsonNode data = getJson("/projects/" + projectId, authorizationHeader).path("data");
        if (data.isMissingNode()) {
            return Optional.empty();
        }
        String name = data.path("name").asText("");
        return name.isBlank() ? Optional.empty() : Optional.of(name);
    }

    public ProjectBrief fetchBrief(long projectId, String authorizationHeader) {
        JsonNode stats = getJson("/projects/" + projectId + "/stats", authorizationHeader);
        JsonNode data = stats.path("data");
        Map<String, Integer> byLang = readIntMap(data.path("byLanguage"));
        Map<String, Integer> byCat = readIntMap(data.path("byCategory"));
        return new ProjectBrief(
                data.path("fileCount").asInt(),
                data.path("totalSize").asLong(),
                data.path("maxDepth").asInt(),
                byLang,
                byCat);
    }

    /** 优先读取 README / 文档 / 构建描述，供 AI 判断项目定位。 */
    public String fetchDocumentSnippets(long projectId, String authorizationHeader) {
        StringBuilder sb = new StringBuilder();
        for (String path : DOC_CANDIDATES) {
            if (sb.length() >= MAX_DOC_CHARS) {
                break;
            }
            tryReadTextFile(projectId, path, authorizationHeader, MAX_FILE_SNIPPET).ifPresent(text -> {
                sb.append("【").append(path).append("】\n").append(text).append("\n\n");
            });
        }
        return sb.toString().trim();
    }

    /** 从文件树抽样少量源码，辅助判断业务类型。 */
    public String fetchSourceSnippets(long projectId, String authorizationHeader) {
        JsonNode tree = getJson("/projects/" + projectId + "/tree?depth=5", authorizationHeader).path("data");
        List<String> javaPaths = new ArrayList<>();
        collectJavaPaths(tree, javaPaths, MAX_JAVA_SAMPLES);

        StringBuilder sb = new StringBuilder();
        for (String path : javaPaths) {
            tryReadTextFile(projectId, path, authorizationHeader, 1500).ifPresent(text ->
                    sb.append("【").append(path).append("】\n").append(text).append("\n\n"));
        }
        return sb.toString().trim();
    }

    private Optional<String> tryReadTextFile(
            long projectId, String path, String authorizationHeader, int maxChars) {
        JsonNode data = getJson(
                "/projects/" + projectId + "/files/content?path=" + urlEncode(path),
                authorizationHeader).path("data");
        if (data.isMissingNode() || data.path("binary").asBoolean(false)) {
            return Optional.empty();
        }
        String content = data.path("content").asText("");
        if (content.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(truncate(content, maxChars));
    }

    private static void collectJavaPaths(JsonNode node, List<String> out, int max) {
        if (out.size() >= max || node == null || node.isMissingNode()) {
            return;
        }
        String type = node.path("type").asText("");
        String path = node.path("path").asText("");
        if ("FILE".equals(type) && path.endsWith(".java")
                && !path.contains("/test/") && !path.contains("Test.java")) {
            out.add(path);
        }
        JsonNode children = node.path("children");
        if (children.isArray()) {
            for (JsonNode child : children) {
                collectJavaPaths(child, out, max);
            }
        }
    }

    private JsonNode getJson(String path, String authorizationHeader) {
        RestClient.RequestHeadersSpec<?> spec = restClient.get().uri(path);
        if (authorizationHeader != null && !authorizationHeader.isBlank()) {
            spec = spec.header("Authorization", authorizationHeader);
        }
        String body = spec.retrieve().body(String.class);
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.path("code").asInt() != 0) {
                return objectMapper.createObjectNode();
            }
            return root;
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private static Map<String, Integer> readIntMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        return objectMapperFields(node);
    }

    private static Map<String, Integer> objectMapperFields(JsonNode node) {
        var it = node.fields();
        Map<String, Integer> map = new java.util.LinkedHashMap<>();
        while (it.hasNext()) {
            var e = it.next();
            map.put(e.getKey(), e.getValue().asInt());
        }
        return map;
    }

    private static String urlEncode(String path) {
        return java.net.URLEncoder.encode(path, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String truncate(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "\n...(已截断)";
    }

    public record ProjectBrief(
            int fileCount,
            long totalSize,
            int maxDepth,
            Map<String, Integer> byLanguage,
            Map<String, Integer> byCategory
    ) {
        public String toPromptBlock() {
            String langs = byLanguage.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(8)
                    .map(e -> e.getKey() + "(" + e.getValue() + ")")
                    .collect(Collectors.joining(", "));
            String cats = byCategory.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining(", "));
            return """
                    - 文件数：%d
                    - 总体积：%d 字节
                    - 目录最大深度：%d
                    - 语言分布：%s
                    - 分类统计：%s
                    """.formatted(fileCount, totalSize, maxDepth, langs, cats);
        }
    }
}
