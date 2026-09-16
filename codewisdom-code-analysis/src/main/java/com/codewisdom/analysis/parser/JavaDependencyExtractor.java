package com.codewisdom.analysis.parser;

import com.codewisdom.analysis.domain.CallSite;
import com.codewisdom.analysis.domain.ImportDeclaration;
import com.codewisdom.analysis.domain.TypeKind;
import org.springframework.stereotype.Component;
import org.treesitter.TSNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从 Java 语法树中抽取<b>跨文件依赖线索</b>：import 声明与调用点。
 *
 * <p>节点结构全部来自对 tree-sitter-java 的实测（不是猜的），关键几条：
 * <pre>
 * import_declaration              <b>无字段名</b>；子节点 = import / static? / scoped_identifier|identifier / asterisk? / ;
 * method_invocation               object? / type_arguments? / name / arguments
 * object_creation_expression      type / arguments
 * explicit_constructor_invocation constructor / arguments
 * method_reference                <b>无字段名</b>；子节点 = 限定符 / :: / 成员名或 new
 * constructor_body                构造器体<b>不是</b> block
 * </pre>
 *
 * <p>因此有三条硬约束：
 * <ol>
 *   <li>{@code method_invocation} 的 {@code object} 在<b>无接收者时缺失</b>（如 {@code requireNonNull(t)}），
 *       必须用 {@code isNull()} 判断「空节点」，否则全被误当成 {@code this.xxx()}。</li>
 *   <li>{@code type_arguments} 是 {@code name} <b>之前</b>的独立字段（{@code this.<String>foo()}），
 *       定位 {@code name} 只能按字段名，按子节点下标会错位。</li>
 *   <li>构造器体是 {@code constructor_body} 不是 {@code block}——只找 {@code block}
 *       会漏掉全部构造器内的调用，且<b>静默漏掉</b>，测试不覆盖就发现不了。</li>
 * </ol>
 *
 * <p><b>本类只提交「原始事实」，不做跨文件解析、不做自环过滤</b>——那是
 * {@code CallGraph} 的职责。接收者的类型是<b>近似推断</b>：维护一张
 * 「变量名 → 声明类型」的表，不区分作用域，同名时后出现的（局部变量）覆盖先出现的（字段），
 * 这在遮蔽场景下恰好是对的，但同名字段跨方法复用会取到最后一个——属于已知近似，不是缺陷。
 */
@Component
public class JavaDependencyExtractor {

    /** 无字段名节点（import_declaration / method_reference）的取值方式见类注释。 */
    private static final String IMPORT_DECLARATION = "import_declaration";
    private static final String METHOD_INVOCATION = "method_invocation";
    private static final String OBJECT_CREATION = "object_creation_expression";
    private static final String CONSTRUCTOR_DELEGATION = "explicit_constructor_invocation";
    private static final String METHOD_REFERENCE = "method_reference";

    /** 与 {@code JavaStructureExtractor} 共用包名解析，避免两处 regex 漂移导致限定名对不上。 */
    private final JavaStructureExtractor structureExtractor;

    public JavaDependencyExtractor(JavaStructureExtractor structureExtractor) {
        this.structureExtractor = structureExtractor;
    }

    /**
     * 抽取 import 声明，保持源码出现顺序。
     *
     * <p>只找 {@code program} 的<b>直接</b>子节点：import 不允许出现在类型内部，
     * 用全树遍历反而会把注释或字符串里的同名节点捞进来。
     */
    public List<ImportDeclaration> extractImports(ParseHandle handle) {
        List<ImportDeclaration> imports = new ArrayList<>();
        for (TSNode node : handle.findChildren(handle.root(), IMPORT_DECLARATION)) {
            boolean isStatic = false;
            boolean isWildcard = false;
            String name = null;

            int count = node.getChildCount();
            for (int i = 0; i < count; i++) {
                TSNode child = node.getChild(i);
                switch (child.getType()) {
                    case "static" -> isStatic = true;
                    // 通配符表现为独立的 asterisk 兄弟节点，文本里找不到 '*'
                    case "asterisk" -> isWildcard = true;
                    case "scoped_identifier", "identifier" -> name = handle.text(child).trim();
                    default -> {
                        // import 关键字与分号，跳过
                    }
                }
            }

            if (name != null && !name.isEmpty()) {
                imports.add(new ImportDeclaration(name, isStatic, isWildcard, handle.startLine(node)));
            }
        }
        return imports;
    }

