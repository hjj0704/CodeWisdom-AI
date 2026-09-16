package com.codewisdom.analysis.arch.rules;

import com.codewisdom.analysis.arch.AuditRule;
import com.codewisdom.analysis.domain.AuditIssue;
import org.springframework.stereotype.Component;
import org.treesitter.TSNode;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * CW-HARDCODE-001：疑似凭据 / 连接串被硬编码进源码。
 *
 * <p>两类命中，等级不同：
 * <table>
 *   <tr><th>形态</th><th>等级</th><th>例子</th></tr>
 *   <tr><td>{@code 关键字=值} 形式的凭据</td><td>高危</td>
 *       <td>{@code "password=123456"}、{@code "apiKey: abcdef"}</td></tr>
 *   <tr><td>带协议的连接串</td><td>中危</td>
 *       <td>{@code "jdbc:mysql://10.0.0.1:3306/db"}</td></tr>
 * </table>
 *
 * <h2>为什么凭据是高危</h2>
 * 硬编码的密码 / token 一旦进版本库就<b>永久留在历史里</b>，删掉当前文件也没用；
 * 而且它不受任何配置管理，轮换要改代码重新发版。这不是「可能出问题」，
 * 是「已经出问题了，只是还没被利用」。连接串是中危：它同样应该外置，
 * 但泄露的是拓扑信息而不是凭据本身。
 *
 * <h2>为什么只认「关键字=值」而不是「含 password 字样」</h2>
 * 后者会把 {@code "password"} 这种字段名字面量、{@code "请输入 password"} 这种提示语
 * 全部误报。加上 {@code =} 或 {@code :} 且后面<b>必须有值</b>之后，
 * 命中的基本就是真正写进去的凭据——这条规则要的是精确，不是覆盖。
 *
 * <h2>不负责的事</h2>
 * 本规则<b>不</b>判断这个值是不是真的有效、是不是测试用的假数据。
 * 命中即报，由人工确认——输出一律按「辅助分析结果，需人工确认」表述。
 */
@Component
public class HardcodedValueRule implements AuditRule {

    /**
     * 凭据形态：关键字 + 分隔符 + 非空值。
     *
     * <p>要求分隔符后必须有非空白字符，否则 {@code "password="} 这种空值也会命中。
     */
    private static final Pattern CREDENTIAL = Pattern.compile(
            "(?i).*(password|passwd|pwd|secret|token|api[-_]?key|access[-_]?key|private[-_]?key)"
                    + "\\s*[=:]\\s*\\S+.*");

    /**
     * 连接串形态：协议 + 主机 + 内容。
     *
     * <p><b>JDBC 的子协议不能漏</b>：真实写法是 {@code jdbc:mysql://host:3306/db}，
     * 子协议夹在 {@code jdbc} 与 {@code ://} 中间。写成 {@code (jdbc|redis|...)://} 会让
     * {@code jdbc:*} 这一支<b>永远匹配不上</b>——而且不报错，只是连接串从不被检出。
     */
    private static final Pattern CONNECTION = Pattern.compile(
            "(?i)^(jdbc(:[a-z0-9]+)?|redis|rediss|mongodb(\\+srv)?|amqp|amqps|kafka|zookeeper)://\\S+.*");

    @Override
    public String id() {
        return "CW-HARDCODE-001";
    }

    @Override
    public AuditIssue.IssueCategory category() {
        return AuditIssue.IssueCategory.HARDCODED;
    }

    /** 基准等级取两者中较高的：这条规则确实会产出高危问题。 */
    @Override
    public AuditIssue.RiskLevel riskLevel() {
        return AuditIssue.RiskLevel.HIGH;
    }

    @Override
    public List<AuditIssue> inspect(Context context) {
        List<AuditIssue> issues = new ArrayList<>();
        for (TSNode literal : context.findAll("string_literal")) {
            String value = unquote(context.text(literal));
            if (CREDENTIAL.matcher(value).matches()) {
                issues.add(issue(context, literal, value, AuditIssue.RiskLevel.HIGH,
                        "疑似凭据被硬编码进源码",
                        "凭据一旦进版本库就永久留在提交历史里，删除当前文件也无法清除；"
                                + "且不受配置管理，轮换需要改代码重新发版。应改为从配置或密钥管理读取"));
            } else if (CONNECTION.matcher(value).matches()) {
                issues.add(issue(context, literal, value, AuditIssue.RiskLevel.MEDIUM,
                        "连接串被硬编码进源码",
                        "连接地址应外置到配置：硬编码会让不同环境必须改代码，"
                                + "也把内网拓扑暴露在源码里"));
            }
        }
        return issues;
    }

    private AuditIssue issue(Context context, TSNode literal, String value,
                             AuditIssue.RiskLevel level, String description, String risk) {
        return new AuditIssue(
                id(),
                category(),
                level,
                context.sourcePath(),
                context.lineOf(literal),
                description,
                context.text(literal).trim(),
                risk);
    }

    /** 去掉字符串字面量两端引号；转义序列原样保留，本规则只做模式匹配，不解析内容。 */
    private static String unquote(String literalText) {
        String text = literalText.trim();
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }
}
