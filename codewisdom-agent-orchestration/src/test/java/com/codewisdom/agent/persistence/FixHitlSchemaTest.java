package com.codewisdom.agent.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("T-105 修复建议与 HITL 建表")
class FixHitlSchemaTest {

    private static final List<String> TABLES = List.of("t_doc_record", "t_fix_record", "t_hitl_review");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Flyway V1+V2 全部业务表可查询")
    void allAgentTablesExist() {
        for (String table : TABLES) {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ?",
                    Integer.class, table);
            assertThat(exists).as("表 %s 应存在", table).isEqualTo(1);
        }

        Integer migrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history_agent_orchestration WHERE success = TRUE",
                Integer.class);
        assertThat(migrations).isGreaterThanOrEqualTo(3);
    }
}
