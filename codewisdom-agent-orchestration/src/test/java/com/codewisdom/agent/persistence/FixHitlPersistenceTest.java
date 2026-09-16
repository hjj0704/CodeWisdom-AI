package com.codewisdom.agent.persistence;

import com.codewisdom.agent.domain.fix.FixModels;
import com.codewisdom.agent.entity.FixRecordEntity;
import com.codewisdom.agent.entity.HitlReviewEntity;
import com.codewisdom.agent.mapper.FixRecordMapper;
import com.codewisdom.agent.mapper.HitlReviewMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("T-105 修复建议与 HITL 入库")
class FixHitlPersistenceTest {

    @Autowired
    private FixRecordMapper fixRecordMapper;

    @Autowired
    private HitlReviewMapper hitlReviewMapper;

    @Test
    @DisplayName("t_fix_record 插入再读回，含 diff_json 与审计字段")
    void fixRecordRoundTrip() {
        FixRecordEntity entity = new FixRecordEntity();
        entity.setProjectId(10L);
        entity.setIssueKey("CW-NULL-001|src/Foo.java|12");
        entity.setRuleId("CW-NULL-001");
        entity.setFilePath("src/Foo.java");
        entity.setLineNo(12);
        entity.setRiskLevel(FixModels.RiskLevel.HIGH.name());
        entity.setSuggestion("在调用前增加 null 检查");
        entity.setRationale("避免 NPE");
        entity.setDiffChanged(true);
        entity.setDiffJson("{\"filePath\":\"src/Foo.java\",\"changed\":true}");

        assertThat(fixRecordMapper.insert(entity)).isEqualTo(1);

        FixRecordEntity loaded = fixRecordMapper.selectById(entity.getId());
        assertThat(loaded.getIssueKey()).isEqualTo("CW-NULL-001|src/Foo.java|12");
        assertThat(loaded.getDiffChanged()).isTrue();
        assertThat(loaded.getDiffJson()).contains("changed");
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
        assertThat(loaded.getCreatedAt().getNano() % 1_000_000).isZero();
    }

    @Test
    @DisplayName("t_fix_record 更新时 updated_at 会刷新")
    void fixRecordUpdateRefreshesUpdatedAt() {
        FixRecordEntity entity = baseFixRecord();
        fixRecordMapper.insert(entity);

        LocalDateTime firstUpdated = fixRecordMapper.selectById(entity.getId()).getUpdatedAt();
        entity.setSuggestion("修订后的建议");
        fixRecordMapper.updateById(entity);

        LocalDateTime secondUpdated = fixRecordMapper.selectById(entity.getId()).getUpdatedAt();
        assertThat(secondUpdated).isAfterOrEqualTo(firstUpdated);
        assertThat(fixRecordMapper.selectById(entity.getId()).getCreatedAt()).isEqualTo(entity.getCreatedAt());
    }

    @Test
    @DisplayName("t_hitl_review 每轮审核一行，字段对齐")
    void hitlReviewRoundTrip() {
        FixRecordEntity fix = baseFixRecord();
        fixRecordMapper.insert(fix);

        HitlReviewEntity review = new HitlReviewEntity();
        review.setProjectId(10L);
        review.setSessionKey("sess-001");
        review.setFixRecordId(fix.getId());
        review.setRound(1);
        review.setDecisionStatus(FixModels.HitlReviewStatus.REJECTED.name());
        review.setApproved(false);
        review.setIsTerminated(false);

        assertThat(hitlReviewMapper.insert(review)).isEqualTo(1);

        HitlReviewEntity loaded = hitlReviewMapper.selectById(review.getId());
        assertThat(loaded.getSessionKey()).isEqualTo("sess-001");
        assertThat(loaded.getRound()).isEqualTo(1);
        assertThat(loaded.getDecisionStatus()).isEqualTo("REJECTED");
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getCreatedAt().getNano() % 1_000_000).isZero();
    }

    private static FixRecordEntity baseFixRecord() {
        FixRecordEntity entity = new FixRecordEntity();
        entity.setProjectId(1L);
        entity.setIssueKey("CW-NULL-001|src/Bar.java|0");
        entity.setRuleId("CW-NULL-001");
        entity.setFilePath("src/Bar.java");
        entity.setLineNo(0);
        entity.setRiskLevel(FixModels.RiskLevel.MEDIUM.name());
        entity.setSuggestion("建议");
        entity.setRationale("理由");
        entity.setDiffChanged(false);
        return entity;
    }
}
