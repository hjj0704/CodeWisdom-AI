package com.codewisdom.evaluation.controller;

import com.codewisdom.common.api.R;
import com.codewisdom.evaluation.dto.RunProfileView;
import com.codewisdom.evaluation.service.ProjectRunProfileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/projects")
public class ProjectRunProfileController {

    private final ProjectRunProfileService projectRunProfileService;

    public ProjectRunProfileController(ProjectRunProfileService projectRunProfileService) {
        this.projectRunProfileService = projectRunProfileService;
    }

    @GetMapping("/{projectId}/run-profile")
    public R<RunProfileView> runProfile(@PathVariable long projectId) {
        return R.ok(projectRunProfileService.profile(projectId));
    }
}
