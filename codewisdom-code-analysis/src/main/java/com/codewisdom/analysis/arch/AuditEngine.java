package com.codewisdom.analysis.arch;

import com.codewisdom.analysis.domain.AuditIssue;
import com.codewisdom.analysis.parser.CallGraph;
import com.codewisdom.analysis.parser.ParseHandle;
import com.codewisdom.analysis.parser.SourceStructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 审计规则引擎。
 *
 * <p>职责只有一个：<b>把规则跑起来，并把结果整理成稳定的顺序</b>。
 * 它不认识任何具体规则——规则通过构造注入的 {@code List<AuditRule>} 进来，
 * 加规则不需要动这个类（见 {@link AuditRule}）。
 *
 * <h2>输出顺序必须稳定</h2>
 * 结果按「文件路径 → 行号 → 规则 ID」排序。审计结果会入库、会在前端做 diff、
 * 会被拿去和上一轮比对（T-905 那类「这次比上次好了没有」的场景），
 * 顺序一漂移，所有比对都变成噪声。
 *
 * <h2>不做的事</h2>
 * <ul>
 *   <li><b>不吞规则异常</b>：规则抛异常就让它抛。静默跳过会让人以为「这条规则没发现问题」，
 *       而真相是「这条规则根本没跑完」——审计工具里这种混淆比崩溃危险得多。</li>
 *   <li><b>不做去重与合并</b>：同一处代码被两条规则命中是<b>两条问题</b>，
 *       它们指向不同的风险。合并会丢掉其中一条的判断依据。</li>
 *   <li><b>不排序风险等级</b>：只按位置排。要高危优先由调用方按
 *       {@link AuditIssue.RiskLevel} 自行筛，引擎不替使用者决定「先看什么」。</li>
 * </ul>
 */
@Component
public class AuditEngine {

    private static final Logger log = LoggerFactory.getLogger(AuditEngine.class);

    private final List<AuditRule> rules;

    /**
     * @param rules 全部规则实现，由 Spring 注入；<b>允许为空</b>（还没写规则时引擎照样能构造）
     */
    public AuditEngine(List<AuditRule> rules) {
        this.rules = List.copyOf(rules);
        log.info("审计规则引擎已加载 {} 条规则: {}", this.rules.size(),
                this.rules.stream().map(AuditRule::id).toList());
    }

    /** 当前注册的全部规则，按 ID 排序。 */
    public List<AuditRule> rules() {
        return rules.stream().sorted(Comparator.comparing(AuditRule::id)).toList();
    }

    /** 按 ID 取规则；不存在返回 {@code null}。 */
    public AuditRule rule(String ruleId) {
        return rules.stream().filter(rule -> rule.id().equals(ruleId)).findFirst().orElse(null);
    }

    /**
     * 审计一个文件。
     *
     * @param sourcePath 文件路径（工程内相对路径）
     * @param handle     已解析的句柄；<b>本方法不关闭它</b>，调用方负责
     * @param structure  该文件的结构化抽取结果
     * @param callGraph  工程级调用图，可为 {@code null}
     * @return 按位置排序的问题列表
     */
    public List<AuditIssue> audit(String sourcePath,
                                  ParseHandle handle,
                                  SourceStructure structure,
                                  CallGraph callGraph) {
        AuditRule.Context context = new AuditRule.Context(sourcePath, handle, structure, callGraph);
        List<AuditIssue> issues = new ArrayList<>();
        for (AuditRule rule : rules) {
            issues.addAll(rule.inspect(context));
        }
        issues.sort(Comparator.comparing(AuditIssue::filePath)
                .thenComparingInt(AuditIssue::line)
                .thenComparing(AuditIssue::ruleId));
        return List.copyOf(issues);
    }
}
