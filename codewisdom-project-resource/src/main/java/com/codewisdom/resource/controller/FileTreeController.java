package com.codewisdom.resource.controller;

import com.codewisdom.common.api.ErrorCode;
import com.codewisdom.common.api.R;
import com.codewisdom.common.exception.BizException;
import com.codewisdom.resource.dto.FileTreeNode;
import com.codewisdom.resource.dto.FileTreeStats;
import com.codewisdom.resource.service.FileTreeService;
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

    public FileTreeController(FileTreeService fileTreeService) {
        this.fileTreeService = fileTreeService;
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
                                @RequestParam(required = false) Integer depth) {
        if (depth != null && depth < 1) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "depth 必须大于 0");
        }

        if (path == null || path.isBlank()) {
            return R.ok(fileTreeService.buildTree(projectId, depth));
        }
        // 指定路径时忽略 depth 之外的语义差异：子树本身已是一次有界展开
        return R.ok(fileTreeService.findSubtree(projectId, path));
    }

    /**
     * 文件树分类统计：按分类、语言、目录层级汇总。
     */
    @GetMapping("/{projectId}/stats")
    public R<FileTreeStats> stats(@PathVariable Long projectId) {
        return R.ok(fileTreeService.stats(projectId));
    }
}
