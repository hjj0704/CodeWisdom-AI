package com.codewisdom.resource.dto;

import com.codewisdom.resource.domain.enums.SourceType;

public record ProjectMetaView(
        Long id,
        String name,
        SourceType sourceType,
        String sourceUrl,
        String defaultBranch,
        boolean exportEnabled
) {
}
