package com.codewisdom.agent.domain.doc;

import java.util.List;
import java.util.Objects;

/** README 生成输入/输出。 */
public final class ReadmeModels {

    private ReadmeModels() {
    }

    public record ReadmeRequest(
            String projectName,
            String descriptionHint,
            List<String> techStack,
            List<String> topLevelDirs,
            String startCommandHint
    ) {
        public ReadmeRequest {
            Objects.requireNonNull(projectName, "projectName");
            techStack = List.copyOf(techStack == null ? List.of() : techStack);
            topLevelDirs = List.copyOf(topLevelDirs == null ? List.of() : topLevelDirs);
            descriptionHint = descriptionHint == null ? "" : descriptionHint;
            startCommandHint = startCommandHint == null ? "" : startCommandHint;
        }
    }

    public record ReadmeResult(boolean skipped, String markdown) {
        public ReadmeResult {
            markdown = markdown == null ? "" : markdown;
        }

        public static ReadmeResult notGenerated() {
            return new ReadmeResult(true, "");
        }
    }
}
