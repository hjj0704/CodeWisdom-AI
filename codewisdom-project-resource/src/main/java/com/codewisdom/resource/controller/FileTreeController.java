package com.codewisdom.resource.controller;

import com.codewisdom.common.api.ErrorCode;
import com.codewisdom.common.api.R;
import com.codewisdom.common.exception.BizException;
import com.codewisdom.resource.auth.AuthContext;
import com.codewisdom.resource.dto.FileTreeNode;
import com.codewisdom.resource.dto.FileTreeStats;
import com.codewisdom.resource.dto.ProjectListItemView;
import com.codewisdom.resource.dto.ProjectMetaView;
import com.codewisdom.resource.service.FileTreeService;
import com.codewisdom.resource.service.ProjectQueryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文件树查询接口。
 */
@RestController
@RequestMapping("/projects")
public class FileTreeController {

    private final FileTreeService fileTreeService;
    private final ProjectQueryService projectQueryService;

    public FileTreeController(FileTreeService fileTreeService,
                              ProjectQueryService projectQueryService) {
        this.fileTreeService = fileTreeService;
        this.projectQueryService = projectQueryService;
    }

    @GetMapping
    public R<java.util.List<ProjectListItemView>> list(HttpServletRequest request) {
        return R.ok(projectQueryService.listForUser(AuthContext.requireUserId(request)));
    }

    @GetMapping("/{projectId}")
    public R<ProjectMetaView> meta(@PathVariable Long projectId, HttpServletRequest request) {
        return R.ok(projectQueryService.meta(projectId, AuthContext.requireUserId(request)));
    }

    /**
     * 查询文件树。
     *
     * @param projectId 项目 id
     * @param path      可选。指定时只返回该路径下的子树，否则返回整棵树
     * @param depth     可选。限制展开层级，1 表示只返回一级条目；不传表示不限
     */
    @GetMapping("/{projectId}/tree")
    public R<FileTreeNode> tree(@PathVariable Long projectId,
                                @RequestParam(required = false) String path,
                                @RequestParam(required = false) Integer depth,
                                HttpServletRequest request) {
        if (depth != null && depth < 1) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "depth 必须大于 0");
        }
        long userId = AuthContext.requireUserId(request);
        projectQueryService.requireReadyProject(projectId, userId);

        if (path == null || path.isBlank()) {
            return R.ok(fileTreeService.buildTree(projectId, depth));
        }
        return R.ok(fileTreeService.findSubtree(projectId, path));
    }

    /**
     * 文件树分类统计：按分类、语言、目录层级汇总。
     */
    @GetMapping("/{projectId}/stats")
    public R<FileTreeStats> stats(@PathVariable Long projectId, HttpServletRequest request) {
        long userId = AuthContext.requireUserId(request);
        projectQueryService.requireReadyProject(projectId, userId);
        return R.ok(fileTreeService.stats(projectId));
    }
}
