package com.codewisdom.evaluation.service;

import com.codewisdom.evaluation.domain.EvalMetrics;
import com.codewisdom.evaluation.dto.EvalReportView;
import com.codewisdom.evaluation.dto.ProjectScoreView;
import com.codewisdom.evaluation.entity.EvalReportEntity;
import com.codewisdom.evaluation.mapper.EvalReportMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * T-1007 / T-904：基于项目实时评分生成评测报告并入库。
 */
@Service
public class ProjectEvalReportService {

    private final ProjectScoreService projectScoreService;
    private final EvalReportGenerator reportGenerator;
    private final EvalReportMapper evalReportMapper;

    public ProjectEvalReportService(
            ProjectScoreService projectScoreService,
            EvalReportMapper evalReportMapper) {
        this.projectScoreService = projectScoreService;
        this.reportGenerator = new EvalReportGenerator();
        this.evalReportMapper = evalReportMapper;
    }

    public EvalReportView generate(long projectId, String authorizationHeader) {
        ProjectScoreView score = projectScoreService.scoreProject(projectId, authorizationHeader);
        EvalMetrics metrics = new EvalMetrics(
                score.bugRecall(),
                score.falsePositiveRate(),
                score.architectureAccuracy(),
                score.ruleMatchRate(),
                score.documentationScore());
        String status = score.overall() >= 60 ? "COMPLETED" : "NEEDS_IMPROVEMENT";
        String sampleId = "project-" + projectId;

        EvalReportGenerator.ReportPayload payload = reportGenerator.generate(
                "project-live", sampleId, metrics, List.of(), status);

        EvalReportEntity entity = new EvalReportEntity();
        entity.setProjectId(projectId);
        entity.setDatasetVersion("project-live");
        entity.setSampleId(sampleId);
        entity.setStatus(status);
        entity.setMetricsJson(payload.metricsJson());
        entity.setTraceJson(payload.traceJson());
        entity.setReportMarkdown(payload.reportMarkdown());
        evalReportMapper.insert(entity);

        return new EvalReportView(
                entity.getId(),
                projectId,
                status,
                score.overall(),
                payload.metricsJson(),
                payload.traceJson(),
                payload.reportMarkdown());
    }
}
