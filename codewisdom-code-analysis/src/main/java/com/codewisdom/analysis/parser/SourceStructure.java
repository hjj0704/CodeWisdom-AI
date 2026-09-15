package com.codewisdom.analysis.parser;

import com.codewisdom.analysis.domain.MethodDeclaration;
import com.codewisdom.analysis.domain.TypeDeclaration;

import java.util.List;

/**
 * 单个源文件的结构化抽取结果。
 *
 * @param packageName 包名；默认包为空串
 * @param types       按源码出现顺序排列的全部类型声明（含嵌套类型，外层在前）
 * @param methods     按源码出现顺序排列的全部方法/构造器声明
 */
public record SourceStructure(String packageName,
                              List<TypeDeclaration> types,
                              List<MethodDeclaration> methods) {

    /** 顶层类型（不属于任何其他类型）。 */
    public List<TypeDeclaration> topLevelTypes() {
        return types.stream().filter(TypeDeclaration::isTopLevel).toList();
    }

    /** 嵌套类型。 */
    public List<TypeDeclaration> nestedTypes() {
        return types.stream().filter(TypeDeclaration::isNested).toList();
    }

    /** 指定类型下的方法，含构造器。 */
    public List<MethodDeclaration> methodsOf(String ownerQualifiedName) {
        return methods.stream()
                .filter(m -> m.ownerQualifiedName().equals(ownerQualifiedName))
                .toList();
    }

    /** 仅构造器。 */
    public List<MethodDeclaration> constructors() {
        return methods.stream().filter(MethodDeclaration::isConstructor).toList();
    }
}
