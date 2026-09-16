package com.codewisdom.agent.service.fix;

import com.codewisdom.agent.domain.fix.FixModels.HitlDecision;
import com.codewisdom.agent.domain.fix.FixModels.HitlReviewState;
import com.codewisdom.agent.domain.fix.FixModels.HitlReviewStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("T-703 HITL 状态机")
class HitlStateTest {

    private final HitlReviewService service = new HitlReviewService();

    @Test
    @DisplayName("确认后终止且通过")
    void approveTerminates() {
        HitlReviewState end = service.submit(
                HitlReviewState.initial(),
                new HitlDecision(HitlReviewStatus.APPROVED));

        assertThat(end.approved()).isTrue();
        assertThat(end.terminated()).isTrue();
    }

    @Test
    @DisplayName("驳回进入下一轮")
    void rejectIncrementsRound() {
        HitlReviewState afterReject = service.submit(
                HitlReviewState.initial(),
                new HitlDecision(HitlReviewStatus.REJECTED));

        assertThat(afterReject.round()).isEqualTo(1);
        assertThat(afterReject.terminated()).isFalse();
        assertThat(afterReject.lastStatus()).isEqualTo(HitlReviewStatus.REJECTED);
    }

    @Test
    @DisplayName("超过最大轮次自动终止且不抛异常")
    void maxRoundsAutoTerminate() {
        HitlReviewState state = HitlReviewState.initial();
        for (int i = 0; i < HitlReviewService.MAX_ROUNDS; i++) {
            state = service.submit(state, new HitlDecision(HitlReviewStatus.REJECTED));
        }
        assertThat(state.terminated()).isTrue();
        assertThat(state.approved()).isFalse();
        assertThat(state.round()).isEqualTo(HitlReviewService.MAX_ROUNDS);
    }
}
