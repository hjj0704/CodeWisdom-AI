package com.codewisdom.evaluation.dto;

import com.codewisdom.evaluation.service.SandboxGuard.SandboxCheckResult;
import com.codewisdom.evaluation.service.SandboxGuard.SandboxPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class SandboxDtos {

    private SandboxDtos() {
    }

    public record SandboxCheckRequest(
            @NotBlank @Size(max = 4096)
            String command
    ) {
    }

    public record SandboxCheckView(
            boolean allowed,
            String message,
            int maxExecutionSeconds,
            int maxMemoryMb,
            int maxCommandLength,
            boolean networkIsolation
    ) {
        public static SandboxCheckView from(SandboxCheckResult result) {
            SandboxPolicy policy = result.policy();
            return new SandboxCheckView(
                    result.allowed(),
                    result.message(),
                    policy.maxExecutionSeconds(),
                    policy.maxMemoryMb(),
                    policy.maxCommandLength(),
                    policy.networkIsolation());
        }
    }

    public record SandboxRunView(
            boolean success,
            int exitCode,
            String stdout,
            String stderr,
            long durationMs,
            String message
    ) {
    }

    public record DemoLinkView(
            boolean found,
            String url,
            String label,
            String sourceFile,
            String message
    ) {
    }
}
