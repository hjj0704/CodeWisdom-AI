package com.codewisdom.analysis.arch;

import com.codewisdom.analysis.domain.AuditIssue;
import com.codewisdom.analysis.domain.AuditReport;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 审计流水线：把各路审计产出汇总成一份 {@link AuditReport}。
 *
 * <h2>它做什么、不做什么</h2>
 * 阶段 5 的四路产出<b>已经都是 {@code List<AuditIssue>}</b>：
 * <pre>
 * AuditEngine.audit(...)                     代码规则（CW-NULL / CW-EXC / CW-HARDCODE / CW-DEAD）
 * PomConflictAnalyzer.analyze(...)           pom 依赖冲突（CW-DEP-*）
 * RequirementsConflictAnalyzer.analyze(...)  requirements 依赖冲突（CW-REQ-*）
 * ArchRiskAnalyzer.analyze(...)              架构隐患（CW-ARCH-*）
 * </pre>
 * 本类<b>只做汇总</b>：去重 → 风险分级 → 稳定排序。
 * <b>不重新实现各分析器已经做过的字段校验与排序</b>，也不再跑一遍任何分析——
 * 那属于各分析器的职责，重复一遍只会让两处实现慢慢漂移。
 *
 * <h2>为什么汇总时要去重</h2>
 * 同一个文件被喂给同一路分析器两次（重跑、多模块扫描重叠）会产出<b>完全相同</b>的问题。
 * 这类重复不是「两个不同的问题」，必须合并，否则风险计数会被灌水，
 * 而风险计数正是这份报告最直接被人看的东西。
 *
 * <p>注意与 T-501 的约定不冲突：那里说的「引擎不做去重」指的是
 * <b>不同规则</b>命中同一行代码——那是两条问题、两个风险，合并会丢依据。
 * 这里去的是<b>完全相同</b>的条目（规则、文件、行号、描述四项全同）。
 *
 * <h2>不落库</h2>
 * 「问题模型入库」需要 code-analysis 先有持久化层，而「各服务接入 MySQL + MyBatis-Plus +
 * Flyway」属于 <b>T-105（挂起中）</b>的职责。本类因此<b>只产出内存中的报告</b>，
 * 由上层决定怎么持久化——不在这里半接一个数据源，那会让 T-105 落地时更难对齐。
 */
@Component
public class AuditPipeline {

    /**
     * 汇总多路审计产出。
     *
     * @param issueGroups 各路产出；允许为空、允许为 null 元素
     * @return 去重并按风险分级后的报告
     */
    @SafeVarargs
    public final AuditReport aggregate(List<AuditIssue>... issueGroups) {
        List<AuditIssue> merged = new ArrayList<>();
        if (issueGroups != null) {
            for (List<AuditIssue> group : issueGroups) {
                if (group != null) {
                    merged.addAll(group);
                }
            }
        }
        return aggregate(merged);
    }

    /**
     * 汇总一批审计问题。
     *
     * @param issues 待汇总的问题；允许为空
     * @return 去重并按风险分级后的报告
     */
    public AuditReport aggregate(Collection<AuditIssue> issues) {
        // LinkedHashMap 保留首次出现的顺序，最终排序由 AuditReport 统一负责
        Map<String, AuditIssue> unique = new LinkedHashMap<>();
        if (issues != null) {
            for (AuditIssue issue : issues) {
                if (issue != null) {
                    unique.merge(keyOf(issue), issue, AuditPipeline::higherRisk);
                }
            }
        }
        return new AuditReport(new ArrayList<>(unique.values()));
    }

    /**
     * 去重键：规则 + 文件 + 行号 + 描述。
     *
     * <p><b>刻意不含风险等级</b>：同一条发现被两路产出时若等级不一致，
     * 那正是最该被看见的异常——把等级放进键里，两条就会同时留下、当作两个问题，
     * 反而把矛盾掩盖过去了。
     */
    private static String keyOf(AuditIssue issue) {
        return issue.ruleId() + "|" + issue.filePath() + "|" + issue.line() + "|" + issue.description();
    }

    /**
     * 同一处发现对应两条记录时，<b>取风险更高的那条</b>。
     *
     * <p>{@link AuditIssue.RiskLevel} 的声明顺序是 高危 → 中危 → 低危，
     * ordinal 越小等级越高。取高不取低是为了<b>宁可报重也不漏报</b>：
     * 审计工具把高危降级显示，比重复报一次危险得多。
     */
    private static AuditIssue higherRisk(AuditIssue first, AuditIssue second) {
        return first.riskLevel().ordinal() <= second.riskLevel().ordinal() ? first : second;
    }
}