    /**
     * 抽取全部调用点。
     *
     * <p>两趟：先扫一遍全树建「变量名 → 类型」表，再带上下文遍历收集调用。
     * 必须先建表，因为调用可能出现在变量声明之前（字段初始化器引用后声明的字段？不合法，
     * 但方法体引用后声明的局部变量同样不合法）——真正的原因是<b>字段</b>可以在类体任意位置
     * 被更早的方法引用，一趟扫不到。
     */
    public List<CallSite> extractCallSites(ParseHandle handle) {
        WalkContext context = new WalkContext(handle, structureExtractor.extractPackageName(handle));
        visit(handle.root(), context);
        return context.callSites;
    }

    // ---- 遍历 ----

    private void visit(TSNode node, WalkContext context) {
        String nodeType = node.getType();

        TypeKind typeKind = TypeKind.fromNodeType(nodeType);
        if (typeKind != null) {
            String name = context.handle.nameOf(node);
            String qualifiedName = qualifiedNameOf(context.packageName, context.typeStack.peek(),
                    name == null ? "<anonymous>" : name);
            context.typeStack.push(qualifiedName);
            try {
                // 进入类型后先假定不在方法体内，方法声明会覆盖它
                visitChildren(node, context, CallSite.INITIALIZER);
            } finally {
                context.typeStack.pop();
            }
            return;
        }

        if ("method_declaration".equals(nodeType) || "constructor_declaration".equals(nodeType)) {
            // 构造器统一记成 <init>：这样 this(...) 委派才与它自身构成同一条标识，
            // 从而被自环过滤掉。用类名做标识会让自环漏网，且同名重载会散成多个节点。
            String methodName = "constructor_declaration".equals(nodeType)
                    ? CallSite.CONSTRUCTOR_METHOD
                    : context.handle.nameOf(node);
            visitChildren(node, context, methodName == null ? CallSite.CONSTRUCTOR_METHOD : methodName);
            return;
        }

        collectCall(node, context);
        // 继续下钻：链式调用 a().b() 的内层、lambda 体内的调用都是真实调用
        visitChildren(node, context, context.currentMethod);
    }

    private void visitChildren(TSNode node, WalkContext context, String methodName) {
        String previous = context.currentMethod;
        context.currentMethod = methodName;
        try {
            int count = node.getChildCount();
            for (int i = 0; i < count; i++) {
                visit(node.getChild(i), context);
            }
        } finally {
            context.currentMethod = previous;
        }
    }

    /** 把当前节点上的调用登记下来；不是调用节点则什么也不做。 */
    private void collectCall(TSNode node, WalkContext context) {
        CallSite callSite = switch (node.getType()) {
            case METHOD_INVOCATION -> toMethodInvocation(node, context);
            case OBJECT_CREATION -> toConstructorCall(node, context);
            case CONSTRUCTOR_DELEGATION -> toConstructorDelegation(node, context);
            case METHOD_REFERENCE -> toMethodReference(node, context);
            default -> null;
        };
        if (callSite != null) {
            context.callSites.add(callSite);
        }
    }

    private CallSite toMethodInvocation(TSNode node, WalkContext context) {
        String calleeName = context.handle.nameOf(node);
        if (calleeName == null) {
            return null;
        }
        Receiver receiver = resolveReceiver(node.getChildByFieldName("object"), context);
        return new CallSite(context.ownerQualifiedName(), context.currentMethod,
                receiver.text(), receiver.kind(), receiver.typeRef(),
                calleeName, CallSite.CallKind.METHOD, context.handle.startLine(node));
    }

    /** {@code new X()}。{@code calleeName} 放的是类型引用，方法名由解析层补 {@code <init>}。 */
    private CallSite toConstructorCall(TSNode node, WalkContext context) {
        String typeRef = typeRefOf(node.getChildByFieldName("type"), context.handle);
        if (typeRef == null) {
            return null;
        }
        return new CallSite(context.ownerQualifiedName(), context.currentMethod,
                null, CallSite.ReceiverKind.TYPE, typeRef,
                typeRef, CallSite.CallKind.CONSTRUCTOR, context.handle.startLine(node));
    }

