package com.codewisdom.evaluation.controller;

import com.codewisdom.common.api.R;
import com.codewisdom.evaluation.dto.ProjectScoreView;
import com.codewisdom.evaluation.service.ProjectScoreService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/projects")
public class ProjectScoreController {

    private final ProjectScoreService projectScoreService;

    public ProjectScoreController(ProjectScoreService projectScoreService) {
        this.projectScoreService = projectScoreService;
    }

    @GetMapping("/{projectId}/score")
    public R<ProjectScoreView> score(@PathVariable long projectId,
                                     @RequestHeader(value = "Authorization", required = false) String authorization) {
        return R.ok(projectScoreService.scoreProject(projectId, authorization));
    }
}
