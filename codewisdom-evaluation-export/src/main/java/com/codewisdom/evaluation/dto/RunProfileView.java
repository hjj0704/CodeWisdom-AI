package com.codewisdom.evaluation.dto;

import java.util.List;

public record RunProfileView(
        String capability,
        List<String> middlewareDependencies,
        String deployGuideMarkdown,
        String summary
) {
}
