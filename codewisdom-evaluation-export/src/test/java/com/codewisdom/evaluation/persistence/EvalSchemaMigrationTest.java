package com.codewisdom.evaluation.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("T-105 评测报告建表")
class EvalSchemaMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Flyway 建表 t_eval_report 且历史表独立")
    void schemaApplied() {
        assertTableExists("t_eval_report");
        assertTableExists("flyway_schema_history_evaluation_export");

        Integer applied = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history_evaluation_export WHERE success = TRUE",
                Integer.class);
        assertThat(applied).isGreaterThanOrEqualTo(1);
    }

    private void assertTableExists(String table) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ?",
                Integer.class, table);
        assertThat(count).as("表 %s 应存在", table).isEqualTo(1);
    }
}
