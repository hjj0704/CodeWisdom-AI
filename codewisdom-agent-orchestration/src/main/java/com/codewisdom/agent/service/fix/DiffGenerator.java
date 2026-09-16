package com.codewisdom.agent.service.fix;

import com.codewisdom.agent.domain.fix.FixModels.DiffHunk;
import com.codewisdom.agent.domain.fix.FixModels.DiffRequest;
import com.codewisdom.agent.domain.fix.FixModels.StructuredDiff;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 统一 Diff 生成（T-702）：前后内容 + hunks，无改动时不产出噪音。
 */
public class DiffGenerator {

    public StructuredDiff generate(DiffRequest request) {
        Objects.requireNonNull(request, "request");

        if (request.beforeContent().equals(request.afterContent())) {
            return StructuredDiff.unchanged(request.filePath(), request.beforeContent());
        }

        List<String> beforeLines = Arrays.asList(request.beforeContent().split("\n", -1));
        List<String> afterLines = Arrays.asList(request.afterContent().split("\n", -1));

        List<DiffHunk> hunks = buildHunks(beforeLines, afterLines);
        return new StructuredDiff(
                request.filePath(),
                request.beforeContent(),
                request.afterContent(),
                hunks,
                true);
    }

    private static List<DiffHunk> buildHunks(List<String> before, List<String> after) {
        int start = 0;
        while (start < before.size() && start < after.size()
                && before.get(start).equals(after.get(start))) {
            start++;
        }

        int endBefore = before.size() - 1;
        int endAfter = after.size() - 1;
        while (endBefore >= start && endAfter >= start
                && before.get(endBefore).equals(after.get(endAfter))) {
            endBefore--;
            endAfter--;
        }

        if (start > endBefore && start > endAfter) {
            return List.of();
        }

        List<String> lines = new ArrayList<>();
        for (int i = start; i <= endBefore; i++) {
            lines.add("-" + before.get(i));
        }
        for (int i = start; i <= endAfter; i++) {
            lines.add("+" + after.get(i));
        }

        int oldLines = endBefore - start + 1;
        int newLines = endAfter - start + 1;
        return List.of(new DiffHunk(start + 1, Math.max(oldLines, 0), start + 1, Math.max(newLines, 0), lines));
    }
}
