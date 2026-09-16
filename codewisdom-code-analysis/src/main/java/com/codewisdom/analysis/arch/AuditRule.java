package com.codewisdom.analysis.arch;

import com.codewisdom.analysis.domain.AuditIssue;
import com.codewisdom.analysis.parser.CallGraph;
import com.codewisdom.analysis.parser.ParseHandle;
import com.codewisdom.analysis.parser.SourceStructure;
import org.treesitter.TSNode;

import java.util.List;

/**
 * 一条审计规则。
 *
 * <p><b>可插拔的机制就是 Spring</b>：实现本接口 + 打 {@code @Component}（或在配置类里
 * {@code @Bean}），{@link AuditEngine} 构造时通过 {@code List<AuditRule>} 拿到全部实现，
 * 引擎自身不认识任何具体规则。加规则不需要改引擎、不需要改注册表——这才是「可插拔」，
 * 而不是「在一个 switch 里多加一个分支」。
 *
 * <p>规则实现约定：
 * <ul>
 *   <li><b>只读</b>：规则不得修改语法树或任何共享状态。同一批规则会被同一份句柄反复调用。</li>
 *   <li><b>不抛异常</b>：引擎不逐条 catch。分析器静默吞掉规则异常会让人以为「这条规则没问题」，
 *       比直接失败更危险。规则内部遇到处理不了的节点就跳过。</li>
 *   <li><b>每个 {@link AuditIssue} 必须带齐六个字段</b>（由 {@code AuditIssue} 的构造校验兜底）。</li>
 * </ul>
 */
public interface AuditRule {

    /** 规则 ID，形如 {@code CW-NULL-001}，用于回溯是哪条规则报的。 */
    String id();

    /** 该规则产出的问题分类。 */
    AuditIssue.IssueCategory category();

    /**
     * 该规则产出的<b>基准</b>风险等级。
     *
     * <p>只是基准：同一条规则内部可以按命中形态给不同等级（例如硬编码密码是「高危」、
     * 硬编码连接串是「中危」）。这个方法的用途是让调用方在<b>不执行规则</b>的情况下
     * 也能知道这条规则会不会产生高危问题。
     */
    AuditIssue.RiskLevel riskLevel();

    /**
     * 检查一个文件，产出问题列表；没有问题返回空列表。
     *
     * @param context 本次分析的上下文
     */
    List<AuditIssue> inspect(Context context);

    /**
     * 规则可见的分析上下文。
     *
     * @param sourcePath 文件路径（工程内相对路径）
     * @param handle     已解析的句柄，<b>由调用方负责关闭</b>，规则不得关闭它
     * @param structure  该文件的结构化抽取结果（类型与方法声明）
     * @param callGraph  工程级调用图；<b>可能为 {@code null}</b>（单文件分析时没有）。
     *                   依赖调用图的规则必须先判空，没有图时返回空列表而不是抛异常
     */
    record Context(String sourcePath, ParseHandle handle, SourceStructure structure, CallGraph callGraph) {

        /** 取节点对应源码。 */
        public String text(TSNode node) {
            return handle.text(node);
        }

        /** 节点起始行号（1-based）。 */
        public int lineOf(TSNode node) {
            return handle.startLine(node);
        }

        /** 深度优先收集指定类型的全部节点。 */
        public List<TSNode> findAll(String nodeType) {
            return handle.findAll(nodeType);
        }

        /** 直接子节点中指定类型的节点。 */
        public List<TSNode> findChildren(TSNode parent, String nodeType) {
            return handle.findChildren(parent, nodeType);
        }

        /** 是否有工程级调用图可用。 */
        public boolean hasCallGraph() {
            return callGraph != null;
        }

        /** 某方法在工程内是否存在调用点。没有调用图时一律返回 {@code true}。 */
        public boolean isCalled(String methodId) {
            return callGraph == null || !callGraph.callersOf(methodId).isEmpty();
        }
    }
}
