package com.codewisdom.resource.dto;

public record AuthResponse(
        String token,
        Long userId,
        String username,
        String nickname
) {
}
