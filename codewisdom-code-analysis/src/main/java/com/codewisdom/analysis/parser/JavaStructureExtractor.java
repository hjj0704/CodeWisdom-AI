package com.codewisdom.analysis.parser;

import com.codewisdom.analysis.domain.MethodDeclaration;
import com.codewisdom.analysis.domain.MethodKind;
import com.codewisdom.analysis.domain.MethodParameter;
import com.codewisdom.analysis.domain.TypeDeclaration;
import com.codewisdom.analysis.domain.TypeKind;
import org.springframework.stereotype.Component;
import org.treesitter.TSNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 从 Java 语法树中抽取类型声明。
 *
 * <p>节点类型与字段名来自对 tree-sitter-java 的实测（不是猜的）：
 * <pre>
 * class_declaration              name / modifiers / superclass / interfaces / body
 * interface_declaration          name / modifiers / interfaces / body
 * enum_declaration               name / modifiers / interfaces / body
 * annotation_type_declaration    name / modifiers / body
 * record_declaration             name / modifiers / parameters / body
 * </pre>
 *
 * <p><b>嵌套类型是本类的主要复杂度来源</b>：tree-sitter 里嵌套类同样是
 * {@code class_declaration}，与顶层类扁平并列，靠父子关系区分。因此遍历时用栈
 * 维护「当前所处的外层类型」，才能算出限定名并标记 {@code enclosingType}。
 *
 * <p>只做 Java。Python 的类/函数结构差异较大，留待需要时另写抽取器，
 * 不硬套同一套字段名。
 */
@Component
public class JavaStructureExtractor {

    private static final Set<String> MODIFIER_KEYWORDS = Set.of(
            "public", "protected", "private", "static", "final", "abstract",
            "sealed", "non-sealed", "strictfp", "default", "native", "synchronized");

    private static final Set<String> ANNOTATION_NODE_TYPES = Set.of(
            "marker_annotation", "annotation");

    /**
     * 抽取单个源文件的类型与方法结构。
     *
     * @param handle 已解析的句柄；调用方负责关闭
     */
    public SourceStructure extract(ParseHandle handle) {
        String packageName = extractPackageName(handle);
        List<TypeDeclaration> types = new ArrayList<>();
        List<MethodDeclaration> methods = new ArrayList<>();
        visit(handle.root(), handle, packageName, new ArrayDeque<>(), types, methods);
        return new SourceStructure(packageName, types, methods);
    }

    /** 包名。默认包返回空串。 */
    public String extractPackageName(ParseHandle handle) {
        List<TSNode> packages = handle.findChildren(handle.root(), "package_declaration");
        if (packages.isEmpty()) {
            return "";
        }
        // package_declaration 形如 (package_declaration (scoped_identifier))，取去除关键字后的文本
        String raw = handle.text(packages.get(0)).trim();
        return raw.replaceFirst("^package\\s+", "").replace(";", "").trim();
    }

    /**
     * 深度优先遍历。
     *
     * <p>遇到类型声明节点时记录一条声明，并把限定名压栈后再递归其子节点，
     * 这样嵌在里面的类型与方法才能拿到正确的外层限定名。
     */
    private void visit(TSNode node,
                       ParseHandle handle,
                       String packageName,
                       Deque<String> enclosingQualifiedNames,
                       List<TypeDeclaration> types,
                       List<MethodDeclaration> methods) {
        String nodeType = node.getType();

        TypeKind typeKind = TypeKind.fromNodeType(nodeType);
        if (typeKind != null) {
            TypeDeclaration declaration = toDeclaration(node, typeKind, handle, packageName,
                    enclosingQualifiedNames.peek());
            types.add(declaration);
            enclosingQualifiedNames.push(declaration.qualifiedName());
            try {
                visitChildren(node, handle, packageName, enclosingQualifiedNames, types, methods);
            } finally {
                enclosingQualifiedNames.pop();
            }
            return;
        }

        MethodKind methodKind = methodKindOf(nodeType);
        if (methodKind != null) {
            methods.add(toMethod(node, methodKind, handle, enclosingQualifiedNames.peek()));
            // 方法体里可能有局部类与匿名类，仍要往下走
            visitChildren(node, handle, packageName, enclosingQualifiedNames, types, methods);
            return;
        }

        visitChildren(node, handle, packageName, enclosingQualifiedNames, types, methods);
    }

