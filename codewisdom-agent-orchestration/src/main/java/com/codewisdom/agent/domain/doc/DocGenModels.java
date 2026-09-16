package com.codewisdom.agent.domain.doc;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 文档注释生成所需的输入/输出模型。
 *
 * <p>与 {@code code-analysis} 的解析结果形状对齐，但在本模块内独立定义，
 * 避免 agent-orchestration 反向依赖解析服务（跨服务调用留到 Feign/MQ 阶段）。
 */
public final class DocGenModels {

    private DocGenModels() {
    }

    /** 单个类型的注释生成请求。 */
    public record TypeDocRequest(
            String qualifiedName,
            String kind,
            String classCommentHint,
            List<MethodDocRequest> methods
    ) {
        public TypeDocRequest {
            Objects.requireNonNull(qualifiedName, "qualifiedName");
            Objects.requireNonNull(kind, "kind");
            methods = List.copyOf(methods == null ? List.of() : methods);
            classCommentHint = classCommentHint == null ? "" : classCommentHint;
        }
    }

    /** 单个方法/构造器的注释生成请求。 */
    public record MethodDocRequest(
            String name,
            String returnType,
            boolean constructor,
            List<ParameterDocRequest> parameters
    ) {
        public MethodDocRequest {
            Objects.requireNonNull(name, "name");
            parameters = List.copyOf(parameters == null ? List.of() : parameters);
        }

        public boolean needsReturnTag() {
            if (constructor) {
                return false;
            }
            return returnType != null && !returnType.isBlank() && !"void".equalsIgnoreCase(returnType.trim());
        }
    }

    public record ParameterDocRequest(String name, String type) {
        public ParameterDocRequest {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
        }
    }

    /** 生成结果：{@code skipped=true} 表示开关关闭，未触达 LLM。 */
    public record DocGenResult(
            boolean skipped,
            String typeComment,
            Map<String, String> methodComments
    ) {
        public DocGenResult {
            methodComments = Map.copyOf(methodComments == null ? Map.of() : methodComments);
            typeComment = typeComment == null ? "" : typeComment;
        }

        public static DocGenResult notGenerated() {
            return new DocGenResult(true, "", Map.of());
        }
    }
}
