package com.codewisdom.agent.dto;

public record HitlStateView(
        int round,
        boolean approved,
        boolean terminated,
        String lastStatus
) {
}
