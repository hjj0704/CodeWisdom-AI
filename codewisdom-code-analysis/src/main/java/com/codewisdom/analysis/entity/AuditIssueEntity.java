package com.codewisdom.analysis.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计问题（表 {@code t_audit_issue}）。
 *
 * <p>这是 T-505「问题模型入库」的落地：{@code domain.AuditIssue} 是<b>内存中的模型</b>，
 * 本类是同一条记录<b>落库的形态</b>。两者字段一一对应，转换由 {@code AuditIssueRepository} 负责。
 *
 * <h2>为什么枚举存成 String 而不是枚举字段</h2>
 * 直接声明成 {@code RiskLevel} 字段也可以，但那样会把「枚举怎么持久化」这件事
 * 交给 MyBatis-Plus 的类型处理器，而它的行为随版本变（序号 / 名字 / 需要 {@code IEnum}）。
 * 这里显式存 {@code name()}，转换在仓储层做——**枚举换顺序不会污染历史数据**，
 * 这个性质比少写几行转换代码重要得多。
 *
 * <h2>列名与字段名的三处刻意差异</h2>
 * <ul>
 *   <li>{@code triggerSnippet} → 列名 {@code trigger_snippet}：<b>{@code TRIGGER} 是 MySQL 保留字</b>，
 *       用它做列名建表会直接语法错误。</li>
 *   <li>{@code lineNo} → 列名 {@code line_no}：{@code line} 在部分库里也是保留字，且语义更明确。</li>
 *   <li>{@code createdAt} → 列名 {@code created_at}：靠 {@code map-underscore-to-camel-case} 自动映射。</li>
 * </ul>
 */
@Data
@TableName("t_audit_issue")
public class AuditIssueEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属项目；MVP 阶段流水线还没接项目上下文，允许为空。 */
    private Long projectId;

    /** 规则 ID，如 {@code CW-NULL-001}。 */
    private String ruleId;

    /** 问题分类，存 {@code AuditIssue.IssueCategory} 的 {@code name()}。 */
    private String category;

    /** 风险等级，存 {@code AuditIssue.RiskLevel} 的 {@code name()}，仅 HIGH / MEDIUM / LOW。 */
    private String riskLevel;

    private String filePath;

    /** 行号，1-based；0 表示「确实没有具体行」（对应 {@code AuditIssue.NO_LINE}）。 */
    private Integer lineNo;

    private String description;

    /** 触发场景。列名是 {@code trigger_snippet}，见类注释。 */
    private String triggerSnippet;

    private String riskDescription;

    /** 创建时间。只在插入时填充，更新时永不写回——审计字段必须不可变。 */
    @TableField(fill = FieldFill.INSERT, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
}
