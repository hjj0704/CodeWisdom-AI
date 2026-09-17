package com.codewisdom.analysis.arch.rules;

import com.codewisdom.analysis.arch.AuditRule;
import com.codewisdom.analysis.domain.AuditIssue;
import org.springframework.stereotype.Component;
import org.treesitter.TSNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * CW-EXC-001：catch 块为空，异常被静默吞掉。
 *
 * <p>判据很硬：{@code catch} 子句的块里<b>一条语句都没有</b>。这是零假阳性的形态——
 * 空块就是空块，没有解释空间。
 *
 * <h2>只写注释算不算「空」</h2>
 * <b>算</b>。注释不是处理，它既不能记日志也不能让上层感知故障。
 * 真正「有意的吞掉」应当写成明确的日志或带说明的重新抛出；
 * 只留一句 {@code // ignore} 的代码，下一个维护者看不出是忘了处理还是真的无所谓。
 * 因此本条规则把「块里只有注释」与「块里什么都没有」一视同仁。
 *
 * <p>刻意<b>不</b>报的形态：catch 块里只有一句 {@code e.printStackTrace()}。
 * 那同样是坏味道（应改用日志框架），但它是「处理得不对」而不是「没处理」，
 * 属于另一条规则的事。把两种混在一起，问题描述就没法写准了。
 *
 * <p>等级中危：异常被吞之后故障在更远的地方以更难查的形式冒出来，
 * 但从代码表面上看不出任何异常。
 */
@Component
public class ExceptionHandlingRule implements AuditRule {

    /** 注释节点。tree-sitter 把注释当 extras，是否算作子节点取决于实现，这里显式排除。 */
    private static final Set<String> COMMENT_TYPES = Set.of("line_comment", "block_comment", "comment");

    @Override
    public String id() {
        return "CW-EXC-001";
    }

    @Override
    public AuditIssue.IssueCategory category() {
        return AuditIssue.IssueCategory.UNCAUGHT_EXCEPTION;
    }

    @Override
    public AuditIssue.RiskLevel riskLevel() {
        return AuditIssue.RiskLevel.LOW;
    }

    @Override
    public List<AuditIssue> inspect(Context context) {
        List<AuditIssue> issues = new ArrayList<>();
        for (TSNode clause : context.findAll("catch_clause")) {
            List<TSNode> blocks = context.findChildren(clause, "block");
            if (blocks.size() != 1 || !isEmptyBody(blocks.get(0))) {
                continue;
            }
            issues.add(new AuditIssue(
                    id(),
                    category(),
                    riskLevel(),
                    context.sourcePath(),
                    context.lineOf(clause),
                    "catch 块为空，异常被静默吞掉",
                    context.text(clause).trim(),
                    "异常被吞掉后故障会在更远的地方以更难排查的形式出现；"
                            + "应至少记录日志，或判断为该忽略时写明原因"));
        }
        return issues;
    }

    /**
     * 块里除了注释与花括号什么都没有。
     *
     * <p><b>必须显式排除花括号</b>：tree-sitter 的 {@code block} 子节点里包含
     * {@code \{} 与 {@code \}} 这两个<b>匿名 token</b>，所以 {@code { }} 的
     * {@code getChildCount()} 是 2 而不是 0。直接判 {@code childCount == 0}
     * 会让这条规则<b>永远不命中</b>，而且不报错——测试里断言「空 catch 必须被检出」才抓得到。
     * （与 T-303 踩到的 {@code formal_parameters} 混入括号是同一类陷阱。）
     */
    private static boolean isEmptyBody(TSNode block) {
        int count = block.getChildCount();
        for (int i = 0; i < count; i++) {
            String type = block.getChild(i).getType();
            if (!COMMENT_TYPES.contains(type) && !"{".equals(type) && !"}".equals(type)) {
                return false;
            }
        }
        return true;
    }
}
