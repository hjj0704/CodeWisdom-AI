package com.codewisdom.agent.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

public record WorkbenchContextDto(
        String filePath,
        Integer selectionStartLine,
        Integer selectionEndLine,
        Integer viewportStartLine,
        Integer viewportEndLine,
        @Size(max = 2000) String selectionSnippet,
        @Size(max = 16000) String fileContent,
        @Size(max = 200) List<@Size(max = 512) String> javaFilePaths
) {
}