    /** {@code this(...)} / {@code super(...)}。抽出来但注定被解析层过滤，见 {@link CallSite.CallKind}。 */
    private CallSite toConstructorDelegation(TSNode node, WalkContext context) {
        TSNode constructor = node.getChildByFieldName("constructor");
        String text = constructor == null || constructor.isNull() ? "" : context.handle.text(constructor).trim();
        CallSite.ReceiverKind kind = switch (text) {
            case "this" -> CallSite.ReceiverKind.THIS;
            case "super" -> CallSite.ReceiverKind.SUPER;
            default -> CallSite.ReceiverKind.UNRESOLVED;
        };
        return new CallSite(context.ownerQualifiedName(), context.currentMethod,
                text.isEmpty() ? null : text, kind, null,
                CallSite.CONSTRUCTOR_METHOD, CallSite.CallKind.CONSTRUCTOR_DELEGATION,
                context.handle.startLine(node));
    }

    /**
     * {@code X::foo} / {@code X::new}。
     *
     * <p>{@code method_reference} 没有字段名，只能按子节点顺序取：
     * 跳过 {@code ::} 令牌，第一个是限定符，第二个是成员名。{@code X::new} 的第二个子节点
     * 是 {@code new} 关键字节点，因此<b>不能</b>只按 {@code identifier} 类型去筛。
     */
    private CallSite toMethodReference(TSNode node, WalkContext context) {
        String qualifier = null;
        String member = null;
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            TSNode child = node.getChild(i);
            if ("::".equals(child.getType())) {
                continue;
            }
            if (qualifier == null) {
                qualifier = context.handle.text(child).trim();
            } else {
                member = context.handle.text(child).trim();
            }
        }
        if (qualifier == null || member == null || qualifier.isEmpty() || member.isEmpty()) {
            return null;
        }

