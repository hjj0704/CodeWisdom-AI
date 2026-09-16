package com.codewisdom.analysis.arch;

import com.codewisdom.analysis.domain.LayerAssignment;
import com.codewisdom.analysis.domain.LayerKind;
import com.codewisdom.analysis.domain.LayerReport;
import com.codewisdom.analysis.domain.TypeDeclaration;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 分层识别：判断每个类型属于 controller / service / mapper / ... 哪一层。
 *
 * <p>输入是 {@code JavaStructureExtractor}（T-302）已经抽好的类型声明 + 文件路径，
 * <b>不需要重新解析源码</b>——分层是包名、注解、命名的问题，语法树里早就有了。
 *
 * <h2>四个信号与优先级</h2>
 * 路径 → 注解 → 包名 → 类型名，逐级降权。每一级都可能「不表态」（返回 null），
 * 四级都不表态才落到 {@link LayerKind#UNKNOWN}。为什么是这个顺序，见
 * {@link LayerAssignment#layer()}。
 *
 * <p>关键设计：<b>四个信号全部保留在结果里</b>，不做「合并成一个结论就丢掉过程」。
 * 信号互相矛盾（{@code @RestController} 落在 {@code service} 包里）本身就是阶段 5
 * 「分层混乱」规则的直接输入，合并掉就永久丢失了。
 *
 * <h2>不猜的地方</h2>
 * {@code model} / {@code pojo} / {@code bean} / {@code common} 这类包里实体与 DTO 混放是常态，
 * {@link LayerKind} 刻意不为这些段建映射；类型名也认不出来时就给 {@link LayerKind#UNKNOWN}，
 * 由 {@link LayerReport#unclassified()} 显式暴露。分层统计宁可少算几类，
 * 也不要靠猜把架构图画歪——图一旦画错，后面所有基于它的判断都跟着错。
 *
 * <h2>准确率的口径</h2>
 * 分层识别是<b>启发式</b>，不是语义分析。命名自由的项目里必然有认不出的类，
 * 任何「识别准确率 X%」的说法都只对特定样例集成立，不能外推——输出时一律按
 * 「辅助分析结果，需人工确认」表述。
 */
@Component
public class LayerDetector {

    /**
     * 测试代码路径标记。
     *
     * <p>不带前导斜杠：工程内路径是相对路径（{@code src/test/java/...}），
     * 写成 {@code /src/test/} 在相对路径上永远匹配不上——而且匹配不上时不报错，
     * 只是静默地把测试类混进分层统计里。
     * 用 {@code src/test/} 还能顺带兼容 {@code module-a/src/test/java/...} 这种多模块路径。
     */
    private static final String TEST_PATH_MARKER = "src/test/";

    /**
     * 识别单个类型。
     *
     * @param sourcePath 源文件在工程内的相对路径，用于判定是否测试代码；可为 null
     * @param type       类型声明
     * @return 归类结果，四个信号齐全
     */
    public LayerAssignment detect(String sourcePath, TypeDeclaration type) {
        String normalizedPath = normalizePath(sourcePath);
        return new LayerAssignment(
                type.qualifiedName(),
                type.packageName() == null ? "" : type.packageName(),
                sourcePath,
                pathSignal(normalizedPath),
                LayerKind.fromAnnotations(type.annotations()),
                LayerKind.fromPackageName(type.packageName()),
                LayerKind.fromTypeName(type.name()));
    }

    /**
     * 识别整个工程。
     *
     * <p>入参用「文件路径 → 该文件的类型声明」而不是先摊平成一个大列表：路径是判定测试代码的
     * 唯一依据，摊平之后就找不回来了。
     *
     * @param typesBySourcePath 文件路径 → 类型声明；顺序不影响结果
     */
    public LayerReport detect(Map<String, List<TypeDeclaration>> typesBySourcePath) {
        List<LayerAssignment> assignments = new ArrayList<>();
        typesBySourcePath.forEach((sourcePath, types) -> {
            for (TypeDeclaration type : types) {
                assignments.add(detect(sourcePath, type));
            }
        });
        return new LayerReport(assignments);
    }

    private static LayerKind pathSignal(String normalizedPath) {
        return normalizedPath != null && normalizedPath.contains(TEST_PATH_MARKER) ? LayerKind.TEST : null;
    }

    private static String normalizePath(String sourcePath) {
        return sourcePath == null ? null : sourcePath.replace('\\', '/');
    }
}
