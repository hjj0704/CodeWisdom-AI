package com.codewisdom.analysis.arch.rules;

import com.codewisdom.analysis.arch.AuditRule;
import com.codewisdom.analysis.domain.AuditIssue;
import com.codewisdom.analysis.domain.MethodDeclaration;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * CW-DEAD-001：私有方法在工程内没有任何调用点。
 *
 * <p>这是四条规则里唯一<b>需要工程级信息</b>的一条——单文件看不出一个私有方法有没有人调，
 * 必须靠 T-304 的调用图。没有调用图时直接返回空列表，不猜（见
 * {@link AuditRule.Context#isCalled}）。
 *
 * <h2>为什么排除这些方法</h2>
 * <table>
 *   <tr><th>排除项</th><th>原因</th></tr>
 *   <tr><td>构造器</td><td>私有构造器常被工具类用来禁止实例化，本来就不该被调用</td></tr>
 *   <tr><td>{@code @PostConstruct} / {@code @PreDestroy}</td>
 *       <td>容器回调，源码里没有调用点</td></tr>
 *   <tr><td>{@code @Scheduled} / {@code @EventListener}</td>
 *       <td>框架按调度或事件触发，源码里没有调用点</td></tr>
 * </table>
 * 不排除这些，规则会稳定地把框架回调报成死代码——而框架回调恰恰是工程里最不能删的方法。
 *
 * <p>等级低危：可能是真没用，也可能只是通过反射调用。这条规则的产出<b>必须人工确认</b>，
 * 直接按它删方法的后果比留着死代码严重得多。
 *
 * <h2>已知粒度上限</h2>
 * 调用图的节点标识只到方法名、不含参数（T-304 的约定），因此<b>同名重载会互相掩护</b>：
 * 只要有一个 {@code helper()} 被调用，同名的 {@code helper(String)} 就不会被报出来。
 * 这是调用图粒度决定的上限，不是本规则的缺陷。
 */
@Component
public class DeadCodeRule implements AuditRule {

    /** 框架回调注解：这些方法的调用方在框架里，源码中找不到调用点。 */
    private static final Set<String> FRAMEWORK_CALLBACKS = Set.of(
            "PostConstruct", "PreDestroy", "Scheduled", "EventListener", "EventListenerAdapter",
            "TransactionalEventListener", "RabbitListener", "KafkaListener", "StreamListener");

    @Override
    public String id() {
        return "CW-DEAD-001";
    }

    @Override
    public AuditIssue.IssueCategory category() {
        return AuditIssue.IssueCategory.DEAD_CODE;
    }

    @Override
    public AuditIssue.RiskLevel riskLevel() {
        return AuditIssue.RiskLevel.LOW;
    }

    @Override
    public List<AuditIssue> inspect(Context context) {
        if (!context.hasCallGraph()) {
            return List.of();
        }
        List<AuditIssue> issues = new ArrayList<>();
        for (MethodDeclaration method : context.structure().methods()) {
            if (!isCandidate(method)) {
                continue;
            }
            String methodId = method.ownerQualifiedName() + "#" + method.name();
            if (context.isCalled(methodId)) {
                continue;
            }
            issues.add(new AuditIssue(
                    id(),
                    category(),
                    riskLevel(),
                    context.sourcePath(),
                    method.startLine(),
                    "私有方法 " + method.name() + "() 在工程内没有任何调用点",
                    method.signature(),
                    "疑似死代码。注意：反射调用、Spring 容器回调、以及仅在测试中调用的私有方法"
                            + "都会落进这条规则的误报范围，删除前必须人工确认"));
        }
        return issues;
    }

    private static boolean isCandidate(MethodDeclaration method) {
        // 私有构造器是「禁止实例化」的常见写法，不是死代码
        if (method.isConstructor()) {
            return false;
        }
        if (!method.modifiers().contains("private")) {
            return false;
        }
        // 没有方法体的私有方法不存在，防御性跳过
        if (!method.hasBody()) {
            return false;
        }
        return method.annotations().stream().noneMatch(FRAMEWORK_CALLBACKS::contains);
    }
}
