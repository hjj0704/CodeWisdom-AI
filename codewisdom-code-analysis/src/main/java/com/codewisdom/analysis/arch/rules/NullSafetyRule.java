package com.codewisdom.analysis.arch.rules;

import com.codewisdom.analysis.arch.AuditRule;
import com.codewisdom.analysis.domain.AuditIssue;
import org.springframework.stereotype.Component;
import org.treesitter.TSNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * CW-NULL-001：字符串字面量应放在 {@code equals} 左侧。
 *
 * <p>只认<b>一种</b>形态：{@code 变量.equals("字面量")}。接收者是变量或字段访问，
 * 参数是<b>恰好一个</b>字符串字面量。命中后建议改成 {@code "字面量".equals(变量)}
 * 或 {@code Objects.equals(a, b)}。
 *
 * <h2>为什么只认这一种</h2>
 * 「接收者可能为 null」本身需要类型推断或空值流分析，本模块不做。但上面这个形态
 * <b>不需要推断就成立</b>：变量可能是 null，而字面量永远不是——所以把字面量放左边是
 * 普适的安全写法，这也是 Java 社区约定俗成的规范（阿里 Java 开发手册把它列在首位）。
 *
 * <p>刻意<b>不</b>报的形态：接收者是 {@code this}、字面量、或方法调用结果。
 * 前两者不可能为 null，后者属于另一类问题（链式解引用），混进来只会把这条规则的
 * 信噪比拉低——一条总在喊狼来了的规则，等于没有规则。
 *
 * <p>等级定为中危而非高危：这是<b>健壮性</b>问题，只有当变量真的为 null 时才触发，
 * 不像硬编码密码那样「确定是坏事」。
 */
@Component
public class NullSafetyRule implements AuditRule {

    /** 可能为 null 的接收者形态：裸标识符与字段访问。 */
    private static final Set<String> VARIABLE_RECEIVERS = Set.of("identifier", "field_access");

    @Override
    public String id() {
        return "CW-NULL-001";
    }

    @Override
    public AuditIssue.IssueCategory category() {
        return AuditIssue.IssueCategory.NULL_SAFETY;
    }

    @Override
    public AuditIssue.RiskLevel riskLevel() {
        return AuditIssue.RiskLevel.MEDIUM;
    }

    @Override
    public List<AuditIssue> inspect(Context context) {
        List<AuditIssue> issues = new ArrayList<>();
        for (TSNode invocation : context.findAll("method_invocation")) {
            if (!"equals".equals(context.handle().nameOf(invocation))) {
                continue;
            }
            TSNode receiver = invocation.getChildByFieldName("object");
            if (isAbsent(receiver) || !VARIABLE_RECEIVERS.contains(receiver.getType())) {
                continue;
            }
            TSNode arguments = invocation.getChildByFieldName("arguments");
            if (isAbsent(arguments) || context.findChildren(arguments, "string_literal").size() != 1) {
                continue;
            }
            issues.add(new AuditIssue(
                    id(),
                    category(),
                    riskLevel(),
                    context.sourcePath(),
                    context.lineOf(invocation),
                    "字符串字面量放在了 equals 的右侧",
                    context.text(invocation).trim(),
                    "接收者 " + context.text(receiver).trim() + " 若为 null 会抛 NullPointerException；"
                            + "把字面量放在左侧（或改用 Objects.equals）可以完全避免"));
        }
        return issues;
    }

    private static boolean isAbsent(TSNode node) {
        return node == null || node.isNull();
    }
}
