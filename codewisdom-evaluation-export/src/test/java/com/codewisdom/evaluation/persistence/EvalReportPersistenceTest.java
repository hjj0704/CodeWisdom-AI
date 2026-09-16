package com.codewisdom.evaluation.persistence;

import com.codewisdom.evaluation.entity.EvalReportEntity;
import com.codewisdom.evaluation.mapper.EvalReportMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("T-105 评测报告入库")
class EvalReportPersistenceTest {

    @Autowired
    private EvalReportMapper evalReportMapper;

    @Test
    @DisplayName("t_eval_report 插入再读回，JSON 与 Markdown 字段完整")
    void roundTrip() {
        EvalReportEntity entity = new EvalReportEntity();
        entity.setProjectId(20L);
        entity.setDatasetVersion("1.0");
        entity.setSampleId("defect-001");
        entity.setStatus("COMPLETED");
        entity.setMetricsJson("{\"recall\":0.85,\"falsePositiveRate\":0.1}");
        entity.setTraceJson("{\"node\":\"audit\",\"error\":null}");
        entity.setReportMarkdown("# 评测报告\n召回率 85%");

        assertThat(evalReportMapper.insert(entity)).isEqualTo(1);

        EvalReportEntity loaded = evalReportMapper.selectById(entity.getId());
        assertThat(loaded.getDatasetVersion()).isEqualTo("1.0");
        assertThat(loaded.getMetricsJson()).contains("recall");
        assertThat(loaded.getTraceJson()).contains("audit");
        assertThat(loaded.getReportMarkdown()).startsWith("# 评测报告");
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getCreatedAt().getNano() % 1_000_000).isZero();
    }
}
