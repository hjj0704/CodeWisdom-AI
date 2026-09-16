package com.codewisdom.analysis.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * 审计报告：把多路审计产出汇总成一份可交付的结果。
 *
 * <p>构造时按「文件 → 行号 → 规则 ID」排序并冻结，与各分析器的排序口径一致——
 * 否则报告会随汇总顺序漂移，前端 diff 与两轮比对都没法做。
 *
 * <h2>风险分级是「固定三档」而不是「列出出现过的」</h2>
 * {@link #countByRiskLevel()} <b>恒定返回 高危 / 中危 / 低危 三行</b>，没有的那档是 0。
 * 只列出现过的档位看着更精简，但「高危 0」与「压根没统计高危」在报告里长得一样——
 * 审计报告最忌讳这种歧义。
 */
public record AuditReport(List<AuditIssue> issues) {

    public AuditReport {
        List<AuditIssue> sorted = new ArrayList<>(issues);
        sorted.sort(Comparator.comparing(AuditIssue::filePath)
                .thenComparingInt(AuditIssue::line)
                .thenComparing(AuditIssue::ruleId));
        issues = List.copyOf(sorted);
    }

    /** 问题总数。 */
    public int total() {
        return issues.size();
    }

    /** 是否没有任何问题。 */
    public boolean isEmpty() {
        return issues.isEmpty();
    }

    /** 是否含高危问题。 */
    public boolean hasHighRisk() {
        return issues.stream().anyMatch(AuditIssue::isHighRisk);
    }

    /**
     * 整体风险等级：取报告里最高的一档。
     *
     * <p>空报告返回 {@link Optional#empty()}——<b>不返回「低危」</b>：
     * 「没有发现问题」与「问题都是低危」是两回事，前者不该被安上一个等级。
     */
    public Optional<AuditIssue.RiskLevel> highestRiskLevel() {
        return issues.stream()
                .map(AuditIssue::riskLevel)
                .min(Comparator.comparingInt(Enum::ordinal));
    }

    /**
     * 按风险等级计数，<b>恒定三行</b>（高危 / 中危 / 低危），无该档时为 0。
     *
     * @see AuditReport 类注释
     */
    public Map<AuditIssue.RiskLevel, Long> countByRiskLevel() {
        Map<AuditIssue.RiskLevel, Long> counts = new LinkedHashMap<>();
        for (AuditIssue.RiskLevel level : AuditIssue.RiskLevel.values()) {
            counts.put(level, 0L);
        }
        for (AuditIssue issue : issues) {
            counts.merge(issue.riskLevel(), 1L, Long::sum);
        }
        return Collections.unmodifiableMap(counts);
    }

    /** 按规则 ID 计数，按规则 ID 升序。 */
    public Map<String, Long> countByRule() {
        return countsBy(AuditIssue::ruleId);
    }

    /** 按文件计数，按文件路径升序。 */
    public Map<String, Long> countByFile() {
        return countsBy(AuditIssue::filePath);
    }

    private Map<String, Long> countsBy(Function<AuditIssue, String> key) {
        Map<String, Long> counts = new TreeMap<>();
        for (AuditIssue issue : issues) {
            counts.merge(key.apply(issue), 1L, Long::sum);
        }
        return Collections.unmodifiableMap(counts);
    }

    /** 指定等级的问题。 */
    public List<AuditIssue> issuesOf(AuditIssue.RiskLevel level) {
        return issues.stream().filter(issue -> issue.riskLevel() == level).toList();
    }

    /** 指定规则的问题。 */
    public List<AuditIssue> issuesOf(String ruleId) {
        return issues.stream().filter(issue -> issue.ruleId().equals(ruleId)).toList();
    }

    /** 报告涉及的全部文件，按路径升序。 */
    public Set<String> files() {
        Set<String> files = new LinkedHashSet<>();
        for (AuditIssue issue : issues) {
            files.add(issue.filePath());
        }
        return Collections.unmodifiableSet(files);
    }

    /** 可读摘要，便于直接写进日志或展示。 */
    public String describe() {
        if (isEmpty()) {
            return "未发现审计问题";
        }
        Map<AuditIssue.RiskLevel, Long> counts = countByRiskLevel();
        return "共 " + total() + " 条审计问题：高危 " + counts.get(AuditIssue.RiskLevel.HIGH)
                + " / 中危 " + counts.get(AuditIssue.RiskLevel.MEDIUM)
                + " / 低危 " + counts.get(AuditIssue.RiskLevel.LOW)
                + "，涉及 " + files().size() + " 个文件。"
                + "（辅助分析结果，需人工确认）";
    }
}
