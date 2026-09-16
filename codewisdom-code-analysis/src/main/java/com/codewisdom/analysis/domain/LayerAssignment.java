package com.codewisdom.analysis.domain;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 一个类型的架构分层归类结果。
 *
 * <p><b>四个信号分开保留，不合并成一个结论</b>——因为「这个类放在哪」与「这个类是什么」
 * 是两件事，而它们的<b>不一致本身就是结论</b>：{@code @RestController} 却落在 {@code service} 包里，
 * 正是阶段 5 的「分层混乱」要抓的东西（T-504）。只留一个最终分层，这个信息就永久丢了。
 *
 * @param typeQualifiedName 类型限定名
 * @param packageName       所在包名，用于输出「包结构 → 分层」视图
 * @param sourcePath        源文件路径（工程内相对路径）
 * @param pathSignal        路径信号：{@code src/test/} 下的代码为 {@link LayerKind#TEST}，否则 null
 * @param annotationSignal  注解信号，来自 {@code @RestController} / {@code @Service} 等
 * @param packageSignal     包名信号，来自包名中<b>最内层</b>能命中的段
 * @param namingSignal      类型名信号，来自 {@code *Controller} / {@code *ServiceImpl} 等后缀
 */
public record LayerAssignment(String typeQualifiedName,
                              String packageName,
                              String sourcePath,
                              LayerKind pathSignal,
                              LayerKind annotationSignal,
                              LayerKind packageSignal,
                              LayerKind namingSignal) {

    /**
     * 最终分层：按 <b>路径 → 注解 → 包名 → 类型名</b> 取第一个有意见的信号。
     *
     * <p>这个优先级是有理由的，不是随手排的：
     * <ol>
     *   <li><b>路径</b>最优先——{@code src/test/} 是编译期事实，测试代码必须先被隔离出去，
     *       否则同包同名的测试类会把 {@code service} 层的统计灌满。</li>
     *   <li><b>注解</b>次之——{@code @RestController} / {@code @Service} 是框架实际生效的契约，
     *       写着 {@code @Service} 的类在运行期就是 Bean 的 service 角色，不以人的命名为转移。</li>
     *   <li><b>包名</b>再次——包是物理边界，决定模块依赖方向，比名字更能代表架构意图。</li>
     *   <li><b>类型名</b>最后——它只是标签，光看名字判断最容易错（{@code Utils} 里塞业务是常态）。</li>
     * </ol>
     */
    public LayerKind layer() {
        LayerKind[] priority = {pathSignal, annotationSignal, packageSignal, namingSignal};
        for (LayerKind signal : priority) {
            if (signal != null) {
                return signal;
            }
        }
        return LayerKind.UNKNOWN;
    }

    /**
     * 判定依据，取值 {@code path} / {@code annotation} / {@code package} / {@code naming} / {@code none}。
     *
     * <p>输出架构说明时要能讲清「为什么把这个类算成 service 层」，没有这个字段只能靠人再猜一遍。
     */
    public String rule() {
        if (pathSignal != null) {
            return "path";
        }
        if (annotationSignal != null) {
            return "annotation";
        }
        if (packageSignal != null) {
            return "package";
        }
        if (namingSignal != null) {
            return "naming";
        }
        return "none";
    }

    /**
     * 信号之间是否自相矛盾。
     *
     * <p>只比较<b>角色信号</b>，见 {@link #signals()}。作用域信号（是不是测试代码）与角色无关，
     * 算进来会造出一堆假冲突：「{@code service} 包下的 {@code OrderServiceTest}」同时带
     * {@code SERVICE} 与 {@code TEST} 两个信号，但这是规范做法，不是分层混乱。
     */
    public boolean hasConflict() {
        return signals().size() > 1;
    }

    /**
     * 表态过的角色信号集合，供人工排查与阶段 5 的「分层混乱」规则消费。
     *
     * <p><b>{@link LayerKind#TEST} 不算角色信号</b>，无论它来自路径还是 {@code *Test} 命名后缀——
     * 它回答的是「这段代码属于主工程还是测试」，不是「这个类在架构里扮演什么角色」。
     * 把它算进来，「{@code service} 包下的 {@code OrderServiceTest}」就会被误报成分层混乱。
     */
    public Set<LayerKind> signals() {
        Set<LayerKind> signals = new LinkedHashSet<>();
        addRoleSignal(signals, annotationSignal);
        addRoleSignal(signals, packageSignal);
        addRoleSignal(signals, namingSignal);
        return signals;
    }

    private static void addRoleSignal(Set<LayerKind> signals, LayerKind signal) {
        if (signal != null && signal != LayerKind.TEST) {
            signals.add(signal);
        }
    }

    /** 是否没能归类。 */
    public boolean isUnclassified() {
        return layer() == LayerKind.UNKNOWN;
    }
}
