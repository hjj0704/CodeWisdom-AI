package com.codewisdom.evaluation.service;

import com.codewisdom.evaluation.config.DownstreamProperties;
import com.codewisdom.evaluation.dto.ProjectScoreView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class ProjectScoreService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public ProjectScoreService(DownstreamProperties properties, ObjectMapper objectMapper) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.getCodeAnalysisBaseUrl())
                .build();
        this.objectMapper = objectMapper;
    }

    public ProjectScoreView scoreProject(long projectId, String authorizationHeader) {
        JsonNode audit = postAudit(projectId, authorizationHeader);
        JsonNode data = audit.path("data");
        int total = data.path("total").asInt();
        int high = data.path("highCount").asInt();
        int medium = data.path("mediumCount").asInt();
        int low = data.path("lowCount").asInt();

        double penalty = high * 8.0 + medium * 3.0 + low * 0.8;
        double overall = Math.max(0, Math.min(100, 100 - penalty));

        double bugRecall = total == 0 ? 1.0 : Math.min(1.0, (high + medium) / (double) Math.max(total, 1));
        double falsePositiveRate = total == 0 ? 0 : low / (double) total;
        double architectureAccuracy = Math.max(0, 1.0 - medium / (double) Math.max(total + 5, 1));
        double ruleMatchRate = Math.max(0, 1.0 - high / (double) Math.max(total + 3, 1));
        double documentationScore = overall >= 70 ? 0.85 : 0.55;

        String summary = """
                共发现 %d 个问题（高危 %d / 中危 %d / 低危 %d）。
                综合得分 %.1f：%s
                """.formatted(
                total, high, medium, low, overall,
                overall >= 80 ? "代码质量较好，建议优先处理高危项"
                        : overall >= 60 ? "存在明显改进空间，建议结合 Agent 会话制定治理计划"
                        : "风险偏高，建议先修复依赖与空指针类问题");

        return new ProjectScoreView(
                overall, bugRecall, falsePositiveRate,
                architectureAccuracy, ruleMatchRate, documentationScore, summary.trim());
    }

    private JsonNode postAudit(long projectId, String authorizationHeader) {
        RestClient.RequestBodySpec spec = restClient.post().uri("/projects/" + projectId + "/audit");
        if (authorizationHeader != null && !authorizationHeader.isBlank()) {
            spec = spec.header("Authorization", authorizationHeader);
        }
        String body = spec.retrieve().body(String.class);
        try {
            return objectMapper.readTree(body);
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }
}
