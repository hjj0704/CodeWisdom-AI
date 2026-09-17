package com.codewisdom.agent.dto;

import jakarta.validation.constraints.NotBlank;

public record FixDiffRequest(
        @NotBlank String filePath,
        @NotBlank String beforeContent,
        @NotBlank String afterContent,
        Long fixRecordId
) {
}
