package com.codewisdom.evaluation.controller;

import com.codewisdom.common.api.R;
import com.codewisdom.evaluation.dto.SandboxDtos.SandboxCheckRequest;
import com.codewisdom.evaluation.dto.SandboxDtos.SandboxCheckView;
import com.codewisdom.evaluation.dto.SandboxDtos.SandboxRunView;
import com.codewisdom.evaluation.service.ProjectRunProfileService;
import com.codewisdom.evaluation.service.SandboxGuard;
import com.codewisdom.evaluation.service.SandboxGuard.SandboxCheckResult;
import com.codewisdom.evaluation.service.SandboxRunner;
import com.codewisdom.evaluation.service.SandboxRunner.SandboxRunResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/projects")
public class SandboxController {

    private final SandboxGuard sandboxGuard;
    private final SandboxRunner sandboxRunner;
    private final ProjectRunProfileService projectRunProfileService;

    public SandboxController(SandboxGuard sandboxGuard,
                             SandboxRunner sandboxRunner,
                             ProjectRunProfileService projectRunProfileService) {
        this.sandboxGuard = sandboxGuard;
        this.sandboxRunner = sandboxRunner;
        this.projectRunProfileService = projectRunProfileService;
    }

    /** 校验命令是否满足沙箱策略（不执行）。重型项目直接拒绝。 */
    @PostMapping("/{projectId}/sandbox/validate")
    public R<SandboxCheckView> validate(@PathVariable long projectId,
                                        @Valid @RequestBody SandboxCheckRequest request) {
        var profile = projectRunProfileService.profile(projectId);
        if ("HEAVY".equals(profile.capability())) {
            return R.ok(new SandboxCheckView(
                    false,
                    "重型项目含中间件依赖，禁止沙箱执行，请按部署指引本地运行",
                    SandboxGuard.MAX_EXECUTION_SECONDS,
                    SandboxGuard.MAX_MEMORY_MB,
                    SandboxGuard.MAX_COMMAND_LENGTH,
                    true));
        }
        SandboxCheckResult result = sandboxGuard.check(request.command());
        return R.ok(SandboxCheckView.from(result));
    }

    /** 轻量项目在线演示（白名单命令，限时执行）。 */
    @PostMapping("/{projectId}/sandbox/run")
    public R<SandboxRunView> run(@PathVariable long projectId,
                                 @Valid @RequestBody SandboxCheckRequest request) {
        var profile = projectRunProfileService.profile(projectId);
        if ("HEAVY".equals(profile.capability())) {
            return R.ok(new SandboxRunView(
                    false, -1, "", "",
                    0, "重型项目请按部署指引本地运行，不支持在线演示"));
        }
        SandboxRunResult result = sandboxRunner.run(projectId, request.command());
        return R.ok(new SandboxRunView(
                result.success(), result.exitCode(), result.stdout(), result.stderr(),
                result.durationMs(), result.message()));
    }
}
