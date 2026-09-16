package com.codewisdom.agent.service.fix;

import com.codewisdom.agent.domain.fix.FixModels.HitlDecision;
import com.codewisdom.agent.domain.fix.FixModels.HitlReviewState;
import com.codewisdom.agent.domain.fix.FixModels.HitlReviewStatus;

import java.util.Objects;

/**
 * HITL 审核状态机（T-703）：确认 / 修改 / 驳回 / 多轮，超轮次自动终止。
 */
public class HitlReviewService {

    public static final int MAX_ROUNDS = 5;

    public HitlReviewState submit(HitlReviewState current, HitlDecision decision) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(decision, "decision");

        if (current.terminated()) {
            return current;
        }

        return switch (decision.status()) {
            case APPROVED -> current.withApproved(true).withTerminated(true)
                    .withLastStatus(HitlReviewStatus.APPROVED);
            case MODIFIED -> current.withApproved(true).withTerminated(true)
                    .withLastStatus(HitlReviewStatus.MODIFIED);
            case REJECTED -> handleReject(current);
            case PENDING -> current;
        };
    }

    private HitlReviewState handleReject(HitlReviewState current) {
        int nextRound = current.round() + 1;
        if (nextRound >= MAX_ROUNDS) {
            return current.withRound(nextRound).withTerminated(true).withApproved(false);
        }
        return new HitlReviewState(nextRound, false, false, HitlReviewStatus.REJECTED);
    }
}
