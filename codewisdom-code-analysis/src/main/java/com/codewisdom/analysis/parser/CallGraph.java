package com.codewisdom.analysis.parser;

import com.codewisdom.analysis.domain.CallSite;
import com.codewisdom.analysis.domain.ImportDeclaration;
import com.codewisdom.analysis.domain.MethodDeclaration;
import com.codewisdom.analysis.domain.TypeDeclaration;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 跨文件调用图：把各文件的 {@link CallSite} 按 import 表解析成工程内的调用边。
 *
 * <h2>为什么不在抽取阶段解析</h2>
 * 简单名 → 全限定名的解析依赖「整个工程有哪些类型」这个全局信息，单文件抽取时拿不到。
 * 因此抽取层只记录「接收者能推断出的类型引用」，解析统一放在这里。
 *
 * <h2>解析顺序</h2>
 * 严格照 JLS §6.5.5.1 的简单名解析顺序，不发明规则：
 * <ol>
 *   <li>本文件内声明的类型（含嵌套类型）</li>
 *   <li>单类型导入 {@code import a.b.C;}</li>
 *   <li>同包类型</li>
 *   <li>按需导入 {@code import a.b.*;}——<b>命中多个包时判为歧义，返回未解析</b>，不挑一个</li>
 * </ol>
 * 无接收者的调用（{@code run()}）再按 JLS §6.5.6.1 走一遍：先在本类型及其工程内祖先里找同名方法，
 * 找不到才落到静态导入上。顺序反了会把静态导入的方法挂到调用方自己头上。
 *
 * <h2>找不到就断边，绝不猜</h2>
 * <ul>
 *   <li>{@code super.xxx()} 需要继承链分析才能定位父类；本卡不做，一律计为未解析。</li>
 *   <li>接收者是链式调用结果等复杂表达式时不做推断，计为未解析。</li>
 *   <li>目标类型在工程内、但整个继承链上都没有这个方法时，<b>不产边</b>。
 *       这是刻意的：调用图里一条假边比缺一条边危害大得多——下游的循环依赖检测、
 *       分层识别都会被它带偏。</li>
 *   <li>工程外的类型（JDK / 三方）查不到方法表，按解析到的引用原样记边。</li>
 * </ul>
 *
 * <h2>自环过滤（T-304 验收点）</h2>
 * <ul>
 *   <li><b>方法级</b>：{@code fromId == toId} 的边一律丢弃。覆盖递归自调用（{@code run()} 里调
 *       {@code run()}）与 {@code this(...)} 构造器委派。</li>
 *   <li><b>类型级</b>：{@link #typeEdges()} 在方法级边上再投影一次，丢弃
 *       {@code fromType == toType} 的边——同类内部互调在类型图上就是自环噪音。</li>
 * </ul>
 * 注意「同类内部互调」在<b>方法级保留</b>：{@code A.run() → A.helper()} 是真实调用关系，
 * 只在类型级才成为自环。两级各自过滤，语义不混淆。
 *
 * <h2>已知粒度上限</h2>
 * 节点标识只到方法名，不含参数（{@code demo.OrderService#query}）。判断实参类型需要类型推断，
 * 本模块不做，代价是同名重载共用同一节点。
 */
public final class CallGraph {

    private final Set<String> projectTypes;
    private final List<CallEdge> methodEdges;
    private final List<CallEdge> externalEdges;
    private final List<TypeEdge> typeEdges;
    private final List<CallSite> unresolvedCallSites;
    private final Map<String, Set<String>> callees;
    private final Map<String, Set<String>> callers;

    private CallGraph(Set<String> projectTypes,
                      List<CallEdge> methodEdges,
                      List<CallEdge> externalEdges,
                      List<CallSite> unresolvedCallSites) {
        this.projectTypes = projectTypes;
        this.methodEdges = methodEdges;
        this.externalEdges = externalEdges;
        this.unresolvedCallSites = unresolvedCallSites;
        this.typeEdges = projectTypeEdges(methodEdges);

        Map<String, Set<String>> calleeMap = new TreeMap<>();
        Map<String, Set<String>> callerMap = new TreeMap<>();
        for (CallEdge edge : methodEdges) {
            calleeMap.computeIfAbsent(edge.fromId(), k -> new TreeSet<>()).add(edge.toId());
            callerMap.computeIfAbsent(edge.toId(), k -> new TreeSet<>()).add(edge.fromId());
        }
        this.callees = Collections.unmodifiableMap(calleeMap);
        this.callers = Collections.unmodifiableMap(callerMap);
    }

    /**
     * 一个待解析的源文件。
     *
     * @param sourcePath 相对工程根的路径，用于把边追溯回文件
     * @param packageName 包名；默认包为空串
     * @param types      该文件声明的全部类型（{@link JavaStructureExtractor} 的输出）
     * @param methods    该文件声明的全部方法（同上）——用来判断「目标类型到底有没有这个方法」
     * @param imports    该文件的 import（{@link JavaDependencyExtractor#extractImports} 的输出）
     * @param callSites  该文件的调用点（{@link JavaDependencyExtractor#extractCallSites} 的输出）
     */
    public record SourceUnit(String sourcePath,
                             String packageName,
                             List<TypeDeclaration> types,
                             List<MethodDeclaration> methods,
                             List<ImportDeclaration> imports,
                             List<CallSite> callSites) {
    }

    /**
     * 方法级调用边。
     *
     * @param fromId     调用方标识 {@code 类型限定名#方法名}
     * @param toId       被调方标识；构造器为 {@code 类型限定名#<init>}
     * @param kind       调用种类（同一对节点可能因种类不同而分成多条边）
     * @param weight     出现次数
     * @param sourcePaths 出现该调用的文件路径，升序
     */
    public record CallEdge(String fromId, String toId, CallSite.CallKind kind,
                           int weight, Set<String> sourcePaths) {

        /** 调用方所属类型限定名。 */
        public String fromOwner() {
            return ownerOf(fromId);
        }

        /** 被调方所属类型限定名。 */
        public String toOwner() {
            return ownerOf(toId);
        }

        /** 是否为自环。构建时已过滤，此方法保留给调用方做二次校验。 */
        public boolean isSelfLoop() {
            return fromId.equals(toId);
        }

        private static String ownerOf(String id) {
            int hash = id.lastIndexOf('#');
            return hash < 0 ? id : id.substring(0, hash);
        }
    }

    /**
     * 类型级调用边——{@link #methodEdges()} 的投影，<b>已丢弃类型自环</b>。
     *
     * @param fromType    调用方类型限定名
     * @param toType      被调方类型限定名
     * @param weight      底层方法级边的次数之和
     * @param sourcePaths 出现该调用的文件路径，升序
     */
    public record TypeEdge(String fromType, String toType, int weight, Set<String> sourcePaths) {
    }

    /**
     * 构建调用图。
     *
     * @param units 全部待分析文件；顺序不影响结果
     */
    public static CallGraph build(List<SourceUnit> units) {
        ProjectIndex index = new ProjectIndex();

        // 第一趟：登记全部类型与方法。必须先做完，否则解析父类型时工程还没建全。
        for (SourceUnit unit : units) {
            for (TypeDeclaration type : unit.types()) {
                index.recordType(type);
            }
            for (MethodDeclaration method : unit.methods()) {
                index.recordMethod(method);
            }
        }

        // 第二趟：解析各类型的工程内父类型。同样要全部做完，继承链查找才能跨文件跑到。
        for (SourceUnit unit : units) {
            UnitContext context = new UnitContext(unit, index);
            for (TypeDeclaration type : unit.types()) {
                index.recordParents(type.qualifiedName(), context.resolveParentTypes(type));
            }
        }

        // 第三趟：解析调用点
        Map<String, EdgeAccumulator> accumulators = new LinkedHashMap<>();
        List<CallSite> unresolved = new ArrayList<>();

        for (SourceUnit unit : units) {
            UnitContext context = new UnitContext(unit, index);
            for (CallSite site : unit.callSites()) {
                String fromId = site.callerId();
                // 调用点在类型之外（Java 里不合法，容错解析可能产出）——无主，不计边
                if (site.callerOwnerQualifiedName() == null || site.callerOwnerQualifiedName().isBlank()) {
                    unresolved.add(site);
                    continue;
                }

                String calleeMethod = site.kind() == CallSite.CallKind.CONSTRUCTOR
                        ? CallSite.CONSTRUCTOR_METHOD
                        : site.calleeName();

                // 先定位被调方类型，再在该类型的继承链上找方法真正声明在哪一层
                String owner = context.resolveCalleeOwner(site);
                if (owner == null) {
                    unresolved.add(site);
                    continue;
                }

                String declaringType;
                if (index.isProjectType(owner)) {
                    declaringType = index.findDeclaringType(owner, calleeMethod);
                    if (declaringType == null) {
                        // 工程内类型但没有这个方法：宁可不产边，也不编一条指向自身的边
                        unresolved.add(site);
                        continue;
                    }
                } else {
                    // 工程外的类型查不到方法表，按解析到的引用原样记
                    declaringType = owner;
                }

                String toId = declaringType + "#" + calleeMethod;

                // 方法级自环：递归自调用、this(...) 构造器委派
                if (fromId.equals(toId)) {
                    continue;
                }

                accumulators.computeIfAbsent(fromId + " -> " + toId,
                                key -> new EdgeAccumulator(fromId, toId, site.kind()))
                        .add(unit.sourcePath());
            }
        }

        List<CallEdge> internal = new ArrayList<>();
        List<CallEdge> external = new ArrayList<>();
        for (EdgeAccumulator accumulator : accumulators.values()) {
            CallEdge edge = accumulator.toEdge();
            if (index.isProjectType(edge.toOwner())) {
                internal.add(edge);
            } else {
                external.add(edge);
            }
        }
        Comparator<CallEdge> order = Comparator.comparing(CallEdge::fromId).thenComparing(CallEdge::toId);
        internal.sort(order);
        external.sort(order);
        unresolved.sort(Comparator.comparing(CallSite::line));

        return new CallGraph(index.projectTypes(),
                List.copyOf(internal), List.copyOf(external), List.copyOf(unresolved));
    }

    // ---- 查询 ----

    /** 工程内声明的全部类型限定名。 */
    public Set<String> projectTypes() {
        return projectTypes;
    }

    /** 工程内的调用边，<b>无自环</b>。 */
    public List<CallEdge> methodEdges() {
        return methodEdges;
    }

    /** 指向工程外（JDK / 三方库）的调用边，<b>无自环</b>。仅包含经 import 可解析的引用。 */
    public List<CallEdge> externalEdges() {
        return externalEdges;
    }

    /** 类型级调用边，<b>无自环</b>。 */
    public List<TypeEdge> typeEdges() {
        return typeEdges;
    }

    /** 解析不到目标的调用点。不产生边，但保留下来便于排查与调优解析规则。 */
    public List<CallSite> unresolvedCallSites() {
        return unresolvedCallSites;
    }

    /** 某方法直接调用了哪些方法。 */
    public Set<String> calleesOf(String methodId) {
        return callees.getOrDefault(methodId, Set.of());
    }

    /** 哪些方法直接调用了某方法。 */
    public Set<String> callersOf(String methodId) {
        return callers.getOrDefault(methodId, Set.of());
    }

    private static List<TypeEdge> projectTypeEdges(List<CallEdge> edges) {
        Map<String, TypeEdgeAccumulator> accumulators = new LinkedHashMap<>();
        for (CallEdge edge : edges) {
            String fromType = edge.fromOwner();
            String toType = edge.toOwner();
            // 类型级自环噪音：同类内部互调
            if (fromType.equals(toType)) {
                continue;
            }
            accumulators.computeIfAbsent(fromType + " -> " + toType,
                            key -> new TypeEdgeAccumulator(fromType, toType))
                    .add(edge);
        }
        List<TypeEdge> result = new ArrayList<>();
        for (TypeEdgeAccumulator accumulator : accumulators.values()) {
            result.add(accumulator.toEdge());
        }
        result.sort(Comparator.comparing(TypeEdge::fromType).thenComparing(TypeEdge::toType));
        return List.copyOf(result);
    }

    // ---- 单文件解析上下文 ----

    /**
     * 规整类型引用文本：去注解、去泛型、去数组后缀。
     *
     * <p>{@code TypeDeclaration.superClass()} 是 T-302 原样保留的文本，可能是
     * {@code Base&lt;T&gt;}；抽取层的 {@code receiverTypeRef} 已经清洗过，这里再走一遍是幂等的。
     */
    private static String cleanTypeReference(String reference) {
        if (reference == null) {
            return null;
        }
        String withoutAnnotations = reference.replaceAll("@[\\w.]+(\\([^)]*\\))?\\s*", "");
        int angle = withoutAnnotations.indexOf('<');
        String base = angle >= 0 ? withoutAnnotations.substring(0, angle) : withoutAnnotations;
        return base.replace("[]", "").replace("...", "").trim();
    }

    // ---- 工程级索引与继承链查找 ----

    /**
     * 工程级的类型 / 方法索引，用来回答「这个方法到底声明在继承链的哪一层」。
     *
     * <p>不查这一层会出两类错：把继承来的方法挂到子类头上（<b>假边</b>），
     * 或因为子类没有该方法就断掉一条真实调用（<b>漏边</b>）。
     */
    private static final class ProjectIndex {

        private final Set<String> projectTypes = new TreeSet<>();
        private final Map<String, TypeDeclaration> typesByFqn = new LinkedHashMap<>();
        private final Set<String> declaredMethods = new HashSet<>();
        private final Map<String, List<String>> parentsByFqn = new LinkedHashMap<>();

        private void recordType(TypeDeclaration type) {
            projectTypes.add(type.qualifiedName());
            typesByFqn.put(type.qualifiedName(), type);
        }

        /** 构造器统一记成 {@code <init>}，与调用边里的标识保持一致。 */
        private void recordMethod(MethodDeclaration method) {
            String name = method.isConstructor() ? CallSite.CONSTRUCTOR_METHOD : method.name();
            declaredMethods.add(method.ownerQualifiedName() + "#" + name);
        }

        private void recordParents(String typeFqn, List<String> parents) {
            parentsByFqn.put(typeFqn, parents);
        }

        private Set<String> projectTypes() {
            return Collections.unmodifiableSet(projectTypes);
        }

        private boolean isProjectType(String fqn) {
            return projectTypes.contains(fqn);
        }

        /**
         * 沿继承链找方法声明在哪个类型上，就近优先。
         *
         * <p>广度优先：先父类后接口、先近后远，与 Java 的方法查找语义方向一致。
         * 只在工程内查找——链上走到工程外的类型就停下，那部分交给「工程外原样记边」处理。
         *
         * @return 声明该方法的类型限定名；整条链上都没有返回 {@code null}
         */
        private String findDeclaringType(String typeFqn, String methodName) {
            Deque<String> queue = new ArrayDeque<>();
            Set<String> visited = new HashSet<>();
            queue.add(typeFqn);

            while (!queue.isEmpty()) {
                String current = queue.poll();
                if (!visited.add(current)) {
                    continue;
                }
                TypeDeclaration declaration = typesByFqn.get(current);
                if (declaration == null) {
                    continue;
                }
                if (declaredMethods.contains(current + "#" + methodName)) {
                    return current;
                }
                // 隐式构造器：类 / 枚举 / 记录即使没写构造器也能 new，接口与注解不行
                if (CallSite.CONSTRUCTOR_METHOD.equals(methodName) && declaration.kind().isInstantiable()) {
                    return current;
                }
                queue.addAll(parentsByFqn.getOrDefault(current, List.of()));
            }
            return null;
        }
    }

    /** 单个文件的解析上下文：包名、导入表、本文件声明的类型。 */
    private static final class UnitContext {

        private final String packageName;
        private final Map<String, String> specificImports;
        private final List<String> wildcardPackages;
        private final Map<String, String> staticImports;
        private final Map<String, String> localTypes;
        private final ProjectIndex index;
        private final Set<String> projectTypes;

        private UnitContext(SourceUnit unit, ProjectIndex index) {
            this.packageName = unit.packageName() == null ? "" : unit.packageName();
            this.index = index;
            this.projectTypes = index.projectTypes();
            this.specificImports = new LinkedHashMap<>();
            this.wildcardPackages = new ArrayList<>();
            this.staticImports = new LinkedHashMap<>();
            this.localTypes = new LinkedHashMap<>();

            for (ImportDeclaration importDeclaration : unit.imports()) {
                String wildcardPackage = importDeclaration.wildcardPackage();
                if (wildcardPackage != null) {
                    wildcardPackages.add(wildcardPackage);
                    continue;
                }
                String staticOwner = importDeclaration.staticOwnerType();
                if (staticOwner != null) {
                    staticImports.put(importDeclaration.simpleName(), staticOwner);
                    continue;
                }
                // 后面的同名单类型导入会覆盖前面的；真实 Java 里这是编译错误，容错解析下按后者为准
                specificImports.put(importDeclaration.simpleName(), importDeclaration.importedQualifiedName());
            }

            for (TypeDeclaration type : unit.types()) {
                localTypes.put(type.name(), type.qualifiedName());
            }
        }

        /** 该类型声明的父类与接口中能在工程内定位的部分。 */
        private List<String> resolveParentTypes(TypeDeclaration type) {
            List<String> parents = new ArrayList<>();
            if (type.superClass() != null) {
                addIfResolved(parents, type.superClass());
            }
            for (String implemented : type.interfaces()) {
                addIfResolved(parents, implemented);
            }
            return parents;
        }

        private void addIfResolved(List<String> parents, String reference) {
            String resolved = resolveType(cleanTypeReference(reference));
            if (resolved != null && projectTypes.contains(resolved)) {
                parents.add(resolved);
            }
        }

        /**
         * 解析一次调用，得到被调方所属类型的限定名。
         *
         * @return 限定名（可能是工程外类型）；无法解析返回 {@code null}
         */
        private String resolveCalleeOwner(CallSite site) {
            if (site.receiverKind() == CallSite.ReceiverKind.NONE) {
                // JLS §6.5.6.1：本类型及其祖先优先，找不到才落到静态导入
                String staticOwner = staticImports.get(site.calleeName());
                if (staticOwner == null) {
                    return site.callerOwnerQualifiedName();
                }
                boolean declaredLocally = index.findDeclaringType(
                        site.callerOwnerQualifiedName(), site.calleeName()) != null;
                return declaredLocally ? site.callerOwnerQualifiedName() : staticOwner;
            }
            if (site.receiverKind() == CallSite.ReceiverKind.THIS) {
                return site.callerOwnerQualifiedName();
            }
            if (site.receiverKind() == CallSite.ReceiverKind.SUPER
                    || site.receiverTypeRef() == null) {
                return null;
            }
            return resolveType(cleanTypeReference(site.receiverTypeRef()));
        }

        /**
         * 简单名 / 限定名 → 全限定名，按 JLS §6.5.5.1 的顺序。
         *
         * <p>返回的不一定在工程内：显式 import 的三方类是合法的解析结果，
         * 由调用方据 {@code projectTypes} 区分内外部边。
         */
        private String resolveType(String reference) {
            if (reference == null || reference.isBlank()) {
                return null;
            }
            String trimmed = reference.trim();
            if (projectTypes.contains(trimmed)) {
                return trimmed;
            }

            String simpleName = trimmed.substring(trimmed.lastIndexOf('.') + 1);

            String local = localTypes.get(simpleName);
            if (local != null) {
                return local;
            }

            String imported = specificImports.get(simpleName);
            if (imported != null) {
                return imported;
            }

            if (!packageName.isEmpty() && projectTypes.contains(packageName + "." + simpleName)) {
                return packageName + "." + simpleName;
            }

            // 按需导入：命中多个包即为歧义，不猜
            Set<String> wildcardHits = new LinkedHashSet<>();
            for (String wildcardPackage : wildcardPackages) {
                String candidate = wildcardPackage + "." + simpleName;
                if (projectTypes.contains(candidate)) {
                    wildcardHits.add(candidate);
                }
            }
            if (wildcardHits.size() == 1) {
                return wildcardHits.iterator().next();
            }

            // 隐式 java.lang.*：只在工程内真存在该类型时才认，否则视为外部未解析
            String javaLang = "java.lang." + simpleName;
            return projectTypes.contains(javaLang) ? javaLang : null;
        }
    }

    // ---- 累加器 ----

    private static final class EdgeAccumulator {

        private final String fromId;
        private final String toId;
        private final CallSite.CallKind kind;
        private final Set<String> sourcePaths = new TreeSet<>();
        private int weight;

        private EdgeAccumulator(String fromId, String toId, CallSite.CallKind kind) {
            this.fromId = fromId;
            this.toId = toId;
            this.kind = kind;
        }

        private void add(String sourcePath) {
            weight++;
            sourcePaths.add(sourcePath);
        }

        private CallEdge toEdge() {
            return new CallEdge(fromId, toId, kind, weight, Collections.unmodifiableSet(sourcePaths));
        }
    }

    private static final class TypeEdgeAccumulator {

        private final String fromType;
        private final String toType;
        private final Set<String> sourcePaths = new TreeSet<>();
        private int weight;

        private TypeEdgeAccumulator(String fromType, String toType) {
            this.fromType = fromType;
            this.toType = toType;
        }

        private void add(CallEdge edge) {
            weight += edge.weight();
            sourcePaths.addAll(edge.sourcePaths());
        }

        private TypeEdge toEdge() {
            return new TypeEdge(fromType, toType, weight, Collections.unmodifiableSet(sourcePaths));
        }
    }
}
