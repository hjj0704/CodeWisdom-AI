package com.codewisdom.resource.controller;

import com.codewisdom.common.api.R;
import com.codewisdom.resource.auth.AuthContext;
import com.codewisdom.resource.dto.FileContentResponse;
import com.codewisdom.resource.dto.FileSaveRequest;
import com.codewisdom.resource.service.ProjectFileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/projects")
public class ProjectFileController {

    private final ProjectFileService projectFileService;

    public ProjectFileController(ProjectFileService projectFileService) {
        this.projectFileService = projectFileService;
    }

    @GetMapping("/{projectId}/files/content")
    public R<FileContentResponse> content(@PathVariable Long projectId,
                                          @RequestParam String path,
                                          HttpServletRequest request) {
        long userId = AuthContext.requireUserId(request);
        return R.ok(projectFileService.readFile(projectId, path, userId));
    }

    @PutMapping("/{projectId}/files/content")
    public R<FileContentResponse> save(@PathVariable Long projectId,
                                       @Valid @RequestBody FileSaveRequest body,
                                       HttpServletRequest request) {
        long userId = AuthContext.requireUserId(request);
        return R.ok(projectFileService.saveFile(projectId, body.path(), body.content(), userId));
    }

    @GetMapping("/{projectId}/export.zip")
    public void exportZip(@PathVariable Long projectId,
                          HttpServletResponse response,
                          HttpServletRequest request) throws IOException {
        long userId = AuthContext.requireUserId(request);
        response.setContentType("application/zip");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"project-" + projectId + ".zip\"");
        projectFileService.writeProjectZip(projectId, response.getOutputStream(), userId);
        response.flushBuffer();
    }
}
