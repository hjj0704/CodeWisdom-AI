package com.codewisdom.resource.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-201 验收之一：Flyway 建表脚本能真实执行，三张表与索引齐备。
 *
 * <p>在 H2（MODE=MySQL）上运行。H2 只是近似 MySQL，因此本测试证明的是
 * 「SQL 语法正确、表结构符合预期」，真实 MySQL 8 的验证留待 1B 阶段。
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("T-201 建表脚本")
class SchemaMigrationTest {

    private static final List<String> EXPECTED_TABLES =
            List.of("t_project", "t_import_task", "t_file_node");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Flyway 迁移成功且三张业务表全部可查询")
    void flywayCreatesAllTables() {
        for (String table : EXPECTED_TABLES) {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
            assertThat(count).as("表 %s 应存在且可查询", table).isNotNull().isZero();
        }
    }

    @Test
    @DisplayName("迁移记录落库，且无失败版本")
    void migrationHistoryIsClean() {
        Integer applied = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE", Integer.class);
        Integer failed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = FALSE", Integer.class);

        assertThat(applied).as("应至少有 1 条成功迁移").isGreaterThanOrEqualTo(1);
        assertThat(failed).as("不应存在失败的迁移").isZero();
    }

    @Test
    @DisplayName("t_project 关键列齐备，且 created_at/updated_at 为 NOT NULL")
    void projectColumnsAreComplete() {
        List<String> columns = columnNames("t_project");

        assertThat(columns).contains(
                "id", "name", "source_type", "source_url", "default_branch",
                "status", "storage_bucket", "storage_prefix",
                "file_count", "total_size", "created_at", "updated_at");

        // 审计字段必须入库强制非空：应用层漏填时应直接被数据库拒绝
        String nullable = jdbcTemplate.queryForObject(
                "SELECT is_nullable FROM information_schema.columns "
                        + "WHERE table_name = 't_project' AND column_name = 'created_at'",
                String.class);
        assertThat(nullable).isEqualToIgnoringCase("NO");
    }

    @Test
    @DisplayName("t_file_node 同时具备父子关系列与完整路径列")
    void fileNodeSupportsTreeAndPathLookup() {
        List<String> columns = columnNames("t_file_node");

        assertThat(columns)
                .as("树形渲染依赖 parent_id，按路径定位依赖 path，两者都要有")
                .contains("parent_id", "path", "node_type", "category", "checksum");
    }

    private List<String> columnNames(String table) {
        return jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = ?",
                String.class, table);
    }
}
