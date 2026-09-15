package com.codewisdom.resource.controller;

import com.codewisdom.common.api.R;
import com.codewisdom.resource.dto.GitImportRequest;
import com.codewisdom.resource.dto.ImportResult;
import com.codewisdom.resource.service.ImportService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 项目导入接口。
 */
@RestController
@RequestMapping("/import")
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    /**
     * 从 Git 公开仓库导入项目。
     *
     * <p>同步执行。实测 18MB 仓库浅克隆约 10 秒，MVP 阶段可接受；
     * 大仓库改异步（T-206，需 RabbitMQ）后本接口改为返回任务 id 并立即返回。
     */
    @PostMapping("/git")
    public R<ImportResult> importFromGit(@Valid @RequestBody GitImportRequest request) {
        return R.ok(importService.importFromGit(request));
    }
}
