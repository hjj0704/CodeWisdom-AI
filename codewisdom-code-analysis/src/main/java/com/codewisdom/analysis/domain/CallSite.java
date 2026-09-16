package com.codewisdom.analysis.domain;

/**
 * 一处调用点：在哪个方法里、调用了什么。
 *
 * <p>这是<b>原始事实</b>，不做跨文件解析、不做自环过滤——那些是
 * {@code CallGraph} 的职责。把「抽取」与「解析」分开，是为了让解析规则变严时
 * 不必重新解析源码，也让排错时能看到「到底抽到了什么」。
 *
 * @param callerOwnerQualifiedName 调用方所属类型的限定名
 * @param callerMethodName         调用方方法名；构造器统一为 {@code <init>}（与字节码一致，
 *                                 这样 {@code this(...)} 委派才与自身同标识而被自环过滤）；
 *                                 不在任何方法体内的调用（字段初始化器等）为 {@code <initializer>}
 * @param receiverText             接收者原始文本，仅用于展示与排错；无接收者为 null
 * @param receiverKind             接收者种类，决定解析策略
 * @param receiverTypeRef          接收者能推断出的<b>类型引用</b>（可能是不含包名的简单名），
 *                                 推断不出为 null。例如 {@code helper.decorate()} 中
 *                                 {@code helper} 声明为 {@code Helper} 时，这里是 {@code Helper}
 * @param calleeName               被调用者名称：方法名，或 {@code new X()} 的类型名
 * @param kind                     调用种类
 * @param line                     行号（1-based）
 */
public record CallSite(String callerOwnerQualifiedName,
                       String callerMethodName,
                       String receiverText,
                       ReceiverKind receiverKind,
                       String receiverTypeRef,
                       String calleeName,
                       CallKind kind,
                       int line) {

    /** 字段初始化器等「不属于任何方法」的调用，统一挂在这个合成名下。 */
    public static final String INITIALIZER = "<initializer>";

    /** 构造器在调用图中的方法名，与 JVM 字节码里的 {@code <init>} 保持一致。 */
    public static final String CONSTRUCTOR_METHOD = "<init>";

    /**
     * 调用种类。
     *
     * <p>{@link #CONSTRUCTOR_DELEGATION} 是 {@code this(...)} / {@code super(...)}。
     * 它一定会产生自环或指向工程外的边，属于典型的图噪音，但<b>仍然抽出来</b>——
     * 「抽取如实记录、由解析层过滤」比「抽取层悄悄丢掉」更容易排错。
     */
    public enum CallKind {
        /** 普通方法调用 {@code a.foo()} */
        METHOD,
        /** 构造调用 {@code new X()} */
        CONSTRUCTOR,
        /** 构造器委派 {@code this(...)} / {@code super(...)} */
        CONSTRUCTOR_DELEGATION,
        /** 方法引用 {@code X::foo} / {@code X::new} */
        METHOD_REFERENCE
    }

    /**
     * 接收者种类。区分这些不是为了好看，而是因为<b>解析策略完全不同</b>：
     * {@code this}/{@code NONE} 指向自身类型，{@code TYPE} 走 import 表，
     * {@code VARIABLE} 得先查变量声明再走 import 表，其余一律不猜。
     */
    public enum ReceiverKind {
        /** 无接收者，如 {@code run()}——即对本类型方法的调用 */
        NONE,
        /** {@code this.foo()} */
        THIS,
        /** {@code super.foo()}——父类通常需要继承链分析，标记为不可解析 */
        SUPER,
        /** 接收者是类型名，如 {@code Objects.hash()} / {@code java.util.Objects.hash()} */
        TYPE,
        /** 接收者是变量或字段，如 {@code helper.decorate()} */
        VARIABLE,
        /** 接收者是复杂表达式（链式调用结果、数组下标……），不做推断 */
        UNRESOLVED
    }

    /**
     * 调用方标识，形如 {@code demo.OrderService#run}。
     *
     * <p><b>刻意只到方法名、不带参数</b>：判断实参类型需要类型推断，本模块不做。
     * 代价是同名重载共用同一个节点，这是调用图在此粒度上的已知上限，不是缺陷。
     */
    public String callerId() {
        return callerOwnerQualifiedName + "#" + callerMethodName;
    }

    /** 接收者是否指向调用方自身类型。 */
    public boolean isImplicitSelfReceiver() {
        return receiverKind == ReceiverKind.NONE || receiverKind == ReceiverKind.THIS;
    }
}
