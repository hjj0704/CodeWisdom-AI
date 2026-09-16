package com.codewisdom.evaluation.service;

import com.codewisdom.evaluation.domain.EvalDataset;
import com.codewisdom.evaluation.domain.EvalDataset.Category;
import com.codewisdom.evaluation.domain.EvalDataset.EvalSample;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 评测数据集 Schema 校验（T-901）。
 */
public class DatasetSchemaValidator {

    private static final int MIN_PER_CATEGORY = 3;

    private final ObjectMapper mapper = new ObjectMapper();

    public EvalDataset loadAndValidate(InputStream input) throws IOException {
        EvalDataset dataset = mapper.readValue(input, EvalDataset.class);
        validate(dataset);
        return dataset;
    }

    public void validate(EvalDataset dataset) {
        Set<String> ids = new HashSet<>();
        Map<Category, Integer> counts = new EnumMap<>(Category.class);
        for (Category c : Category.values()) {
            counts.put(c, 0);
        }

        for (EvalSample sample : dataset.samples()) {
            if (!ids.add(sample.id())) {
                throw new IllegalArgumentException("重复样例 id: " + sample.id());
            }
            counts.merge(sample.category(), 1, Integer::sum);
            validateSample(sample);
        }

        for (Category category : Category.values()) {
            int count = counts.get(category);
            if (count < MIN_PER_CATEGORY) {
                throw new IllegalArgumentException(
                        "类别 " + category + " 样例不足 " + MIN_PER_CATEGORY + " 条，当前 " + count);
            }
        }
    }

    public Map<Category, List<EvalSample>> groupByCategory(EvalDataset dataset) {
        return dataset.samples().stream()
                .collect(Collectors.groupingBy(EvalSample::category,
                        () -> new EnumMap<>(Category.class),
                        Collectors.toList()));
    }

    private void validateSample(EvalSample sample) {
        EvalDataset.ExpectedAnnotations expected = sample.expected();
        for (String ruleId : expected.requiredRuleIds()) {
            if (ruleId == null || ruleId.isBlank()) {
                throw new IllegalArgumentException(sample.id() + " 的 requiredRuleIds 含空值");
            }
        }
        for (String ruleId : expected.forbiddenRuleIds()) {
            if (ruleId == null || ruleId.isBlank()) {
                throw new IllegalArgumentException(sample.id() + " 的 forbiddenRuleIds 含空值");
            }
        }
        for (String layer : expected.expectedLayers()) {
            if (layer == null || layer.isBlank()) {
                throw new IllegalArgumentException(sample.id() + " 的 expectedLayers 含空值");
            }
        }
        if (sample.category() == Category.NO_DOC && expected.documentationExpected()) {
            throw new IllegalArgumentException(sample.id() + " 为 NO_DOC 类别但 documentationExpected=true");
        }
    }
}
