package com.codewisdom.analysis.domain;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 方法或构造器声明。
 *
 * @param name               方法名；构造器为类名
 * @param ownerQualifiedName 所属类型的限定名
 * @param kind               方法 或 构造器
 * @param returnType         返回类型文本；<b>构造器为 null</b>
 * @param typeParameters     泛型类型参数，如 {@code ["R"]}；无则为空列表
 * @param parameters         参数列表
 * @param modifiers          修饰符关键字
 * @param annotations        方法上的注解简单名，不含 {@code @}
 * @param thrownTypes        声明的受检异常类型
 * @param hasBody            是否有方法体。<b>false 表示抽象方法或接口方法</b>
 * @param startLine          起始行号（1-based，含）
 * @param endLine            结束行号（1-based，含）
 */
public record MethodDeclaration(
        String name,
        String ownerQualifiedName,
        MethodKind kind,
        String returnType,
        List<String> typeParameters,
        List<MethodParameter> parameters,
        Set<String> modifiers,
        List<String> annotations,
        List<String> thrownTypes,
        boolean hasBody,
        int startLine,
        int endLine
) {
    /** 是否为抽象方法（无方法体且非 native）。 */
    public boolean isAbstract() {
        return !hasBody;
    }

    public boolean isConstructor() {
        return kind == MethodKind.CONSTRUCTOR;
    }

    /** 参数个数。 */
    public int parameterCount() {
        return parameters.size();
    }

    /**
     * 可读签名，形如 {@code findOrders(String userId, int limit)}。
     *
     * <p>不含返回值与修饰符——这两个会变，而签名用于跨文件调用匹配时要稳定。
     */
    public String signature() {
        return name + "(" + parameters.stream()
                .map(MethodParameter::signature)
                .collect(Collectors.joining(", ")) + ")";
    }
}
