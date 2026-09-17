package com.codewisdom.resource.controller;

import com.codewisdom.common.api.R;
import com.codewisdom.resource.auth.AuthContext;
import com.codewisdom.resource.dto.GitImportRequest;
import com.codewisdom.resource.dto.ImportResult;
import com.codewisdom.resource.service.ImportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
    public R<ImportResult> importFromGit(@Valid @RequestBody GitImportRequest request,
                                         HttpServletRequest httpRequest) {
        long userId = AuthContext.requireUserId(httpRequest);
        return R.ok(importService.importFromGit(request, userId));
    }

    /**
     * 上传 ZIP 压缩包导入项目。
     *
     * <p>压缩包是不可信输入，解压前会做路径穿越（Zip Slip）与压缩炸弹防护，
     * 详见 {@code ZipExtractor}。
     *
     * @param file ZIP 文件
     * @param name 项目显示名；留空则用压缩包文件名
     */
    @PostMapping(value = "/zip", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<ImportResult> importFromZip(@RequestPart("file") MultipartFile file,
                                         @RequestParam(value = "name", required = false) String name,
                                         HttpServletRequest httpRequest) {
        long userId = AuthContext.requireUserId(httpRequest);
        return R.ok(importService.importFromZip(file, name, userId));
    }
}
