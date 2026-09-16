package com.codewisdom.analysis.domain;

/**
 * 审计问题的<b>统一模型</b>。
 *
 * <p>阶段 5 的所有产出——代码缺陷、依赖冲突、架构隐患——都收敛成这一个类型
 * （{@code docs/acceptance.md} 阶段 5 明确要求「架构隐患与阶段 4 结果打通，使用统一问题模型」）。
 * 统一到「同一个 DTO」而不是「几个长得很像的 DTO」，前端与入库才只需要一套渲染与一套表。
 *
 * <h2>六个字段是硬约束，不是建议</h2>
 * {@code docs/acceptance.md} 要求「每条问题必须包含：文件路径、行号、问题描述、风险说明、
 * 触发场景、风险等级」。因此这些字段在构造时<b>强制校验</b>——缺一个就造不出对象，
 * 而不是等到前端渲染时才发现是空的。把约束放在构造函数里，是让编译器与单元测试替人记住它。
 *
 * <h2>风险等级只有三个值</h2>
 * {@link RiskLevel} 是枚举，从类型层面堵死第四种取值。
 *
 * @param ruleId          规则 ID，如 {@code CW-NULL-001}，用于回溯是哪条规则报的
 * @param category        问题分类
 * @param riskLevel       风险等级，仅 高危 / 中危 / 低危
 * @param filePath        文件路径（工程内相对路径）
 * @param line            行号（1-based）；<b>确实没有具体行时用 {@link #NO_LINE}</b>，
 *                        不要用 0 或 -1 这类魔法值
 * @param description     问题描述
 * @param trigger         触发场景：触发这条判断的代码片段
 * @param riskDescription 风险说明：为什么这是问题、会导致什么后果
 */
public record AuditIssue(String ruleId,
                         IssueCategory category,
                         RiskLevel riskLevel,
                         String filePath,
                         int line,
                         String description,
                         String trigger,
                         String riskDescription) {

    /** 没有具体行号时的占位。用显式常量而不是 0/-1，避免与「第 0 行」混淆。 */
    public static final int NO_LINE = 0;

    public AuditIssue {
        requireText(ruleId, "ruleId");
        requireText(filePath, "filePath");
        requireText(description, "description");
        requireText(trigger, "trigger");
        requireText(riskDescription, "riskDescription");
        if (line < NO_LINE) {
            throw new IllegalArgumentException("行号不能为负: " + line);
        }
        if (category == null || riskLevel == null) {
            throw new IllegalArgumentException("问题分类与风险等级不能为空");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("审计问题的 " + field + " 不能为空");
        }
    }

    /** 是否高危。 */
    public boolean isHighRisk() {
        return riskLevel == RiskLevel.HIGH;
    }

    /** 是否有具体行号。 */
    public boolean hasLine() {
        return line != NO_LINE;
    }

    /** 可读单行摘要，便于日志与调试。 */
    public String describe() {
        return "[" + riskLevel.displayName() + "] " + ruleId + " "
                + filePath + (hasLine() ? ":" + line : "") + " " + description;
    }

    /**
     * 风险等级。
     *
     * <p>{@code docs/acceptance.md} 规定「风险等级仅允许 高危/中危/低危 三值」——
     * 枚举从类型层面保证这一点，不给第四种取值留口子。
     */
    public enum RiskLevel {

        /** 高危 */
        HIGH("高危"),

        /** 中危 */
        MEDIUM("中危"),

        /** 低危 */
        LOW("低危");

        private final String displayName;

        RiskLevel(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    /**
     * 问题分类。
     *
     * <p>取值覆盖阶段 5 的全部审计面：前四项是代码级缺陷（T-501），
     * {@link #DEPENDENCY} 是依赖冲突（T-502/T-503），{@link #ARCHITECTURE} 是架构隐患（T-504）。
     */
    public enum IssueCategory {

        /** 空指针风险 */
        NULL_SAFETY("空指针风险"),

        /** 未捕获异常 / 异常被吞掉 */
        UNCAUGHT_EXCEPTION("异常处理"),

        /** 硬编码 */
        HARDCODED("硬编码"),

        /** 死代码 */
        DEAD_CODE("死代码"),

        /** 依赖冲突 */
        DEPENDENCY("依赖冲突"),

        /** 架构隐患 */
        ARCHITECTURE("架构隐患");

        private final String displayName;

        IssueCategory(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }
}
