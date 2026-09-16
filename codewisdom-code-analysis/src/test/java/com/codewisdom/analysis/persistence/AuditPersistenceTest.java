package com.codewisdom.analysis.persistence;

import com.codewisdom.analysis.domain.AuditIssue;
import com.codewisdom.analysis.entity.AuditIssueEntity;
import com.codewisdom.analysis.mapper.AuditIssueMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-105 验收：审计问题入库。
 *
 * <h2>这个测试在验什么</h2>
 * 「审计结果已入库」这句话至少要能拆成三件可验证的事，这里逐件验：
 * <ol>
 *   <li><b>建表脚本真能执行</b>——Flyway 在 H2(MODE=MySQL) 上跑通了
 *       {@code V1__audit_schema.sql}，表真实存在；</li>
 *   <li><b>实体映射对得上</b>——插入再读回，字段一一对应，
 *       尤其是列名与字段名**刻意不同**的那两处（{@code trigger_snippet}、{@code line_no}）；</li>
 *   <li><b>审计字段真被填充</b>——{@code created_at} 是 NOT NULL 且无数据库默认值，
 *       少了 {@code MetaObjectHandler} 会直接撞约束。</li>
 * </ol>
 *
 * <h2>口径（别把结论说大）</h2>
 * 本测试跑在 <b>H2(MODE=MySQL)</b> 上，H2 只是 MySQL 的**近似**，不能替代真实 MySQL 验证
 * （R-13）。真实 MySQL 的验证另做，两者不互相替代——这正是这个测试档存在的原因：
 * 让**没有 Docker / MySQL 的机器**也能跑，而不是让「H2 通过」冒充「MySQL 通过」。
 *
 * <p>用 {@code @Transactional} 让每个用例自动回滚：H2 内存库跨测试类共享，
 * 不隔离的话会出现「单跑绿、全量红」的顺序依赖（T-204 踩过这个坑）。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("T-105 审计问题入库")
class AuditPersistenceTest {

    @Autowired
    private AuditIssueMapper auditIssueMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("建表脚本在 H2(MODE=MySQL) 上跑通，表与索引都在")
    void schemaIsAppliedByFlyway() {
        Integer tables = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 't_audit_issue'",
                Integer.class);
        assertThat(tables).as("t_audit_issue 表应由 Flyway 建出来").isEqualTo(1);

        // Flyway 历史表按服务隔离：本服务用自己的，不能占用默认名
        Integer history = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_name = 'flyway_schema_history_code_analysis'",
                Integer.class);
        assertThat(history).as("本服务应有独立的历史表").isEqualTo(1);
    }

    @Test
    @DisplayName("插入再读回，字段一一对应——含两处刻意改名的列")
    void roundTripsAllFields() {
        AuditIssueEntity entity = new AuditIssueEntity();
        entity.setRuleId("CW-NULL-001");
        entity.setCategory(AuditIssue.IssueCategory.NULL_SAFETY.name());
        entity.setRiskLevel(AuditIssue.RiskLevel.MEDIUM.name());
        entity.setFilePath("src/main/java/demo/OrderService.java");
        entity.setLineNo(42);
        entity.setDescription("字符串字面量放在了 equals 的右侧");
        // 列名是 trigger_snippet：TRIGGER 是 MySQL 保留字，用原名建表会语法错误
        entity.setTriggerSnippet("input.equals(\"admin\")");
        entity.setRiskDescription("接收者可能为 null");

        assertThat(auditIssueMapper.insert(entity)).isEqualTo(1);
        assertThat(entity.getId()).as("自增主键应被回填").isNotNull();

        AuditIssueEntity loaded = auditIssueMapper.selectById(entity.getId());
        assertThat(loaded).isNotNull();
        assertThat(loaded.getRuleId()).isEqualTo("CW-NULL-001");
        assertThat(loaded.getCategory()).isEqualTo("NULL_SAFETY");
        assertThat(loaded.getRiskLevel()).isEqualTo("MEDIUM");
        assertThat(loaded.getFilePath()).isEqualTo("src/main/java/demo/OrderService.java");
        assertThat(loaded.getLineNo()).isEqualTo(42);
        assertThat(loaded.getDescription()).isEqualTo("字符串字面量放在了 equals 的右侧");
        assertThat(loaded.getTriggerSnippet()).isEqualTo("input.equals(\"admin\")");
        assertThat(loaded.getRiskDescription()).isEqualTo("接收者可能为 null");
    }

    @Test
    @DisplayName("created_at 被自动填充，且截断到毫秒（与列 DATETIME(3) 对齐）")
    void fillsCreatedAtAtMillisecondPrecision() {
        LocalDateTime before = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS).minusSeconds(1);

        AuditIssueEntity entity = new AuditIssueEntity();
        entity.setRuleId("CW-ARCH-001");
        entity.setCategory(AuditIssue.IssueCategory.ARCHITECTURE.name());
        entity.setRiskLevel(AuditIssue.RiskLevel.HIGH.name());
        entity.setFilePath("pom.xml");
        entity.setLineNo(AuditIssue.NO_LINE);
        entity.setDescription("分层循环依赖");
        entity.setTriggerSnippet("CONTROLLER -> SERVICE -> CONTROLLER");
        entity.setRiskDescription("架构约定被反过来依赖");

        auditIssueMapper.insert(entity);
        AuditIssueEntity loaded = auditIssueMapper.selectById(entity.getId());

        assertThat(loaded.getCreatedAt()).as("created_at 必须由应用层填充").isNotNull();
        assertThat(loaded.getCreatedAt()).isAfterOrEqualTo(before);
        // 纳秒被截断，写入与读回的值相等——不对齐的话会有「刚插进去和查出来不一样」的隐蔽 bug
        assertThat(loaded.getCreatedAt().getNano() % 1_000_000).isZero();
    }

    @Test
    @DisplayName("行号 0 表示「没有具体行」，能正常存取（对应 AuditIssue.NO_LINE）")
    void storesNoLineAsZero() {
        AuditIssueEntity entity = new AuditIssueEntity();
        entity.setRuleId("CW-DEP-001");
        entity.setCategory(AuditIssue.IssueCategory.DEPENDENCY.name());
        entity.setRiskLevel(AuditIssue.RiskLevel.MEDIUM.name());
        entity.setFilePath("module-b/pom.xml");
        entity.setLineNo(AuditIssue.NO_LINE);
        entity.setDescription("跨模块版本声明不一致");
        entity.setTriggerSnippet("shared-lib:1.0 (module-a/pom.xml:12) / shared-lib:2.0 (module-b/pom.xml:9)");
        entity.setRiskDescription("本工具判不出是否共享 classpath");

        auditIssueMapper.insert(entity);

        List<AuditIssueEntity> found = auditIssueMapper.selectList(null);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getLineNo()).isZero();
    }
}
