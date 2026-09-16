package com.codewisdom.evaluation.service;

import com.codewisdom.evaluation.domain.RunCapability;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("T-801 运行能力判定")
class RunJudgeTest {

    private final RunCapabilityJudge judge = new RunCapabilityJudge();

    @Test
    @DisplayName("无中间件依赖判为轻量")
    void lightweightProject() {
        assertThat(judge.judge(List.of("spring-boot-starter-web", "lombok"))).isEqualTo(RunCapability.LIGHTWEIGHT);
    }

    @Test
    @DisplayName("含 Redis/MySQL/MQ 判为重型")
    void heavyProject() {
        assertThat(judge.judge(List.of("spring-boot-starter-data-redis"))).isEqualTo(RunCapability.HEAVY);
        assertThat(judge.judge(List.of("mysql-connector-j"))).isEqualTo(RunCapability.HEAVY);
        assertThat(judge.judge(List.of("spring-rabbit"))).isEqualTo(RunCapability.HEAVY);
    }
}
