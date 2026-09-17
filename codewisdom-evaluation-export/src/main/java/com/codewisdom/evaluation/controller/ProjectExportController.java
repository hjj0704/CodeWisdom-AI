package com.codewisdom.evaluation.controller;

import com.codewisdom.common.api.R;
import com.codewisdom.evaluation.config.WorkspaceProperties;
import com.codewisdom.evaluation.service.ProjectExportService;
import com.codewisdom.evaluation.service.ProjectExportService.ExportResult;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Paths;

@RestController
@RequestMapping("/projects")
public class ProjectExportController {

    private final ProjectExportService projectExportService;
    private final WorkspaceProperties workspaceProperties;

    public ProjectExportController(ProjectExportService projectExportService,
                                   WorkspaceProperties workspaceProperties) {
        this.projectExportService = projectExportService;
        this.workspaceProperties = workspaceProperties;
    }

    /** 打包项目并上传 MinIO {@code cw-export}。 */
    @PostMapping("/{projectId}/export")
    public R<ExportResult> export(@PathVariable long projectId) {
        ExportResult result = projectExportService.exportFromWorkspace(
                projectId, Paths.get(workspaceProperties.getRoot()));
        return R.ok(result);
    }

    /** 从 MinIO 下载已导出的 ZIP（objectKey 须属于该项目）。 */
    @GetMapping("/{projectId}/export/download")
    public void download(@PathVariable long projectId,
                         @RequestParam("key") String objectKey,
                         HttpServletResponse response) throws IOException {
        byte[] zip = projectExportService.downloadExport(projectId, objectKey);
        response.setContentType("application/zip");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"project-" + projectId + ".zip\"");
        response.setContentLength(zip.length);
        response.getOutputStream().write(zip);
        response.flushBuffer();
    }
}
