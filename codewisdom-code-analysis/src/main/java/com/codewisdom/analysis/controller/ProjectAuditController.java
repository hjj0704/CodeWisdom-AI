package com.codewisdom.analysis.controller;

import com.codewisdom.analysis.dto.AuditReportView;
import com.codewisdom.analysis.service.ProjectAuditService;
import com.codewisdom.common.api.R;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/projects")
public class ProjectAuditController {

    private final ProjectAuditService projectAuditService;

    public ProjectAuditController(ProjectAuditService projectAuditService) {
        this.projectAuditService = projectAuditService;
    }

    @PostMapping("/{projectId}/audit")
    public R<AuditReportView> audit(@PathVariable long projectId) {
        return R.ok(projectAuditService.auditProject(projectId));
    }
}