    private void visitChildren(TSNode node,
                               ParseHandle handle,
                               String packageName,
                               Deque<String> enclosingQualifiedNames,
                               List<TypeDeclaration> types,
                               List<MethodDeclaration> methods) {
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            visit(node.getChild(i), handle, packageName, enclosingQualifiedNames, types, methods);
        }
    }

    private static MethodKind methodKindOf(String nodeType) {
        return switch (nodeType) {
            case "method_declaration" -> MethodKind.METHOD;
            case "constructor_declaration" -> MethodKind.CONSTRUCTOR;
            default -> null;
        };
    }

    private TypeDeclaration toDeclaration(TSNode node,
                                          TypeKind kind,
                                          ParseHandle handle,
                                          String packageName,
                                          String enclosingQualifiedName) {
        String name = handle.nameOf(node);
        if (name == null) {
            name = "<anonymous>";
        }

        return new TypeDeclaration(
                name,
                qualifiedNameOf(packageName, enclosingQualifiedName, name),
                packageName,
                kind,
                extractModifiers(node, handle),
                extractAnnotations(node, handle),
                extractSuperClass(node, handle),
                extractInterfaces(node, handle),
                enclosingQualifiedName,
                handle.startLine(node),
                handle.endLine(node));
    }

    private static String qualifiedNameOf(String packageName, String enclosing, String name) {
        if (enclosing != null && !enclosing.isBlank()) {
            return enclosing + "." + name;
        }
        return packageName == null || packageName.isBlank() ? name : packageName + "." + name;
    }

    // ---- 方法与构造器 ----

    private MethodDeclaration toMethod(TSNode node,
                                       MethodKind kind,
                                       ParseHandle handle,
                                       String ownerQualifiedName) {
        return new MethodDeclaration(
                handle.nameOf(node),
                ownerQualifiedName,
                kind,
                extractReturnType(node, handle, kind),
                extractTypeParameters(node, handle),
                extractParameters(node, handle),
                extractModifiers(node, handle),
                extractAnnotations(node, handle),
                extractThrownTypes(node, handle),
                hasBody(node),
                handle.startLine(node),
                handle.endLine(node));
    }

    /** 返回类型。构造器没有返回类型，返回 null。 */
    private String extractReturnType(TSNode node, ParseHandle handle, MethodKind kind) {
        if (kind == MethodKind.CONSTRUCTOR) {
            return null;
        }
        TSNode type = node.getChildByFieldName("type");
        return isAbsent(type) ? null : handle.text(type).trim();
    }

    /** 泛型类型参数，如 {@code <R>} → {@code ["R"]}。 */
    private List<String> extractTypeParameters(TSNode node, ParseHandle handle) {
        TSNode typeParameters = handle.findChildren(node, "type_parameters").stream()
                .findFirst().orElse(null);
        if (isAbsent(typeParameters)) {
            return List.of();
        }
        String raw = handle.text(typeParameters).trim();
        raw = raw.replaceAll("^<", "").replaceAll(">$", "");
        if (raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * 参数列表。
     *
     * <p>注意 {@code formal_parameters} 的子节点<b>包含括号与逗号标点</b>，
     * 只能按节点类型挑出 {@code formal_parameter} 与 {@code spread_parameter}，
     * 直接遍历子节点会把 {@code (}、{@code ,}、{@code )} 也当成参数。
     */
    private List<MethodParameter> extractParameters(TSNode node, ParseHandle handle) {
        TSNode parameters = node.getChildByFieldName("parameters");
        if (isAbsent(parameters)) {
            return List.of();
        }
        List<MethodParameter> result = new ArrayList<>();
        int count = parameters.getChildCount();
        for (int i = 0; i < count; i++) {
            TSNode child = parameters.getChild(i);
            switch (child.getType()) {
                case "formal_parameter" -> result.add(toParameter(child, handle));
                case "spread_parameter" -> result.add(toVarArgsParameter(child, handle));
                default -> {
                    // 标点，跳过
                }
            }
        }
        return result;
    }

    private MethodParameter toParameter(TSNode node, ParseHandle handle) {
        TSNode type = node.getChildByFieldName("type");
        TSNode name = node.getChildByFieldName("name");
        return new MethodParameter(
                isAbsent(name) ? "" : handle.text(name).trim(),
                isAbsent(type) ? "" : handle.text(type).trim(),
                false,
                extractParameterAnnotations(node, handle));
    }

    /**
     * 可变参数 {@code int... nums}。
     *
     * <p>{@code spread_parameter} 的 {@code type}/{@code name} 字段都是空的
     * （实测），只能从节点文本里按 {@code ...} 拆。
     */
    private MethodParameter toVarArgsParameter(TSNode node, ParseHandle handle) {
        String text = handle.text(node).trim();
        String withoutAnnotations = stripAnnotations(text);
        int ellipsis = withoutAnnotations.indexOf("...");
        if (ellipsis < 0) {
            return new MethodParameter(withoutAnnotations, "", true, extractParameterAnnotations(node, handle));
        }
        return new MethodParameter(
                withoutAnnotations.substring(ellipsis + 3).trim(),
                withoutAnnotations.substring(0, ellipsis).trim(),
                true,
                extractParameterAnnotations(node, handle));
    }

    /**
     * 参数上的注解。
     *
     * <p>与类型声明一样，参数注解也被包在 {@code modifiers} 节点里
     * （实测 {@code (@NotNull String a)} 的结构为
     * {@code formal_parameter → modifiers → marker_annotation}），
     * 因此不能只看参数的直接子节点。
     */
    private List<String> extractParameterAnnotations(TSNode node, ParseHandle handle) {
        List<String> annotations = new ArrayList<>();
        TSNode modifiers = handle.findChildren(node, "modifiers").stream().findFirst().orElse(null);
        TSNode container = isAbsent(modifiers) ? node : modifiers;

        int count = container.getChildCount();
        for (int i = 0; i < count; i++) {
            TSNode child = container.getChild(i);
            if (ANNOTATION_NODE_TYPES.contains(child.getType())) {
                String name = handle.nameOf(child);
                if (name != null) {
                    annotations.add(stripGenerics(name));
                }
            }
        }
        return annotations;
    }

    /**
     * 声明的受检异常。
     *
     * <p>{@code throws} 子句节点<b>没有字段名</b>（实测 {@code getFieldNameForChild} 返回 null），
     * 只能按节点类型 {@code throws} 定位。
     */
    private List<String> extractThrownTypes(TSNode node, ParseHandle handle) {
        TSNode throwsNode = handle.findChildren(node, "throws").stream().findFirst().orElse(null);
        if (isAbsent(throwsNode)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        collectTypeNames(throwsNode, handle, names);
        return names;
    }

    /**
     * 是否有方法体。
     *
     * <p><b>必须用 {@code isNull()} 判断</b>：该绑定对不存在的字段返回的是
     * 「空节点」而不是 Java {@code null}，用 {@code != null} 会把抽象方法与
     * 接口方法误判成有方法体。
     */
    private boolean hasBody(TSNode node) {
        TSNode body = node.getChildByFieldName("body");
        return !isAbsent(body);
    }

    /** 该绑定里「字段不存在」表现为空节点，故 Java null 与 isNull() 都要判。 */
    private static boolean isAbsent(TSNode node) {
        return node == null || node.isNull();
    }

    /** 去掉参数前缀上的注解，留下类型与名字。{@code @NotNull String name} → {@code String name}。 */
    private static String stripAnnotations(String text) {
        return text.replaceAll("@[\\w.]+(\\([^)]*\\))?\\s*", "").trim();
    }

    /** 修饰符关键字。注解不算修饰符，另由 {@link #extractAnnotations} 处理。 */
    private Set<String> extractModifiers(TSNode node, ParseHandle handle) {
        Set<String> modifiers = new LinkedHashSet<>();
        TSNode modifiersNode = handle.findChildren(node, "modifiers").stream().findFirst().orElse(null);
        if (modifiersNode == null) {
            return modifiers;
        }
        int count = modifiersNode.getChildCount();
        for (int i = 0; i < count; i++) {
            String childType = modifiersNode.getChild(i).getType();
            if (MODIFIER_KEYWORDS.contains(childType)) {
                modifiers.add(childType);
            }
        }
        return modifiers;
    }

    /** 类型上标注的注解简单名（不含 {@code @}）。 */
    private List<String> extractAnnotations(TSNode node, ParseHandle handle) {
        List<String> annotations = new ArrayList<>();
        TSNode modifiersNode = handle.findChildren(node, "modifiers").stream().findFirst().orElse(null);
        if (modifiersNode == null) {
            return annotations;
        }
        int count = modifiersNode.getChildCount();
        for (int i = 0; i < count; i++) {
            TSNode child = modifiersNode.getChild(i);
            if (ANNOTATION_NODE_TYPES.contains(child.getType())) {
                String annotationName = handle.nameOf(child);
                if (annotationName != null) {
                    annotations.add(stripGenerics(annotationName));
                }
            }
        }
        return annotations;
    }

    /**
     * 父类。原样保留类型文本（可能带泛型），不做简单名截断——调用方需要时再处理。
     *
     * <p>注意 {@code superclass} 节点的文本<b>包含 {@code extends} 关键字</b>
     * （实测为 {@code "extends BaseService"}），必须剥掉，否则父类名没法直接比对。
     */
    private String extractSuperClass(TSNode node, ParseHandle handle) {
        TSNode superClass = node.getChildByFieldName("superclass");
        if (superClass == null || superClass.isNull()) {
            return null;
        }
        String raw = handle.text(superClass).trim();
        String stripped = raw.replaceFirst("^extends\\s+", "").trim();
        return stripped.isEmpty() ? null : stripped;
    }

    /** 实现的接口 / 继承的父接口。 */
    private List<String> extractInterfaces(TSNode node, ParseHandle handle) {
        TSNode interfaces = node.getChildByFieldName("interfaces");
        if (interfaces == null || interfaces.isNull()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        collectTypeNames(interfaces, handle, names);
        return names;
    }

    /** 递归收集类型名，遇到 type_list 继续下钻，遇到类型标识符即取文本。 */
    private void collectTypeNames(TSNode node, ParseHandle handle, List<String> out) {
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            TSNode child = node.getChild(i);
            String type = child.getType();
            if ("type_identifier".equals(type) || "scoped_type_identifier".equals(type)) {
                out.add(stripGenerics(handle.text(child).trim()));
            } else if (child.getChildCount() > 0) {
                collectTypeNames(child, handle, out);
            }
        }
    }

    /** 去掉泛型参数与数组后缀，只留类型名。{@code List<String>} → {@code List}。 */
    private static String stripGenerics(String typeText) {
        if (typeText == null) {
            return null;
        }
        int angle = typeText.indexOf('<');
        String base = angle >= 0 ? typeText.substring(0, angle) : typeText;
        return base.replace("[]", "").trim();
    }
}
