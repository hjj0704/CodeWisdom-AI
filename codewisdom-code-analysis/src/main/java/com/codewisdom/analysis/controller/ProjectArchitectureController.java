package com.codewisdom.analysis.controller;

import com.codewisdom.analysis.dto.ArchitectureView;
import com.codewisdom.analysis.service.ProjectArchitectureService;
import com.codewisdom.common.api.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/projects")
public class ProjectArchitectureController {

    private final ProjectArchitectureService projectArchitectureService;

    public ProjectArchitectureController(ProjectArchitectureService projectArchitectureService) {
        this.projectArchitectureService = projectArchitectureService;
    }

    @GetMapping("/{projectId}/architecture")
    public R<ArchitectureView> architecture(@PathVariable long projectId) {
        return R.ok(projectArchitectureService.analyze(projectId));
    }
}
