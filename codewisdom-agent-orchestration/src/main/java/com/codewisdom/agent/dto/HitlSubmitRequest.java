package com.codewisdom.agent.dto;

import com.codewisdom.agent.domain.fix.FixModels.HitlReviewStatus;
import jakarta.validation.constraints.NotNull;

public record HitlSubmitRequest(
        @NotNull HitlReviewStatus status,
        Long fixRecordId,
        String sessionKey
) {
}