        Receiver receiver = receiverOfQualifier(qualifier);
        boolean constructorRef = "new".equals(member);
        return new CallSite(context.ownerQualifiedName(), context.currentMethod,
                qualifier, receiver.kind(), receiver.typeRef(),
                constructorRef ? qualifier : member,
                constructorRef ? CallSite.CallKind.CONSTRUCTOR : CallSite.CallKind.METHOD_REFERENCE,
                context.handle.startLine(node));
    }

    // ---- 接收者推断 ----

    /**
     * 推断接收者。
     *
     * <p>规则刻意保守：<b>推断不出就标 UNRESOLVED，绝不猜一个类型名出来</b>。
     * 调用图里一条假边比缺一条边危害大得多——下游的循环依赖检测、分层识别都会被它带偏。
     */
    private Receiver resolveReceiver(TSNode objectNode, WalkContext context) {
        if (objectNode == null || objectNode.isNull()) {
            return new Receiver(null, CallSite.ReceiverKind.NONE, null);
        }
        String text = context.handle.text(objectNode).trim();
        return switch (objectNode.getType()) {
            case "this" -> new Receiver(text, CallSite.ReceiverKind.THIS, null);
            case "super" -> new Receiver(text, CallSite.ReceiverKind.SUPER, null);

            // 裸标识符：是变量就用变量类型，否则按类名试（OrderService.helper() 这种静态调用）
            case "identifier" -> variableOrType(text, text, context);

            // a.b(...)：末段是已知变量则整体是字段访问，否则整段当类型引用（java.util.Objects）
            case "field_access" -> {
                TSNode field = objectNode.getChildByFieldName("field");
                String fieldName = field == null || field.isNull() ? null : context.handle.text(field).trim();
                String fieldType = fieldName == null ? null : context.variableTypes.get(fieldName);
                if (fieldType != null) {
                    yield new Receiver(text, CallSite.ReceiverKind.VARIABLE, fieldType);
                }
                yield new Receiver(text, CallSite.ReceiverKind.TYPE, text);
            }

            // a.b.C(...)：包路径形式的类型引用
            case "scoped_identifier" -> new Receiver(text, CallSite.ReceiverKind.TYPE, text);

            // new Helper().decorate()：接收者是刚构造出来的对象，类型就是它
            case OBJECT_CREATION -> {
                String typeRef = typeRefOf(objectNode.getChildByFieldName("type"), context.handle);
                yield typeRef == null
                        ? new Receiver(text, CallSite.ReceiverKind.UNRESOLVED, null)
                        : new Receiver(text, CallSite.ReceiverKind.TYPE, typeRef);
            }

            // 链式调用结果 a().b()、数组下标、三元表达式……一律不推断
            default -> new Receiver(text, CallSite.ReceiverKind.UNRESOLVED, null);
        };
    }

    private Receiver variableOrType(String text, String identifier, WalkContext context) {
        String declaredType = context.variableTypes.get(identifier);
        if (declaredType != null) {
            return new Receiver(text, CallSite.ReceiverKind.VARIABLE, declaredType);
        }
        return new Receiver(text, CallSite.ReceiverKind.TYPE, text);
    }

    private Receiver receiverOfQualifier(String qualifier) {
        return switch (qualifier) {
            case "this" -> new Receiver(qualifier, CallSite.ReceiverKind.THIS, null);
            case "super" -> new Receiver(qualifier, CallSite.ReceiverKind.SUPER, null);
            default -> new Receiver(qualifier, CallSite.ReceiverKind.TYPE, qualifier);
        };
    }

    // ---- 变量类型表 ----

    /**
     * 扫全树收集「变量名 → 声明类型引用」。
     *
     * <p>覆盖字段、局部变量、方法参数与 catch 形参。不区分作用域——见类注释里的近似说明。
     * 类型引用保留源码里的写法（可能是简单名，也可能已经是全限定名），
     * 具体落到哪个类由 {@code CallGraph} 按 import 表解析。
     */
    private static Map<String, String> collectVariableTypes(ParseHandle handle) {
        Map<String, String> types = new LinkedHashMap<>();
        handle.walk(node -> {
            switch (node.getType()) {
                case "field_declaration", "local_variable_declaration" -> {
                    String typeRef = typeRefOf(node.getChildByFieldName("type"), handle);
                    if (typeRef == null) {
                        return;
                    }
                    for (TSNode declarator : handle.findChildren(node, "variable_declarator")) {
                        String name = handle.nameOf(declarator);
                        if (name != null) {
                            types.put(name, typeRef);
                        }
                    }
                }
                case "formal_parameter", "catch_formal_parameter" -> {
                    String typeRef = typeRefOf(node.getChildByFieldName("type"), handle);
                    String name = handle.nameOf(node);
                    if (typeRef != null && name != null) {
                        types.put(name, typeRef);
                    }
                }
                default -> {
                    // 其余节点不参与类型表
                }
            }
        });
        return types;
    }

    // ---- 工具 ----

    /** 把类型节点文本规整成类型引用：去注解、去泛型、去数组与可变参数后缀。 */
    private static String typeRefOf(TSNode typeNode, ParseHandle handle) {
        if (typeNode == null || typeNode.isNull()) {
            return null;
        }
        String raw = handle.text(typeNode).trim();
        String withoutAnnotations = raw.replaceAll("@[\\w.]+(\\([^)]*\\))?\\s*", "");
        // 只取最外层类型：Map<String, List<Integer>> → Map
        int angle = withoutAnnotations.indexOf('<');
        String base = angle >= 0 ? withoutAnnotations.substring(0, angle) : withoutAnnotations;
        String cleaned = base.replace("[]", "").replace("...", "").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    /** 与 {@code JavaStructureExtractor#qualifiedNameOf} 同规则，两处必须一致。 */
    private static String qualifiedNameOf(String packageName, String enclosing, String name) {
        if (enclosing != null && !enclosing.isBlank()) {
            return enclosing + "." + name;
        }
        return packageName == null || packageName.isBlank() ? name : packageName + "." + name;
    }

    /** 接收者推断结果。 */
    private record Receiver(String text, CallSite.ReceiverKind kind, String typeRef) {
    }

    /** 一次遍历的可变上下文，避免把六个参数在递归里传来传去。 */
    private static final class WalkContext {

        private final ParseHandle handle;
        private final Map<String, String> variableTypes;
        private final String packageName;
        private final Deque<String> typeStack = new ArrayDeque<>();
        private final List<CallSite> callSites = new ArrayList<>();
        private String currentMethod = CallSite.INITIALIZER;

        private WalkContext(ParseHandle handle, String packageName) {
            this.handle = handle;
            this.packageName = packageName;
            this.variableTypes = collectVariableTypes(handle);
        }

        private String ownerQualifiedName() {
            String owner = typeStack.peek();
            return owner == null ? "" : owner;
        }
    }
}
