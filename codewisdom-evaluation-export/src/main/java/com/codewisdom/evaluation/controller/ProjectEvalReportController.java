package com.codewisdom.evaluation.controller;

import com.codewisdom.common.api.R;
import com.codewisdom.evaluation.dto.EvalReportView;
import com.codewisdom.evaluation.service.ProjectEvalReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/projects")
public class ProjectEvalReportController {

    private final ProjectEvalReportService projectEvalReportService;

    public ProjectEvalReportController(ProjectEvalReportService projectEvalReportService) {
        this.projectEvalReportService = projectEvalReportService;
    }

    @GetMapping("/{projectId}/eval-report")
    public R<EvalReportView> evalReport(@PathVariable long projectId,
                                      @RequestHeader(value = "Authorization", required = false) String authorization) {
        return R.ok(projectEvalReportService.generate(projectId, authorization));
    }
}
