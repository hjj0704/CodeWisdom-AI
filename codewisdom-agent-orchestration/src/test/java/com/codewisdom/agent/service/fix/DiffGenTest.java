package com.codewisdom.agent.service.fix;

import com.codewisdom.agent.domain.fix.FixModels.DiffRequest;
import com.codewisdom.agent.domain.fix.FixModels.StructuredDiff;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("T-702 Diff 生成")
class DiffGenTest {

    private final DiffGenerator generator = new DiffGenerator();

    @Test
    @DisplayName("有改动时输出 hunks")
    void producesHunksWhenChanged() {
        StructuredDiff diff = generator.generate(new DiffRequest(
                "Demo.java",
                "line1\nline2\nline3",
                "line1\nline2-fixed\nline3"));

        assertThat(diff.changed()).isTrue();
        assertThat(diff.hunks()).isNotEmpty();
        assertThat(diff.beforeContent()).contains("line2");
        assertThat(diff.afterContent()).contains("line2-fixed");
    }

    @Test
    @DisplayName("无改动时不产生噪音 diff")
    void unchangedProducesNoNoise() {
        String content = "same\ncontent";
        StructuredDiff diff = generator.generate(new DiffRequest("Demo.java", content, content));

        assertThat(diff.changed()).isFalse();
        assertThat(diff.hunks()).isEmpty();
    }
}
