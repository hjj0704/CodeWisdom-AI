package com.codewisdom.agent.service.doc;

import com.codewisdom.agent.client.llm.LlmClient;
import com.codewisdom.agent.client.llm.LlmModels.CallResult;
import com.codewisdom.agent.client.llm.LlmModels.Request;
import com.codewisdom.agent.domain.doc.DocGenModels.DocGenResult;
import com.codewisdom.agent.domain.doc.DocGenModels.MethodDocRequest;
import com.codewisdom.agent.domain.doc.DocGenModels.ParameterDocRequest;
import com.codewisdom.agent.domain.doc.DocGenModels.TypeDocRequest;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 类/方法/参数/返回值 Javadoc 生成（T-602）。
 *
 * <p>{@code docGenEnabled=false} 时<b>不调用</b> {@link LlmClient}，供开关验收与 T-602 测试断言。
 * LLM 返回 JSON，经校验后输出合法 Javadoc 块。
 */
public class DocCommentGenerator {

    private static final Logger log = LoggerFactory.getLogger(DocCommentGenerator.class);

    private static final Pattern JAVADOC_BLOCK = Pattern.compile("/\\*\\*[\\s\\S]*?\\*/");

    private static final String SYSTEM_PROMPT = """
            你是 Java Javadoc 生成助手。只输出 JSON，不要 Markdown 围栏。
            格式：{"typeComment":"/** ... */","methods":[{"name":"方法名","comment":"/** ... */"}]}
            要求：typeComment 描述类型；每个 method 的 comment 必须含 @param（每个参数一条）；
            非 void 非构造器方法必须含 @return；使用中文简述；注释必须是合法 Javadoc 块。
            """;

    private final LlmClient llmClient;
    private final DocGenProperties properties;
    private final ObjectMapper objectMapper;

    public DocCommentGenerator(LlmClient llmClient, DocGenProperties properties) {
        this(llmClient, properties, new ObjectMapper());
    }

    DocCommentGenerator(LlmClient llmClient, DocGenProperties properties, ObjectMapper objectMapper) {
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public DocGenResult generate(TypeDocRequest request) {
        Objects.requireNonNull(request, "request");

        if (!properties.enabled()) {
            return DocGenResult.notGenerated();
        }

        String userPrompt = buildUserPrompt(request);
        CallResult callResult = llmClient.call(new Request(userPrompt, SYSTEM_PROMPT));

        if (callResult.failed()) {
            log.warn("文档注释生成失败，返回空结果: {}", callResult.failureReason());
            return new DocGenResult(false, "", Map.of());
        }

        try {
            LlmDocPayload payload = objectMapper.readValue(callResult.response().content(), LlmDocPayload.class);
            validateTypeComment(payload.typeComment());
            Map<String, String> methodComments = new LinkedHashMap<>();
            for (MethodCommentPayload method : payload.methods()) {
                MethodDocRequest spec = findMethod(request, method.name());
                if (spec == null) {
                    continue;
                }
                validateMethodComment(method.comment(), spec);
                methodComments.put(method.name(), method.comment());
            }
            return new DocGenResult(false, payload.typeComment(), methodComments);
        } catch (Exception ex) {
            log.warn("无法解析 LLM 文档注释 JSON: {}", ex.getMessage());
            return new DocGenResult(false, "", Map.of());
        }
    }

    private static MethodDocRequest findMethod(TypeDocRequest request, String name) {
        return request.methods().stream()
                .filter(m -> m.name().equals(name))
                .findFirst()
                .orElse(null);
    }

    private static String buildUserPrompt(TypeDocRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("为以下 Java 类型生成 Javadoc JSON。\n");
        sb.append("类型: ").append(request.kind()).append(' ').append(request.qualifiedName()).append('\n');
        if (!request.classCommentHint().isBlank()) {
            sb.append("背景: ").append(request.classCommentHint()).append('\n');
        }
        sb.append("方法列表:\n");
        for (MethodDocRequest method : request.methods()) {
            sb.append("- ").append(method.constructor() ? "构造器" : "方法")
                    .append(' ').append(method.name()).append('(');
            for (int i = 0; i < method.parameters().size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                ParameterDocRequest p = method.parameters().get(i);
                sb.append(p.type()).append(' ').append(p.name());
            }
            sb.append(')');
            if (!method.constructor() && method.returnType() != null) {
                sb.append(" -> ").append(method.returnType());
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    static void validateTypeComment(String comment) {
        Objects.requireNonNull(comment, "typeComment");
        if (!JAVADOC_BLOCK.matcher(comment.trim()).matches()) {
            throw new IllegalArgumentException("类型注释不是合法 Javadoc 块");
        }
    }

    static void validateMethodComment(String comment, MethodDocRequest spec) {
        Objects.requireNonNull(comment, "comment");
        if (!JAVADOC_BLOCK.matcher(comment.trim()).matches()) {
            throw new IllegalArgumentException("方法 " + spec.name() + " 注释不是合法 Javadoc 块");
        }
        for (ParameterDocRequest param : spec.parameters()) {
            if (!comment.contains("@param " + param.name())) {
                throw new IllegalArgumentException("方法 " + spec.name() + " 缺少 @param " + param.name());
            }
        }
        if (spec.needsReturnTag() && !comment.contains("@return")) {
            throw new IllegalArgumentException("方法 " + spec.name() + " 缺少 @return");
        }
    }

    /** 与 {@code codewisdom.doc-gen.enabled} 对齐。 */
    public record DocGenProperties(boolean enabled) {

        public static DocGenProperties on() {
            return new DocGenProperties(true);
        }

        public static DocGenProperties off() {
            return new DocGenProperties(false);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LlmDocPayload(String typeComment, List<MethodCommentPayload> methods) {
        LlmDocPayload {
            methods = methods == null ? List.of() : methods;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MethodCommentPayload(String name, String comment) {
    }
}
