package com.codewisdom.agent.dto;

import java.util.List;

public record StructuredDiffView(
        String filePath,
        String beforeContent,
        String afterContent,
        boolean changed,
        List<DiffHunkView> hunks
) {
    public record DiffHunkView(
            int oldStart,
            int oldLines,
            int newStart,
            int newLines,
            List<String> lines
    ) {
    }
}
