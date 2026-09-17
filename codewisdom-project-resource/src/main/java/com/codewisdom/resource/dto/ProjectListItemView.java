package com.codewisdom.resource.dto;

import com.codewisdom.resource.domain.enums.SourceType;

import java.time.LocalDateTime;

public record ProjectListItemView(
        Long id,
        String name,
        SourceType sourceType,
        String sourceUrl,
        int fileCount,
        long totalSize,
        LocalDateTime updatedAt
) {
}
