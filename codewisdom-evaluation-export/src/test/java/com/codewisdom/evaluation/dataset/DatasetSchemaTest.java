package com.codewisdom.evaluation.dataset;

import com.codewisdom.evaluation.domain.EvalDataset;
import com.codewisdom.evaluation.domain.EvalDataset.Category;
import com.codewisdom.evaluation.service.DatasetSchemaValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("T-901 评测数据集 Schema")
class DatasetSchemaTest {

    private final DatasetSchemaValidator validator = new DatasetSchemaValidator();

    @Test
    @DisplayName("内置 v1 数据集通过 Schema 校验，四类各 ≥3 条")
    void bundledDatasetValid() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/eval/eval-dataset-v1.json")) {
            EvalDataset dataset = validator.loadAndValidate(in);
            assertThat(dataset.version()).isEqualTo("1.0");
            assertThat(dataset.samples()).hasSize(12);

            Map<Category, List<EvalDataset.EvalSample>> grouped = validator.groupByCategory(dataset);
            for (Category category : Category.values()) {
                assertThat(grouped.get(category)).hasSizeGreaterThanOrEqualTo(3);
            }
        }
    }

    @Test
    @DisplayName("重复 id 被拒绝")
    void duplicateIdRejected() {
        EvalDataset dataset = new EvalDataset("1.0", List.of(
                sample("a-1", Category.NORMAL),
                sample("a-1", Category.NORMAL),
                sample("a-2", Category.NORMAL),
                sample("b-1", Category.DEFECT),
                sample("b-2", Category.DEFECT),
                sample("b-3", Category.DEFECT),
                sample("c-1", Category.CONFLICT),
                sample("c-2", Category.CONFLICT),
                sample("c-3", Category.CONFLICT),
                sample("d-1", Category.NO_DOC),
                sample("d-2", Category.NO_DOC),
                sample("d-3", Category.NO_DOC)
        ));

        assertThatThrownBy(() -> validator.validate(dataset))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复样例 id");
    }

    private static EvalDataset.EvalSample sample(String id, Category category) {
        return new EvalDataset.EvalSample(
                id, category, "t", "d",
                new EvalDataset.ExpectedAnnotations(List.of(), List.of(), List.of(), false, 0, 5));
    }
}
